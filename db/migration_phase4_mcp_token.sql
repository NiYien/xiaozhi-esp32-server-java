-- MCP Token 表：用于 MCP 接入点 WebSocket 认证
CREATE TABLE IF NOT EXISTS `sys_mcp_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `userId` INT NOT NULL COMMENT '用户ID',
  `token` VARCHAR(68) NOT NULL COMMENT 'MCP专用Token（mcp_+64位hex）',
  `tokenName` VARCHAR(100) DEFAULT '' COMMENT 'Token名称（用户自定义）',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：1-启用 0-禁用',
  `lastUsedAt` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token` (`token`),
  INDEX `idx_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP接入点Token';
