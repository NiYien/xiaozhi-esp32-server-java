package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 测试语音合成请求参数
 *
 */
@Data
@Schema(description = "测试语音合成请求参数")
public class TestVoiceParam {

    @Schema(description = "消息文本", example = "你好，欢迎使用小智", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "消息文本不能为空")
    private String message;

    @Schema(description = "语音合成提供方", example = "edge", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "提供方不能为空")
    private String provider;

    @Schema(description = "TTS配置ID", example = "1")
    private Integer ttsId;

    @Schema(description = "音色名称", example = "zh-CN-XiaoxiaoNeural")
    private String voiceName;

    @Schema(description = "语音音调(0.5-2.0)", example = "1.0")
    private Float ttsPitch;

    @Schema(description = "语音语速(0.5-2.0)", example = "1.0")
    private Float ttsSpeed;
}
