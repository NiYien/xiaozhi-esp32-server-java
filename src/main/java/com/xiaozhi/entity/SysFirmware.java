package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 固件信息表
 *
 * @author Joey
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"startTime", "endTime", "start", "limit"})
@Schema(description = "固件信息")
public class SysFirmware extends Base<SysFirmware> {

    @Schema(description = "固件ID")
    private Integer firmwareId;

    @Schema(description = "固件名称")
    private String firmwareName;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "适用芯片型号，NULL表示通用")
    private String chipModelName;

    @Schema(description = "适用设备类型，NULL表示通用")
    private String deviceType;

    @Schema(description = "固件下载地址")
    private String url;

    @Schema(description = "固件文件大小（字节）")
    private Long fileSize;

    @Schema(description = "SHA-256哈希值")
    private String fileHash;

    @Schema(description = "版本说明")
    private String description;

    @Schema(description = "是否为默认固件：1-是，0-否")
    private String isDefault;
}
