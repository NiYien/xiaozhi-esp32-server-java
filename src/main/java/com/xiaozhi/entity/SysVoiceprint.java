package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户声纹实体
 * 存储用户的声纹嵌入向量（192维float32）
 */
@Data
@Accessors(chain = true)
@Schema(description = "用户声纹信息")
public class SysVoiceprint implements Serializable {

    @Schema(description = "声纹ID")
    private Long voiceprintId;

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "采集声纹时使用的设备ID")
    private String deviceId;

    @Schema(description = "声纹名称，用于标识说话人")
    private String voiceprintName;

    /**
     * 声纹嵌入向量原始字节（192维float32 = 768字节）
     */
    @JsonIgnore
    @Schema(description = "声纹嵌入向量（二进制）")
    private byte[] embedding;

    @Schema(description = "采样次数，多次录制通过加权平均提高精度")
    private Integer sampleCount;

    @Schema(description = "状态：1-有效，0-无效")
    private String state;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;
}
