package com.xiaozhi.dialogue.rag;

import com.xiaozhi.common.config.RagConfig;
import com.xiaozhi.entity.SysKnowledgeDoc;
import com.xiaozhi.service.SysKnowledgeDocService;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private SysKnowledgeDocService knowledgeDocService;

    @Autowired
    private RagConfig ragConfig;

    private final Tika tika = new Tika();

    /**
     * 异步处理文档（使用虚拟线程），添加异常处理防止未捕获异常（W2）
     */
    public void processDocumentAsync(SysKnowledgeDoc doc) {
        Thread.startVirtualThread(() -> {
            try {
                processDocument(doc);
            } catch (Exception e) {
                logger.error("文档异步处理未捕获异常: docId={}, error={}", doc.getDocId(), e.getMessage(), e);
                // 确保状态更新为失败
                try {
                    SysKnowledgeDoc failDoc = new SysKnowledgeDoc();
                    failDoc.setDocId(doc.getDocId());
                    failDoc.setStatus("failed");
                    failDoc.setErrorMsg("未捕获异常: " + (e.getMessage() != null ?
                            e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知错误"));
                    knowledgeDocService.update(failDoc);
                } catch (Exception ex) {
                    logger.error("更新文档失败状态异常: docId={}", doc.getDocId(), ex);
                }
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
            // 1. 提取文本内容
            String content = extractText(doc.getFilePath());
            if (content == null || content.isBlank()) {
                throw new RuntimeException("文档内容为空");
            }

            // 2. 文本分块（C4：使用自定义分块策略，因 Spring AI 1.1.2 的 TokenTextSplitter
            //    需要额外的 jtokkit 依赖且对中文 token 计算不够精确，故使用字符数近似 token 数的方式）
            List<String> chunks = splitText(content, ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
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

            vectorStore.add(documents);
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
            updateDoc = new SysKnowledgeDoc();
            updateDoc.setDocId(doc.getDocId());
            updateDoc.setStatus("failed");
            updateDoc.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "未知错误");
            knowledgeDocService.update(updateDoc);
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
}
