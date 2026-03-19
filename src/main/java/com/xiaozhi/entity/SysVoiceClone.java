package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 音色克隆实体类
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "音色克隆信息")
public class SysVoiceClone extends Base<SysVoiceClone> {

    @Schema(description = "克隆音色ID")
    private Integer cloneId;

    @Schema(description = "用户自定义名称")
    private String cloneName;

    @Schema(description = "服务提供商(volcengine/aliyun-nls)")
    private String provider;

    @Schema(description = "TTS配置ID")
    private Integer configId;

    @Schema(description = "云API训练任务ID")
    private String taskId;

    @Schema(description = "训练完成后的音色ID")
    private String voiceId;

    @Schema(description = "上传的音频样本路径")
    private String samplePath;

    @Schema(description = "训练状态(uploading/training/ready/failed)")
    private String status;

    @Schema(description = "失败错误信息")
    private String errorMessage;
}
