-- ============================================================
-- Phase 3 监控面板迁移脚本
-- 适用于已有数据库（从 Phase 2 升级到 Phase 3）
-- 幂等设计：可安全重复执行
-- ============================================================

USE `xiaozhi`;

-- ------------------------------------------------------------
-- 1. 每日用量统计预聚合表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_usage_daily` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `statDate` DATE NOT NULL COMMENT '统计日期',
    `userId` INT DEFAULT NULL COMMENT '用户ID',
    `deviceId` VARCHAR(64) DEFAULT NULL COMMENT '设备ID',
    `roleId` INT DEFAULT NULL COMMENT '角色ID',
    `provider` VARCHAR(50) DEFAULT NULL COMMENT '服务提供商（如 openai、aliyun、edge-tts）',
    `serviceType` ENUM('llm','stt','tts') NOT NULL COMMENT '服务类型',
    `requestCount` INT DEFAULT 0 COMMENT '调用次数',
    `promptTokens` BIGINT DEFAULT 0 COMMENT 'LLM prompt token 数',
    `completionTokens` BIGINT DEFAULT 0 COMMENT 'LLM completion token 数',
    `totalTokens` BIGINT DEFAULT 0 COMMENT 'LLM 总 token 数',
    `avgLatencyMs` INT DEFAULT 0 COMMENT '平均延迟（毫秒）',
    `errorCount` INT DEFAULT 0 COMMENT '错误次数',
    `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_daily_stat` (`statDate`, `userId`, `deviceId`, `roleId`, `provider`, `serviceType`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日用量统计预聚合表';

-- ------------------------------------------------------------
-- 2. 监控面板菜单权限（幂等插入）
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(0, '监控面板', 'system:monitor', 'menu', '/monitor', 'page/MonitorView', 'MonitorOutlined', 2, '1', '1');

-- 管理员角色赋予监控面板权限（仅在权限存在且未关联时插入）
INSERT IGNORE INTO `sys_role_permission` (`roleId`, `permissionId`)
SELECT 1, permissionId FROM `sys_permission` WHERE `permissionKey` = 'system:monitor'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    JOIN `sys_permission` p ON rp.permissionId = p.permissionId
    WHERE rp.roleId = 1 AND p.permissionKey = 'system:monitor'
);
