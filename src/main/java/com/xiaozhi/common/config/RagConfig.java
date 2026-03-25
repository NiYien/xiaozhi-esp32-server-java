package com.xiaozhi.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import redis.clients.jedis.JedisPooled;

/**
 * RAG 知识库配置类
 * 通过 xiaozhi.rag.enabled=true 启用
 *
 * VectorStore 和 EmbeddingModel 不再作为全局 Bean 创建，
 * 由 DocumentProcessingService 和 KnowledgeRetrievalService 根据知识库的 embeddingConfigId 动态获取
 *
 * @author xiaozhi
 */
@Getter
@Configuration
@ConditionalOnProperty(name = "xiaozhi.rag.enabled", havingValue = "true", matchIfMissing = false)
public class RagConfig {

    private static final Logger logger = LoggerFactory.getLogger(RagConfig.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${xiaozhi.rag.index-name:xiaozhi-knowledge-vectors}")
    private String indexName;

    @Value("${xiaozhi.rag.prefix:knowledge:}")
    private String prefix;

    @Value("${xiaozhi.rag.chunk-size:800}")
    private int chunkSize;

    @Value("${xiaozhi.rag.chunk-overlap:100}")
    private int chunkOverlap;

    @Value("${xiaozhi.rag.top-k:5}")
    private int topK;

    @Value("${xiaozhi.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${xiaozhi.rag.max-context-chars:2000}")
    private int maxContextChars;

    @Value("${xiaozhi.rag.parse-timeout-seconds:60}")
    private int parseTimeoutSeconds;

    @Value("${xiaozhi.rag.max-file-size-mb:50}")
    private int maxFileSizeMb;

    @Value("${xiaozhi.rag.max-concurrent-processing:5}")
    private int maxConcurrentProcessing;

    @Bean
    public JedisPooled ragJedisPooled() {
        try {
            logger.info("初始化 RAG Redis 连接: {}:{}", redisHost, redisPort);
            return new JedisPooled(redisHost, redisPort);
        } catch (Exception e) {
            logger.warn("RAG Redis 连接失败，知识库功能不可用: {}", e.getMessage());
            return null;
        }
    }
}
