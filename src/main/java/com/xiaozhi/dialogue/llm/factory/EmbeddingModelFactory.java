package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class EmbeddingModelFactory {

    private final Logger logger = LoggerFactory.getLogger(EmbeddingModelFactory.class);
    @Autowired
    private SysConfigService configService;

    public EmbeddingModel takeEmbeddingModel(Integer configId) {
        //SysConfig config = configService.selectModelType("embedding");
        SysConfig config = configService.selectConfigById(configId);
        return takeEmbeddingModel(config);
    }

    public EmbeddingModel defaultEmbeddingModel() {
        SysConfig config = configService.selectModelType(SysConfig.ModelType.embedding.getValue());
        return takeEmbeddingModel(config);
    }

    public @NotNull EmbeddingModel takeEmbeddingModel(SysConfig config) {
        Assert.notNull(config, "未配置向量模型");
        String provider = config.getProvider().toLowerCase();
        switch (provider) {
            case "openai":
                return newOpenAiEmbeddingModel(config);
            case "ollama":
                return newOllamaEmbeddingModel(config);
            case "zhipu":
                return newZhipuEmbeddingModel(config);
            // 默认为 openai 协议
            default:
                return newOpenAiEmbeddingModel(config);
        }
    }

    private EmbeddingModel newOllamaEmbeddingModel(SysConfig config) {
        String endpoint = config.getApiUrl();
        String model = config.getConfigName();
        var ollamaApi = OllamaApi.builder().baseUrl(endpoint).build();

        var ollamaOptions = OllamaEmbeddingOptions.builder()
                .model(model)
                .build();

        var embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(ollamaOptions)
                .build();
        logger.info("Using Ollama EmbeddingModel baseUrl: {}, name:{}",endpoint, model);
        return embeddingModel;
    }

    private EmbeddingModel newOpenAiEmbeddingModel(SysConfig config) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("Content-Type", "application/json");

        // 拆分 URL：Spring AI OpenAiApi 内部用 URI.resolve("/v1/embeddings") 拼接路径，
        // 绝对路径会覆盖 baseUrl 中的子路径（如 /compatible-mode/v1/）。
        // 解决方案：将 URL 拆分为 scheme+host+port 作为 baseUrl，
        // 完整路径 + "/embeddings" 作为自定义 embeddingsPath。
        String originalUrl = config.getApiUrl();
        String baseUrl;
        String embeddingsPath;
        try {
            java.net.URI uri = java.net.URI.create(originalUrl);
            baseUrl = uri.getScheme() + "://" + uri.getAuthority() + "/";
            String path = uri.getPath();
            // 确保路径末尾有 /
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            embeddingsPath = path + "embeddings";
        } catch (Exception e) {
            // URL 解析失败时回退到原始行为
            baseUrl = originalUrl;
            if (!baseUrl.endsWith("/")) {
                baseUrl = baseUrl + "/";
            }
            embeddingsPath = "/v1/embeddings";
        }

        // LM Studio不支持Http/2，所以需要强制使用HTTP/1.1
        var openAiApi = OpenAiApi.builder()
                .apiKey(StringUtils.hasText(config.getApiKey()) ? new SimpleApiKey(config.getApiKey()) : new NoopApiKey())
                .baseUrl(baseUrl)
                .embeddingsPath(embeddingsPath)
                .headers(headers)
                .webClientBuilder(WebClient.builder()
                        // Force HTTP/1.1 for streaming
                        .clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .restClientBuilder(RestClient.builder()
                        // Force HTTP/1.1 for non-streaming
                        .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(Duration.ofSeconds(30))
                                .build())))
                .build();
        var openAiEmbeddingOptions = OpenAiEmbeddingOptions.builder().model(config.getConfigName()).build();

        var embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,openAiEmbeddingOptions);
        logger.info("Using OpenAi EmbeddingModel baseUrl: {}, embeddingsPath: {}, name:{}", baseUrl, embeddingsPath, config.getConfigName());
        return embeddingModel;
    }
    private EmbeddingModel newZhipuEmbeddingModel(SysConfig config) {
        var zhiPuAiApi = ZhiPuAiApi.builder()
                .baseUrl(config.getApiUrl())
                .apiKey(config.getApiKey())
                .build();

        var zhipuAiEmbeddingOptions = ZhiPuAiEmbeddingOptions.builder().model(config.getConfigName()).build();

        var chatModel = new ZhiPuAiEmbeddingModel(zhiPuAiApi, MetadataMode.EMBED,zhipuAiEmbeddingOptions,
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
        logger.info("Using ZhiPuAi EmbeddingModel baseUrl: {}, name:{}",config.getApiUrl(), config.getConfigName());
        return chatModel;
    }

}
