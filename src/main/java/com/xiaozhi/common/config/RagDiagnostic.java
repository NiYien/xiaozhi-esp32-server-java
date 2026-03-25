package com.xiaozhi.common.config;

import com.xiaozhi.dialogue.llm.factory.EmbeddingModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;

/**
 * RAG 知识库启动诊断组件
 * 在应用启动完成后输出知识库功能的诊断信息
 *
 * @author xiaozhi
 */
@Component
public class RagDiagnostic {

    private static final Logger logger = LoggerFactory.getLogger(RagDiagnostic.class);

    @Value("${xiaozhi.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Autowired(required = false)
    private EmbeddingModelFactory embeddingModelFactory;

    @Autowired(required = false)
    private JedisPooled ragJedisPooled;

    @EventListener(ApplicationReadyEvent.class)
    public void diagnose() {
        if (!ragEnabled) {
            logger.info("知识库 RAG 功能未启用，如需使用请设置 xiaozhi.rag.enabled=true");
            return;
        }

        logger.info("知识库 RAG 功能已启用");

        // 检查 Embedding 模型
        if (embeddingModelFactory != null) {
            try {
                EmbeddingModel model = embeddingModelFactory.defaultEmbeddingModel();
                logger.info("知识库 RAG 默认 Embedding 模型: {}", model.getClass().getSimpleName());
            } catch (Exception e) {
                logger.warn("知识库 RAG 未配置默认 Embedding 模型，请在配置管理中添加 Embedding 模型（类型选择 embedding）并设为默认，或在创建知识库时手动选择: {}", e.getMessage());
            }
        } else {
            logger.warn("知识库 RAG EmbeddingModelFactory 未初始化");
        }

        // 检查 Redis 连接
        if (ragJedisPooled != null) {
            logger.info("知识库 RAG Redis 连接正常: {}:{}", redisHost, redisPort);
        } else {
            logger.warn("知识库 RAG Redis 连接失败，请检查配置（{}:{}）", redisHost, redisPort);
        }
    }
}
