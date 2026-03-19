package com.xiaozhi.common.config;

import com.xiaozhi.dialogue.llm.factory.EmbeddingModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    @Bean
    public JedisPooled jedisPooled() {
        logger.info("初始化 RAG Redis 连接: {}:{}", redisHost, redisPort);
        return new JedisPooled(redisHost, redisPort);
    }

    @Bean
    public EmbeddingModel ragEmbeddingModel() {
        logger.info("初始化 RAG 向量模型（使用系统默认 embedding 配置）");
        return embeddingModelFactory.defaultEmbeddingModel();
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel ragEmbeddingModel) {
        logger.info("初始化 Redis VectorStore，索引名称: {}，前缀: {}", indexName, prefix);

        return RedisVectorStore.builder(jedisPooled, ragEmbeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(true)
                .build();
    }
}
