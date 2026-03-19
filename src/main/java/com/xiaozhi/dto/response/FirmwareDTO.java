package com.xiaozhi.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 固件信息响应DTO
 *
 */
@Data
@Schema(description = "固件信息")
public class FirmwareDTO {

    @Schema(description = "固件ID", example = "1")
    private Integer firmwareId;

    @Schema(description = "固件名称", example = "xiaozhi-esp32s3-v1.2.0")
    private String firmwareName;

    @Schema(description = "版本号", example = "1.2.0")
    private String version;

    @Schema(description = "适用芯片型号", example = "esp32s3")
    private String chipModelName;

    @Schema(description = "适用设备类型", example = "esp-box")
    private String deviceType;

    @Schema(description = "固件下载地址")
    private String url;

    @Schema(description = "固件文件大小（字节）", example = "2097152")
    private Long fileSize;

    @Schema(description = "SHA-256哈希值")
    private String fileHash;

    @Schema(description = "版本说明")
    private String description;

    @Schema(description = "是否为默认固件：1-是，0-否", example = "0")
    private String isDefault;

    @Schema(description = "上传用户ID", example = "1")
    private Integer userId;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;
}
