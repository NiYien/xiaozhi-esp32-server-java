package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.DateUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天记录
 *
 *
 */

@Service
public class SysMessageServiceImpl extends BaseServiceImpl implements SysMessageService {

    private static final Logger logger = LoggerFactory.getLogger(SysMessageServiceImpl.class);
    private static final int BATCH_CONCURRENCY = 3;
    private static final int MAX_MESSAGES_FOR_TITLE = 10;
    private static final int MAX_TITLE_LENGTH = 50;

    private final String titlePrompt;

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private SessionManager sessionManager;

    @Autowired
    @Lazy
    private ChatModelFactory chatModelFactory;

    public SysMessageServiceImpl() {
        String prompt;
        try {
            prompt = new ClassPathResource("/prompts/session_title.md", getClass())
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            prompt = "请用10个字以内总结本次对话的主题，只输出标题文字。";
        }
        this.titlePrompt = prompt;
    }

    /**
     * 新增聊天记录
     *
     * @param message
     * @return
     */
    @Override
    @Transactional
    public int add(SysMessage message) {
        return messageMapper.add(message);
    }

    @Override
    @Transactional
    public int saveAll(List<SysMessage> messages) {
        return messageMapper.saveAll(messages);
    }

    /**
     * 查询聊天记录
     *
     * @param message
     * @return
     */
    @Override
    public List<SysMessage> query(SysMessage message, PageFilter pageFilter) {
        if(pageFilter != null){
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return messageMapper.query(message);
    }

    @Override
    public SysMessage findById(Integer messageId) {
        return messageMapper.findById(messageId);
    }

    @Override
    public List<Map<String, Object>> querySessions(Integer userId, String deviceId, Date startTime, Date endTime, PageFilter pageFilter) {
        if (pageFilter != null) {
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return messageMapper.querySessions(userId, deviceId, startTime, endTime);
    }

    /**
     * 删除记忆，同步删除关联的音频文件
     * - 单条删除（messageId不为空）：直接从记录中拿 audioPath 删文件
     * - 批量删除（deviceId不为空）：遍历最近 RETENTION_DAYS 天的日期目录，删除对应 device 子目录
     *
     * @param message
     * @return
     */
    @Override
    @Transactional
    public int delete(SysMessage message) {
        if (message.getMessageId() != null) {
            // 单条：直接拿 audioPath 删文件
            SysMessage existing = messageMapper.findById(message.getMessageId());
            if (existing != null && StringUtils.hasText(existing.getAudioPath())) {
                AudioUtils.deleteFile(existing.getAudioPath());
            }
        } else if (StringUtils.hasText(message.getDeviceId())) {
            // 批量：遍历最近 RETENTION_DAYS 天的日期目录，删除 device 子目录
            String deviceId = message.getDeviceId().replace(":", "-");
            LocalDate today = LocalDate.now();
            for (int i = 0; i <= AudioUtils.AUDIO_RETENTION_DAYS; i++) {
                String date = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
                Path deviceDir = Path.of(AudioUtils.AUDIO_PATH, date, deviceId);
                AudioUtils.deleteDirectory(deviceDir);
            }
            // 清除内存中的对话历史，避免数据库已清空但LLM仍能看到旧上下文
            sessionManager.findConversation(message.getDeviceId()).ifPresent(conversation -> conversation.clear());
        }

        return messageMapper.delete(message);
    }

    @Override
    public void updateMessageByAudioFile(String deviceId, Integer roleId, String sender,
                                         String createTime, String audioPath) {
        SysMessage sysMessage = new SysMessage();
        // 设置消息的where条件
        sysMessage.setDeviceId(deviceId);
        sysMessage.setRoleId(roleId);
        sysMessage.setSender(sender);
        sysMessage.setCreateTime(DateUtils.toDate(createTime.replace("T", " "), "yyyy-MM-dd HHmmss"));
        // 设置音频路径和TTS时长
        sysMessage.setAudioPath(audioPath);
        messageMapper.updateMessageByAudioFile(sysMessage);
    }

    @Override
    public Map<String, Integer> batchGenerateTitles(Integer userId) {
        List<String> sessionIds = messageMapper.querySessionsWithoutTitle(userId);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        if (sessionIds.isEmpty()) {
            return Map.of("total", 0, "success", 0, "failed", 0);
        }

        // 获取可用的 ChatModel
        ChatModel chatModel = chatModelFactory.takeAnyChatModel();
        if (chatModel == null) {
            logger.warn("无可用 ChatModel，无法批量生成标题");
            return Map.of("total", sessionIds.size(), "success", 0, "failed", sessionIds.size());
        }

        Semaphore semaphore = new Semaphore(BATCH_CONCURRENCY);
        List<Thread> threads = new ArrayList<>();

        for (String sessionId : sessionIds) {
            Thread t = Thread.startVirtualThread(() -> {
                try {
                    semaphore.acquire();
                    generateTitleForSession(sessionId, chatModel);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    logger.warn("批量生成标题失败, sessionId={}", sessionId, e);
                } finally {
                    semaphore.release();
                }
            });
            threads.add(t);
        }

        // 等待所有线程完成
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        logger.info("批量生成标题完成: total={}, success={}, failed={}", sessionIds.size(), success.get(), failed.get());
        return Map.of("total", sessionIds.size(), "success", success.get(), "failed", failed.get());
    }

    @Override
    public List<SysMessage> queryUserAudios(Integer userId, String deviceId) {
        return messageMapper.queryUserAudios(userId, deviceId);
    }

    @Override
    public List<SysMessage> findByIds(List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        return messageMapper.findByIds(messageIds);
    }

    private void generateTitleForSession(String sessionId, ChatModel chatModel) {
        List<SysMessage> messages = messageMapper.queryMessagesBySessionId(sessionId, MAX_MESSAGES_FOR_TITLE);
        if (messages.size() < 2) {
            return;
        }

        // 构建对话消息列表
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage("你是一个对话标题生成助手。"));
        for (SysMessage msg : messages) {
            if ("user".equals(msg.getSender())) {
                promptMessages.add(new UserMessage(msg.getMessage()));
            } else if ("assistant".equals(msg.getSender())) {
                promptMessages.add(new AssistantMessage(msg.getMessage()));
            }
        }
        promptMessages.add(new UserMessage(titlePrompt));

        // 调用 LLM
        String result = chatModel.call(new Prompt(promptMessages)).getResult().getOutput().getText();
        if (!StringUtils.hasText(result)) {
            return;
        }

        String title = result.trim().replaceAll("[\"'\\u201c\\u201d\\u2018\\u2019\\n\\r]", "").trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        if (StringUtils.hasText(title)) {
            messageMapper.updateSessionTitle(sessionId, title);
        }
    }

}