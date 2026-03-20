-- Phase 4: MCP Server 管理
-- 创建 sys_mcp_server 表，用于存储 MCP Server 连接配置

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `xiaozhi`.`sys_mcp_server`;
CREATE TABLE `xiaozhi`.`sys_mcp_server` (
  `serverId` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '服务器ID',
  `serverName` varchar(100) NOT NULL COMMENT '服务器显示名称',
  `serverCode` varchar(100) NOT NULL COMMENT '唯一标识（用于工具名称前缀）',
  `transportType` varchar(20) NOT NULL DEFAULT 'sse' COMMENT '传输类型：sse / streamable_http',
  `serverUrl` varchar(500) NOT NULL COMMENT 'MCP Server URL',
  `authType` varchar(20) NOT NULL DEFAULT 'none' COMMENT '认证方式：none / api_key / bearer',
  `authToken` varchar(500) DEFAULT NULL COMMENT '认证令牌',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：1-启用 0-禁用',
  `userId` int DEFAULT NULL COMMENT '创建人ID',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`serverId`),
  UNIQUE KEY `uk_server_code` (`serverCode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP Server 连接配置表';

-- 插入 MCP Server 管理菜单权限（放在配置管理子菜单下）
INSERT INTO `xiaozhi`.`sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(7, 'MCP Server', 'system:config:mcp-server', 'menu', '/config/mcp-server', 'page/config/McpServer', NULL, 6, '1', '1');

-- 管理员角色赋予 MCP Server 管理权限
INSERT INTO `xiaozhi`.`sys_role_permission` (`roleId`, `permissionId`)
SELECT 1, permissionId FROM `xiaozhi`.`sys_permission` WHERE `permissionKey` = 'system:config:mcp-server';

SET FOREIGN_KEY_CHECKS = 1;
