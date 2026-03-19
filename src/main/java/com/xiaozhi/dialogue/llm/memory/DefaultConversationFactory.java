package com.xiaozhi.dialogue.llm.memory;

import com.xiaozhi.entity.SysUserMemory;
import com.xiaozhi.service.SysUserMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;

import java.util.List;

@Primary
@Service
@Slf4j
public class DefaultConversationFactory implements ConversationFactory{

    @Value("${conversation.max-messages:16}")
    private int maxMessages;

    @Autowired
    private ChatMemory chatMemory;
    // 聊天总结(或者可以看作是中短期记忆）
    @Autowired
    private SummaryConversationFactory summaryConversationFactory;

    @Autowired
    private SysUserMemoryService userMemoryService;

    @Override
    public Conversation initConversation(SysDevice device, SysRole role, String sessionId) {
        // 根据配置文件的不同类型，返回不同的Conversation
        Conversation conversation = switch (role.getMemoryType()) {
            case "summary" -> summaryConversationFactory.initConversation(device, role, sessionId);
            case "window"-> MessageWindowConversation.builder().chatMemory(chatMemory)
                    .maxMessages(maxMessages)
                    .role(role)
                    .device(device)
                    .sessionId(sessionId)
                    .build();
            default ->{
                log.warn("系统目前不支持这类未知的记忆类型：{} ， 将启用默认的MessageWindowConversation", role.getMemoryType());
                yield MessageWindowConversation.builder().chatMemory(chatMemory)
                        .maxMessages(maxMessages)
                        .role(role)
                        .device(device)
                        .sessionId(sessionId)
                        .build();
            }
        };
        
        // 注入用户长期记忆（跨设备、跨角色共享）
        injectUserMemory(conversation, device);

        return conversation;

    }

    /**
     * 将用户长期记忆注入到Conversation中
     */
    private void injectUserMemory(Conversation conversation, SysDevice device) {
        try {
            Integer userId = device.getUserId();
            if (userId == null || userId <= 0) {
                return;
            }
            List<SysUserMemory> memories = userMemoryService.findForContext(userId);
            if (memories != null && !memories.isEmpty()) {
                String memoryText = userMemoryService.formatMemoriesForPrompt(memories);
                conversation.setUserMemoryText(memoryText);
                log.info("已注入{}条用户长期记忆到设备{}的对话上下文", memories.size(), device.getDeviceId());
            }
        } catch (Exception e) {
            log.warn("注入用户长期记忆失败", e);
        }
    }
}
