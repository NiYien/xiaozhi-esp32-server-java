package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EmbeddingModelFactory 单元测试
 * 测试 provider 选择、null config 断言、unknown provider 默认 OpenAI
 */
class EmbeddingModelFactoryTest extends BaseUnitTest {

    @Mock
    private SysConfigService configService;

    @InjectMocks
    private EmbeddingModelFactory embeddingModelFactory;

    @Test
    void takeEmbeddingModel_openaiProvider_createsOpenAiModel() {
        SysConfig config = new SysConfig()
                .setProvider("openai")
                .setConfigId(1)
                .setConfigName("text-embedding-3-small")
                .setApiKey("test-key")
                .setApiUrl("https://api.openai.com");

        EmbeddingModel result = embeddingModelFactory.takeEmbeddingModel(config);

        assertNotNull(result, "应创建 OpenAI EmbeddingModel");
    }

    @Test
    void takeEmbeddingModel_ollamaProvider_createsOllamaModel() {
        SysConfig config = new SysConfig()
                .setProvider("ollama")
                .setConfigId(2)
                .setConfigName("nomic-embed-text")
                .setApiUrl("http://localhost:11434");

        EmbeddingModel result = embeddingModelFactory.takeEmbeddingModel(config);

        assertNotNull(result, "应创建 Ollama EmbeddingModel");
    }

    @Test
    void takeEmbeddingModel_unknownProvider_defaultsToOpenAi() {
        SysConfig config = new SysConfig()
                .setProvider("custom-provider")
                .setConfigId(3)
                .setConfigName("custom-model")
                .setApiKey("test-key")
                .setApiUrl("https://custom.api.com");

        EmbeddingModel result = embeddingModelFactory.takeEmbeddingModel(config);

        assertNotNull(result, "未知 provider 应默认使用 OpenAI 协议");
    }

    @Test
    void takeEmbeddingModel_nullConfig_throwsAssertionError() {
        assertThrows(IllegalArgumentException.class,
                () -> embeddingModelFactory.takeEmbeddingModel((SysConfig) null),
                "null config 应抛出异常");
    }

    @Test
    void takeEmbeddingModel_byConfigId_loadsConfigFromService() {
        SysConfig config = new SysConfig()
                .setProvider("openai")
                .setConfigId(10)
                .setConfigName("embedding-model")
                .setApiKey("key")
                .setApiUrl("https://api.example.com");

        when(configService.selectConfigById(10)).thenReturn(config);

        EmbeddingModel result = embeddingModelFactory.takeEmbeddingModel(10);

        assertNotNull(result);
        verify(configService).selectConfigById(10);
    }
}
