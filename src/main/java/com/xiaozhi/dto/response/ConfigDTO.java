package com.xiaozhi.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 配置信息响应DTO
 *
 */
@Data
@Schema(description = "配置信息")
public class ConfigDTO {

    @Schema(description = "配置ID", example = "1")
    private Integer configId;

    @Schema(description = "用户ID", example = "1")
    private Integer userId;

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

    @Schema(description = "服务提供商的API地址", example = "https://api.openai.com/v1")
    private String apiUrl;

    @Schema(description = "服务提供商状态", example = "1", allowableValues = {"0", "1"})
    private String state;

    @Schema(description = "是否作为默认配置", example = "1", allowableValues = {"0", "1"})
    private String isDefault;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2024-01-01 00:00:00")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间", example = "2024-12-01 10:30:00")
    private Date updateTime;
}
