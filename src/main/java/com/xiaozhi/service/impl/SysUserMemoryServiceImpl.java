package com.xiaozhi.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.UserMemoryMapper;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.entity.SysUserMemory;
import com.xiaozhi.service.SysUserMemoryService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户长期记忆服务实现
 * 负责从对话中异步提取关键信息并保存为长期记忆
 */
@Service
public class SysUserMemoryServiceImpl extends BaseServiceImpl implements SysUserMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(SysUserMemoryServiceImpl.class);
    private static final int MAX_CONTEXT_MEMORIES = 20;
    private static final Set<String> VALID_CATEGORIES = Set.of("preference", "fact", "habit", "relationship", "other");

    private final PromptTemplate memoryExtractPromptTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Autowired
    @Lazy
    private ChatModelFactory chatModelFactory;

    public SysUserMemoryServiceImpl() {
        this.memoryExtractPromptTemplate = PromptTemplate.builder()
                .renderer(StTemplateRenderer.builder().startDelimiterToken('{').endDelimiterToken('}').build())
                .resource(new ClassPathResource("/prompts/memory_extract.md", getClass()))
                .build();
    }

    @Override
    public void extractAndSaveAsync(int userId, int roleId, String conversation) {
        if (!StringUtils.hasText(conversation)) {
            return;
        }
        // 异步提取记忆，避免阻塞对话流程
        Thread.startVirtualThread(() -> {
            try {
                doExtractAndSave(userId, roleId, conversation);
            } catch (Exception e) {
                logger.error("提取用户记忆失败, userId={}", userId, e);
            }
        });
    }

    private void doExtractAndSave(int userId, int roleId, String conversation) {
        // 获取已有记忆用于去重
        List<String> existingContents = userMemoryMapper.findAllContentByUserId(userId);
        String existingMemories = existingContents.isEmpty() ? "无" :
                existingContents.stream().collect(Collectors.joining("\n- ", "- ", ""));

        // 调用LLM提取记忆
        String prompt = memoryExtractPromptTemplate.render(Map.of(
                "existing_memories", existingMemories,
                "conversation", conversation
        ));

        logger.debug("调用LLM提取用户记忆, userId={}", userId);
        // 使用角色对应的ChatModel进行记忆提取
        ChatModel chatModel = chatModelFactory.takeChatModel(roleId);
        String result = chatModel.call(prompt);
        logger.debug("LLM提取用户记忆结果: {}", result);

        if (!StringUtils.hasText(result)) {
            return;
        }

        // 解析JSON结果
        List<SysUserMemory> memories = parseMemories(userId, result);
        if (memories.isEmpty()) {
            return;
        }

        // 批量保存
        userMemoryMapper.batchInsert(memories);
        logger.info("成功保存{}条用户记忆, userId={}", memories.size(), userId);
    }

    private List<SysUserMemory> parseMemories(int userId, String jsonResult) {
        List<SysUserMemory> memories = new ArrayList<>();
        try {
            // 提取JSON数组部分（LLM可能返回额外文字）
            String json = extractJsonArray(jsonResult);
            if (json == null) {
                return memories;
            }

            List<Map<String, String>> items = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, String> item : items) {
                String category = item.get("category");
                String content = item.get("content");
                if (!StringUtils.hasText(content) || !StringUtils.hasText(category)) {
                    continue;
                }
                // 验证分类
                if (!VALID_CATEGORIES.contains(category)) {
                    category = "other";
                }
                // 限制内容长度
                if (content.length() > 500) {
                    content = content.substring(0, 500);
                }

                SysUserMemory memory = new SysUserMemory();
                memory.setUserId(userId);
                memory.setCategory(category);
                memory.setContent(content);
                memories.add(memory);
            }
        } catch (Exception e) {
            logger.warn("解析记忆JSON失败: {}", jsonResult, e);
        }
        return memories;
    }

    /**
     * 从LLM返回的文本中提取JSON数组
     */
    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    @Override
    public List<SysUserMemory> findForContext(int userId) {
        return userMemoryMapper.findByUserId(userId, MAX_CONTEXT_MEMORIES);
    }

    @Override
    public String formatMemoriesForPrompt(List<SysUserMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("关于用户的已知信息：\n");

        // 按分类分组展示
        Map<String, List<SysUserMemory>> grouped = memories.stream()
                .collect(Collectors.groupingBy(SysUserMemory::getCategory));

        Map<String, String> categoryNames = Map.of(
                "preference", "偏好",
                "fact", "个人信息",
                "habit", "习惯",
                "relationship", "人际关系",
                "other", "其他"
        );

        for (Map.Entry<String, List<SysUserMemory>> entry : grouped.entrySet()) {
            String categoryName = categoryNames.getOrDefault(entry.getKey(), entry.getKey());
            sb.append("【").append(categoryName).append("】");
            for (SysUserMemory m : entry.getValue()) {
                sb.append(m.getContent()).append("；");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<SysUserMemory> query(SysUserMemory memory) {
        PageFilter pageFilter = getPageFilter();
        if (pageFilter != null) {
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return userMemoryMapper.query(memory);
    }

    @Override
    public int delete(long memoryId, int userId) {
        return userMemoryMapper.deleteById(memoryId, userId);
    }

    @Override
    public int update(long memoryId, int userId, String content, String category) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        if (!VALID_CATEGORIES.contains(category)) {
            category = "other";
        }
        return userMemoryMapper.updateContent(memoryId, userId, content, category);
    }
}
