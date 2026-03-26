package com.xiaozhi.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 角色信息DTO
 *
 */
@Data
@Schema(description = "角色信息")
public class RoleDTO {

    @Schema(description = "角色ID", example = "1")
    private Integer roleId;

    @Schema(description = "角色名称", example = "小智助手")
    private String roleName;

    @Schema(description = "角色描述", example = "友好的智能助手")
    private String roleDesc;

    @Schema(description = "角色头像", example = "https://example.com/avatar.png")
    private String avatar;

    @Schema(description = "角色音效", example = "https://example.com/sound.mp3")
    private String roleSound;

    @Schema(description = "语音名称", example = "zh-CN-XiaoxiaoNeural")
    private String voiceName;

    @Schema(description = "语音音调", example = "1.0")
    private Float ttsPitch;

    @Schema(description = "语音语速", example = "1.0")
    private Float ttsSpeed;

    @Schema(description = "状态(1启用 0禁用)", example = "1", allowableValues = {"0", "1"})
    private String state;

    @Schema(description = "TTS服务ID", example = "1")
    private Integer ttsId;

    @Schema(description = "模型ID", example = "1")
    private Integer modelId;

    @Schema(description = "模型名称", example = "gpt-4")
    private String modelName;

    @Schema(description = "STT服务ID", example = "1")
    private Integer sttId;

    @Schema(description = "温度参数", example = "0.7")
    private Double temperature;

    @Schema(description = "Top-P参数", example = "0.9")
    private Double topP;

    @Schema(description = "语音活动检测-能量阈值", example = "0.5")
    private Float vadEnergyTh;

    @Schema(description = "语音活动检测-语音阈值", example = "0.5")
    private Float vadSpeechTh;

    @Schema(description = "语音活动检测-静音阈值", example = "0.5")
    private Float vadSilenceTh;

    @Schema(description = "语音活动检测-静音毫秒数", example = "500")
    private Integer vadSilenceMs;

    @Schema(description = "模型提供商", example = "openai")
    private String modelProvider;

    @Schema(description = "TTS服务提供商", example = "edge")
    private String ttsProvider;

    @Schema(description = "是否默认角色(1是 0否)", example = "0", allowableValues = {"0", "1"})
    private String isDefault;

    @Schema(description = "总设备数", example = "5")
    private Integer totalDevice;

    @Schema(description = "数据集ID", example = "dataset_001")
    private String datasetId;

    @Schema(description = "记忆类型", example = "long")
    private String memoryType;

    @Schema(description = "关联知识库ID列表，逗号分隔", example = "1,2,3")
    private String knowledgeBaseIds;

    @Schema(description = "RAG检索返回条数", example = "5")
    private Integer ragTopK;

    @Schema(description = "RAG相似度阈值", example = "0.7")
    private Double ragThreshold;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2024-01-01 00:00:00")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间", example = "2024-12-01 10:30:00")
    private Date updateTime;
}
