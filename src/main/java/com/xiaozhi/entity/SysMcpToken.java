package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * MCP接入点Token
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP接入点Token")
public class SysMcpToken extends Base<SysMcpToken> {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "MCP专用Token")
    private String token;

    @Schema(description = "Token名称")
    private String tokenName;

    @Schema(description = "启用状态：1-启用 0-禁用")
    private Integer enabled;

    @Schema(description = "最后使用时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUsedAt;
}
