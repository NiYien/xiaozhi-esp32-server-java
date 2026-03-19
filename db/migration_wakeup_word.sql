-- ============================================================
-- Phase 3 唤醒词-角色映射 增量迁移脚本
-- 适用于已有数据库（从 Phase 2 升级到 Phase 3）
-- 幂等设计：可安全重复执行
-- ============================================================

USE `xiaozhi`;

-- ------------------------------------------------------------
-- 1. 唤醒词-角色映射表：sys_device_wakeup
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_device_wakeup` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deviceId` varchar(64) NOT NULL COMMENT '设备唯一标识',
  `wakeupWord` varchar(100) NOT NULL COMMENT '唤醒词文本',
  `roleId` int NOT NULL COMMENT '关联角色ID',
  `priority` int DEFAULT 1 COMMENT '优先级，0为默认角色',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_wakeup` (`deviceId`, `wakeupWord`),
  INDEX `idx_device` (`deviceId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备唤醒词-角色映射表';
