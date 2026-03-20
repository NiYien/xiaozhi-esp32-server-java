package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * MCP Server 连接配置
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP Server 配置信息")
public class SysMcpServer extends Base<SysMcpServer> {

    @Schema(description = "服务器ID")
    private Long serverId;

    @Schema(description = "服务器显示名称")
    private String serverName;

    @Schema(description = "唯一标识（用于工具名称前缀）")
    private String serverCode;

    @Schema(description = "传输类型：sse / streamable_http")
    private String transportType;

    @Schema(description = "MCP Server URL")
    private String serverUrl;

    @Schema(description = "认证方式：none / api_key / bearer")
    private String authType;

    @Schema(description = "认证令牌")
    private String authToken;

    @Schema(description = "启用状态：1-启用 0-禁用")
    private Integer enabled;
}
