package com.xiaozhi.dialogue.rag;

import com.xiaozhi.common.config.RagConfig;
import com.xiaozhi.dialogue.llm.factory.EmbeddingModelFactory;
import com.xiaozhi.entity.SysKnowledgeBase;
import com.xiaozhi.entity.SysKnowledgeDoc;
import com.xiaozhi.service.SysKnowledgeBaseService;
import com.xiaozhi.service.SysKnowledgeDocService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 文档处理服务
 * 负责文档内容提取、文本分块、向量化存储
 *
 * 注意：文本提取使用原生 Apache Tika 而非 Spring AI TikaDocumentReader，
 * 原因是项目需要更灵活的分块策略控制，且 Tika 原生 API 更轻量，无需引入额外的
 * spring-ai-tika 依赖。
 *
 * @author xiaozhi
 */
@Service
@ConditionalOnProperty(name = "xiaozhi.rag.enabled", havingValue = "true", matchIfMissing = false)
public class DocumentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired
    private SysKnowledgeDocService knowledgeDocService;

    @Autowired
    private SysKnowledgeBaseService knowledgeBaseService;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    @Resource(name = "ragJedisPooled")
    private JedisPooled ragJedisPooled;

    @Autowired
    private RagConfig ragConfig;

    private final Tika tika = new Tika();

    /**
     * 并发控制信号量，限制同时处理的文档数量
     */
    private Semaphore processingSemaphore;

    /**
     * 启动时初始化信号量，并将遗留的 processing 状态文档标记为 failed
     */
    @PostConstruct
    public void init() {
        // 初始化并发控制信号量
        processingSemaphore = new Semaphore(ragConfig.getMaxConcurrentProcessing());
        logger.info("文档处理服务初始化完成，最大并发数: {}", ragConfig.getMaxConcurrentProcessing());

        // 将遗留的 processing 状态文档标记为 failed（上次异常关闭导致）
        try {
            int resetCount = knowledgeDocService.resetProcessingStatus("服务重启导致处理中断，请重新处理");
            if (resetCount > 0) {
                logger.warn("已将 {} 个遗留的 processing 状态文档标记为 failed", resetCount);
            }
        } catch (Exception e) {
            logger.error("重置遗留 processing 状态文档失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步处理文档（使用虚拟线程），添加异常处理防止未捕获异常（W2）
     */
    public void processDocumentAsync(SysKnowledgeDoc doc) {
        Thread.startVirtualThread(() -> {
            try {
                // 获取信号量，达到并发上限时排队等待
                processingSemaphore.acquire();
                try {
                    processDocument(doc);
                } finally {
                    processingSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("文档处理被中断: docId={}", doc.getDocId(), e);
                updateDocFailed(doc.getDocId(), "文档处理被中断");
            } catch (Exception e) {
                logger.error("文档异步处理未捕获异常: docId={}, error={}", doc.getDocId(), e.getMessage(), e);
                updateDocFailed(doc.getDocId(), "未捕获异常: " + truncateMessage(e.getMessage()));
            }
        });
    }

    /**
     * 处理文档：提取文本 -> 分块 -> 向量化存储
     */
    private void processDocument(SysKnowledgeDoc doc) {
        logger.info("开始处理文档: docId={}, docName={}", doc.getDocId(), doc.getDocName());

        // 更新状态为处理中
        SysKnowledgeDoc updateDoc = new SysKnowledgeDoc();
        updateDoc.setDocId(doc.getDocId());
        updateDoc.setStatus("processing");
        knowledgeDocService.update(updateDoc);

        try {
            // 1. 提取文本内容（带超时控制）
            String content = extractTextWithTimeout(doc.getFilePath());
            if (content == null || content.isBlank()) {
                throw new RuntimeException("文档内容为空");
            }

            // 2. 文本分块（C4：使用自定义分块策略，因 Spring AI 1.1.2 的 TokenTextSplitter
            //    需要额外的 jtokkit 依赖且对中文 token 计算不够精确，故使用字符数近似 token 数的方式）
            List<String> chunks;
            try {
                chunks = splitText(content, ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
            } catch (Exception e) {
                throw new RuntimeException("文本分块失败: " + e.getMessage(), e);
            }
            logger.info("文档分块完成: docId={}, 块数={}", doc.getDocId(), chunks.size());

            // 3. 构建 Spring AI Document 列表并向量化存储
            //    W4：每个分块的 Document ID 使用 "docId:chunkIndex" 格式，
            //    metadata 中包含 documentId 字段，便于按文档 ID 删除向量
            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", doc.getDocId().toString());
                metadata.put("knowledgeBaseId", doc.getKnowledgeBaseId() != null ? doc.getKnowledgeBaseId().toString() : "");
                metadata.put("userId", String.valueOf(doc.getUserId()));
                metadata.put("docName", doc.getDocName());
                metadata.put("chunkIndex", String.valueOf(i));
                // 使用确定性 ID，方便后续删除和去重
                String documentId = doc.getDocId() + ":" + i;
                documents.add(new Document(documentId, chunks.get(i), metadata));
            }

            // 根据知识库关联的 embeddingConfigId 获取对应的 VectorStore
            VectorStore targetVectorStore = getVectorStoreForKnowledgeBase(doc.getKnowledgeBaseId());
            if (targetVectorStore == null) {
                throw new RuntimeException("VectorStore 不可用，请检查 Embedding 模型配置和 Redis 连接");
            }

            try {
                targetVectorStore.add(documents);
            } catch (Exception e) {
                throw new RuntimeException("向量化存储失败: " + diagnoseEmbeddingError(e.getMessage()), e);
            }
            logger.info("向量化存储完成: docId={}, 存储块数={}", doc.getDocId(), documents.size());

            // 4. 更新文档状态为就绪（W1：使用 ready 替代 completed）
            updateDoc = new SysKnowledgeDoc();
            updateDoc.setDocId(doc.getDocId());
            updateDoc.setStatus("ready");
            updateDoc.setChunkCount(chunks.size());
            updateDoc.setCharCount(content.length());
            knowledgeDocService.update(updateDoc);

            logger.info("文档处理完成: docId={}, docName={}", doc.getDocId(), doc.getDocName());

        } catch (Exception e) {
            logger.error("文档处理失败: docId={}, error={}", doc.getDocId(), e.getMessage(), e);
            updateDocFailed(doc.getDocId(), truncateMessage(e.getMessage()));
        }
    }

    /**
     * 根据知识库的 embeddingConfigId 获取对应的 VectorStore
     * 如果知识库关联了特定的 Embedding 模型，则使用该模型创建 VectorStore 实例
     * 否则回退到全局默认的 VectorStore
     *
     * @param knowledgeBaseId 知识库ID
     * @return VectorStore 实例
     */
    private VectorStore getVectorStoreForKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            SysKnowledgeBase knowledgeBase = knowledgeBaseService.selectById(knowledgeBaseId);
            if (knowledgeBase != null && knowledgeBase.getEmbeddingConfigId() != null) {
                try {
                    EmbeddingModel embeddingModel = embeddingModelFactory.takeEmbeddingModel(knowledgeBase.getEmbeddingConfigId());
                    if (embeddingModel != null) {
                        logger.info("使用知识库关联的 Embedding 模型: knowledgeBaseId={}, embeddingConfigId={}",
                                knowledgeBaseId, knowledgeBase.getEmbeddingConfigId());
                        return RedisVectorStore.builder(ragJedisPooled, embeddingModel)
                                .indexName(ragConfig.getIndexName())
                                .prefix(ragConfig.getPrefix())
                                .initializeSchema(true)
                                .build();
                    }
                } catch (Exception e) {
                    logger.warn("获取知识库关联的 Embedding 模型失败，回退到全局默认: knowledgeBaseId={}, error={}",
                            knowledgeBaseId, e.getMessage());
                }
            }
        }
        // 回退到全局默认 Embedding 模型动态创建 VectorStore
        logger.info("回退到全局默认: vectorStore={}, ragJedisPooled={}, embeddingModelFactory={}",
                vectorStore != null, ragJedisPooled != null, embeddingModelFactory != null);
        if (vectorStore != null) {
            return vectorStore;
        }
        try {
            EmbeddingModel defaultModel = embeddingModelFactory.defaultEmbeddingModel();
            logger.info("获取到默认 Embedding 模型: {}, ragJedisPooled={}", defaultModel != null, ragJedisPooled != null);
            if (defaultModel != null && ragJedisPooled != null) {
                logger.info("使用全局默认 Embedding 模型创建 VectorStore: knowledgeBaseId={}", knowledgeBaseId);
                RedisVectorStore store = RedisVectorStore.builder(ragJedisPooled, defaultModel)
                        .indexName(ragConfig.getIndexName())
                        .prefix(ragConfig.getPrefix())
                        .initializeSchema(true)
                        .build();
                store.afterPropertiesSet();
                return store;
            }
        } catch (Exception e) {
            logger.error("创建默认 VectorStore 失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 根据 Embedding API 异常信息诊断错误原因，返回用户友好的中文提示
     */
    private String diagnoseEmbeddingError(String errorMsg) {
        if (errorMsg == null) {
            return "未知错误";
        }
        if (errorMsg.contains("404")) {
            return "Embedding API 端点不存在（HTTP 404），请检查 sys_config 表中 apiUrl 配置是否正确";
        }
        if (errorMsg.contains("401") || errorMsg.toLowerCase().contains("unauthorized")) {
            return "API 认证失败（HTTP 401），请检查 apiKey 是否正确";
        }
        if (errorMsg.contains("429")) {
            return "API 请求频率超限（HTTP 429），请稍后重试";
        }
        if (errorMsg.contains("500")) {
            return "Embedding 服务内部错误（HTTP 500），请检查服务端状态";
        }
        return errorMsg;
    }

    /**
     * 使用 Apache Tika 提取文档文本内容，带超时控制
     * 使用 CompletableFuture.supplyAsync().orTimeout() 包装 Tika 解析
     */
    private String extractTextWithTimeout(String filePath) throws Exception {
        int timeoutSeconds = ragConfig.getParseTimeoutSeconds();
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return extractText(filePath);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).orTimeout(timeoutSeconds, TimeUnit.SECONDS).join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                throw new RuntimeException("文档解析超时（超过 " + timeoutSeconds + " 秒）");
            }
            // 解包原始异常
            if (e.getCause() instanceof RuntimeException && e.getCause().getCause() != null) {
                throw new RuntimeException("Tika 解析失败: " + e.getCause().getCause().getMessage(), e.getCause().getCause());
            }
            throw new RuntimeException("Tika 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 Apache Tika 提取文档文本内容
     * W3：保持使用原生 Tika API，而非 Spring AI TikaDocumentReader，
     * 原因是需要独立控制文本提取逻辑，避免引入不必要的 spring-ai-tika 依赖
     */
    private String extractText(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("文件不存在: " + filePath);
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return tika.parseToString(inputStream);
        }
    }

    /**
     * 文本分块策略：按段落优先分块，支持重叠
     * C4：未使用 Spring AI TokenTextSplitter，因其依赖 jtokkit 库且对中文 token
     * 计算不够准确。此处使用字符数近似 token 数（中文1字≈1-2 token）
     *
     * @param text      原始文本
     * @param chunkSize 每块大小（字符数）
     * @param overlap   重叠字符数
     * @return 分块列表
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 先按段落分割
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (currentChunk.length() + trimmed.length() + 1 > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                // 保留重叠部分
                String content = currentChunk.toString();
                if (content.length() > overlap) {
                    currentChunk = new StringBuilder(content.substring(content.length() - overlap));
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n");
            }
            currentChunk.append(trimmed);
        }

        // 处理最后一块
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        // 如果没有段落分隔，使用滑动窗口分块
        if (chunks.isEmpty() && !text.isBlank()) {
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                chunks.add(text.substring(start, end).trim());
                start += chunkSize - overlap;
            }
        }

        return chunks;
    }

    /**
     * 删除文档对应的所有向量数据
     * W4：使用文档处理时生成的确定性 Document ID（格式为 "docId:chunkIndex"）进行删除，
     * 而非使用 filter 表达式字符串
     *
     * @param docId 文档ID
     */
    public void deleteDocumentVectors(Long docId) {
        try {
            // 先查询文档获取分块数量，构建所有分块的 Document ID 列表
            SysKnowledgeDoc doc = knowledgeDocService.selectById(docId);
            if (doc != null && doc.getChunkCount() != null && doc.getChunkCount() > 0) {
                List<String> documentIds = new ArrayList<>();
                for (int i = 0; i < doc.getChunkCount(); i++) {
                    documentIds.add(docId + ":" + i);
                }
                vectorStore.delete(documentIds);
                logger.info("已删除文档向量数据: docId={}, 删除数量={}", docId, documentIds.size());
            } else {
                logger.warn("文档无分块信息，跳过向量删除: docId={}", docId);
            }
        } catch (Exception e) {
            logger.warn("删除文档向量数据失败: docId={}, error={}", docId, e.getMessage());
        }
    }

    /**
     * 更新文档状态为失败
     */
    private void updateDocFailed(Long docId, String errorMsg) {
        try {
            SysKnowledgeDoc failDoc = new SysKnowledgeDoc();
            failDoc.setDocId(docId);
            failDoc.setStatus("failed");
            failDoc.setErrorMsg(errorMsg != null ? errorMsg : "未知错误");
            knowledgeDocService.update(failDoc);
        } catch (Exception ex) {
            logger.error("更新文档失败状态异常: docId={}", docId, ex);
        }
    }

    /**
     * 截断错误消息，最大 500 字符
     */
    private String truncateMessage(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
