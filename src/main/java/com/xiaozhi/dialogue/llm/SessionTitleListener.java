package com.xiaozhi.dialogue.llm;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.service.Persona;
import com.xiaozhi.event.ChatSessionCloseEvent;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话关闭时自动生成对话标题
 * 使用 @Order(HIGHEST_PRECEDENCE) 确保在 ChatService.handleSessionClose (releaseAll) 之前执行
 */
@Component
public class SessionTitleListener {

    private static final Logger logger = LoggerFactory.getLogger(SessionTitleListener.class);
    private static final int MIN_USER_MESSAGES = 2;
    private static final int MAX_TITLE_LENGTH = 50;

    private final String titlePrompt;

    @Resource
    private MessageMapper messageMapper;

    public SessionTitleListener() {
        String prompt;
        try {
            prompt = new ClassPathResource("/prompts/session_title.md", getClass())
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            prompt = "请用10个字以内总结本次对话的主题，只输出标题文字。";
        }
        this.titlePrompt = prompt;
    }

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onSessionClose(ChatSessionCloseEvent event) {
        ChatSession session = event.getSession();
        if (session == null) {
            return;
        }

        String sessionId = session.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        Persona persona = session.getPersonaRegistry().getActive();
        if (persona == null) {
            return;
        }

        Conversation conversation = persona.getConversation();
        ChatModel chatModel = persona.getChatModel();
        if (conversation == null || chatModel == null) {
            return;
        }

        // 统计 UserMessage 数量，不足 2 轮则跳过
        List<Message> messages = conversation.messages();
        long userMessageCount = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .count();
        if (userMessageCount < MIN_USER_MESSAGES) {
            return;
        }

        // 在主线程中复制消息列表，避免 releaseAll() 后 conversation.clear() 导致空列表
        List<Message> copiedMessages = new ArrayList<>(messages);

        // 异步生成标题
        Thread.startVirtualThread(() -> {
            try {
                generateAndSaveTitle(sessionId, copiedMessages, chatModel);
            } catch (Exception e) {
                logger.warn("生成会话标题失败, sessionId={}", sessionId, e);
            }
        });
    }

    private void generateAndSaveTitle(String sessionId, List<Message> messages, ChatModel chatModel) {
        // 复制消息列表并追加标题生成请求
        List<Message> promptMessages = new ArrayList<>(messages);
        promptMessages.add(new UserMessage(titlePrompt));

        // 调用 LLM
        String result = chatModel.call(new Prompt(promptMessages)).getResult().getOutput().getText();

        if (!StringUtils.hasText(result)) {
            return;
        }

        // 清理标题：去除引号、换行，截取长度
        String title = result.trim()
                .replaceAll("[\"'\\u201c\\u201d\\u2018\\u2019\\n\\r]", "")
                .trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            title = title.substring(0, MAX_TITLE_LENGTH);
        }

        if (!StringUtils.hasText(title)) {
            return;
        }

        // 存储标题
        messageMapper.updateSessionTitle(sessionId, title);
        logger.info("会话标题生成成功, sessionId={}, title={}", sessionId, title);
    }
}
