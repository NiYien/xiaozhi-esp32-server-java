package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新配置请求参数
 *
 */
@Data
@Schema(description = "更新配置请求参数")
public class ConfigUpdateParam {

    @Schema(description = "设备ID", example = "ESP32_001")
    private String deviceId;

    @Schema(description = "角色ID", example = "1")
    private Integer roleId;

    @Schema(description = "配置名称", example = "OpenAI GPT-4配置")
    private String configName;

    @Schema(description = "配置描述", example = "使用GPT-4进行对话")
    private String configDesc;

    @Schema(description = "配置类型", example = "llm", allowableValues = {"llm", "stt", "tts"})
    private String configType;

    @Schema(description = "模型类型", example = "chat", allowableValues = {"chat", "vision", "intent", "embedding", "director"})
    private String modelType;

    @Schema(description = "服务提供商", example = "openai", allowableValues = {"openai", "qwen", "coze", "dify", "tencent", "aliyun", "vosk"})
    private String provider;

    @Schema(description = "服务提供商分配的AppId", example = "app_123456")
    private String appId;

    @Schema(description = "服务提供商分配的ApiKey", example = "sk-xxxxx")
    private String apiKey;

    @Schema(description = "服务提供商分配的ApiSecret", example = "secret_xxxxx")
    private String apiSecret;

    @Schema(description = "服务提供商分配的Access Key", example = "ak_xxxxx")
    private String ak;

    @Schema(description = "服务提供商分配的Secret Key", example = "sk_xxxxx")
    private String sk;

    @Schema(description = "服务提供商的API地址", example = "https://api.openai.com/v1")
    private String apiUrl;

    @Schema(description = "服务提供商状态", example = "1", allowableValues = {"0", "1"})
    private String state;

    @Schema(description = "是否作为默认配置", example = "0", allowableValues = {"0", "1"})
    private String isDefault;
}
