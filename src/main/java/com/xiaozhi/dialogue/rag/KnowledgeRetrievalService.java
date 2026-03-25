package com.xiaozhi.dialogue.rag;

import com.xiaozhi.common.config.RagConfig;
import jakarta.annotation.Resource;
import com.xiaozhi.dialogue.llm.factory.EmbeddingModelFactory;
import com.xiaozhi.entity.SysKnowledgeBase;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysKnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识检索服务
 * 负责从向量数据库中检索与用户问题相关的知识片段
 *
 * @author xiaozhi
 */
@Service
@ConditionalOnProperty(name = "xiaozhi.rag.enabled", havingValue = "true", matchIfMissing = false)
public class KnowledgeRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired
    private RagConfig ragConfig;

    @Autowired
    private SysKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    @Resource(name = "ragJedisPooled")
    private JedisPooled ragJedisPooled;

    /**
     * 根据用户查询检索相关知识（指定角色的知识库）
     * W5：优先使用角色级别的 ragTopK 和 ragThreshold 配置，回退到全局配置
     *
     * @param query 用户查询文本
     * @param role  角色信息（包含 knowledgeBaseIds、ragTopK、ragThreshold）
     * @return 格式化后的知识上下文文本
     */
    public String retrieveKnowledge(String query, SysRole role) {
        if (query == null || query.isBlank() || role == null) {
            return null;
        }

        // 解析角色关联的知识库ID列表
        String knowledgeBaseIds = role.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isBlank()) {
            return null;
        }

        List<String> kbIds = Arrays.stream(knowledgeBaseIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (kbIds.isEmpty()) {
            return null;
        }

        try {
            // W5：角色级检索参数优先于全局配置
            int topK = role.getRagTopK() != null ? role.getRagTopK() : ragConfig.getTopK();
            double threshold = role.getRagThreshold() != null ? role.getRagThreshold() : ragConfig.getSimilarityThreshold();

            // 构建过滤条件：匹配任一知识库ID
            FilterExpressionBuilder b = new FilterExpressionBuilder();
            FilterExpressionBuilder.Op filterExpression;
            if (kbIds.size() == 1) {
                filterExpression = b.eq("knowledgeBaseId", kbIds.get(0));
            } else {
                filterExpression = b.in("knowledgeBaseId", kbIds.toArray());
            }

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(threshold)
                    .filterExpression(filterExpression.build())
                    .build();

            // 根据知识库关联的 embeddingConfigId 获取对应的 VectorStore
            VectorStore targetVectorStore = getVectorStoreForKnowledgeBases(kbIds);

            List<Document> results = targetVectorStore.similaritySearch(searchRequest);

            if (results == null || results.isEmpty()) {
                logger.debug("未检索到相关知识: query={}, knowledgeBaseIds={}", query, knowledgeBaseIds);
                return null;
            }

            logger.info("检索到 {} 条相关知识: knowledgeBaseIds={}", results.size(), knowledgeBaseIds);
            return formatKnowledgeContext(results);

        } catch (Exception e) {
            logger.warn("知识检索失败: knowledgeBaseIds={}, error={}", knowledgeBaseIds, e.getMessage());
            return null;
        }
    }

    /**
     * 根据知识库列表获取对应的 VectorStore
     * 使用第一个有 embeddingConfigId 的知识库的模型，回退到全局默认
     *
     * @param kbIds 知识库ID列表
     * @return VectorStore 实例
     */
    private VectorStore getVectorStoreForKnowledgeBases(List<String> kbIds) {
        for (String kbIdStr : kbIds) {
            try {
                Long kbId = Long.parseLong(kbIdStr);
                SysKnowledgeBase knowledgeBase = knowledgeBaseService.selectById(kbId);
                if (knowledgeBase != null && knowledgeBase.getEmbeddingConfigId() != null) {
                    EmbeddingModel embeddingModel = embeddingModelFactory.takeEmbeddingModel(knowledgeBase.getEmbeddingConfigId());
                    if (embeddingModel != null) {
                        logger.info("检索使用知识库关联的 Embedding 模型: knowledgeBaseId={}, embeddingConfigId={}",
                                kbId, knowledgeBase.getEmbeddingConfigId());
                        return RedisVectorStore.builder(ragJedisPooled, embeddingModel)
                                .indexName(ragConfig.getIndexName())
                                .prefix(ragConfig.getPrefix())
                                .initializeSchema(true)
                                .build();
                    }
                }
            } catch (Exception e) {
                logger.warn("获取知识库 Embedding 模型失败: kbId={}, error={}", kbIdStr, e.getMessage());
            }
        }
        // 回退到全局默认
        if (vectorStore != null) {
            return vectorStore;
        }
        try {
            EmbeddingModel defaultModel = embeddingModelFactory.defaultEmbeddingModel();
            if (defaultModel != null && ragJedisPooled != null) {
                return RedisVectorStore.builder(ragJedisPooled, defaultModel)
                        .indexName(ragConfig.getIndexName())
                        .prefix(ragConfig.getPrefix())
                        .initializeSchema(true)
                        .build();
            }
        } catch (Exception e) {
            logger.error("创建默认 VectorStore 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 将检索结果格式化为设计要求的格式并按 maxTokens 截断
     * C5：格式为 "\n\n## 相关知识\n{chunk1}\n---\n{chunk2}"
     * 截断阈值使用 maxContextChars 配置（默认2000字符，近似2000 token）
     *
     * @param documents 检索到的文档列表
     * @return 格式化文本
     */
    private String formatKnowledgeContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return null;
        }

        int maxChars = ragConfig.getMaxContextChars();
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 相关知识\n");

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String chunkText = doc.getText();

            // 检查是否会超出截断阈值
            if (sb.length() + chunkText.length() > maxChars) {
                // 截取剩余可用空间的文本
                int remaining = maxChars - sb.length();
                if (remaining > 50) {
                    // 至少保留50字符才有意义
                    sb.append(chunkText, 0, remaining);
                }
                break;
            }

            sb.append(chunkText);
            // 在分块之间添加分隔符（最后一块不加）
            if (i < documents.size() - 1) {
                sb.append("\n---\n");
            }
        }

        return sb.toString();
    }
}
