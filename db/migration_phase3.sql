-- ============================================================
-- Phase 3 增量迁移脚本 - 多设备协同（设备分组与广播）
-- 适用于已有数据库（从 Phase 2 升级到 Phase 3）
-- 幂等设计：可安全重复执行
-- ============================================================

USE `xiaozhi`;

-- ------------------------------------------------------------
-- 1. 设备分组表：sys_device_group
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_device_group` (
  `groupId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '分组ID，主键',
  `groupName` varchar(100) NOT NULL COMMENT '分组名称',
  `description` varchar(500) DEFAULT NULL COMMENT '分组描述',
  `userId` int NOT NULL COMMENT '所属用户ID',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`groupId`),
  KEY `idx_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组表';

-- ------------------------------------------------------------
-- 2. 设备分组成员表：sys_device_group_member（多对多关系）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_device_group_member` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `groupId` int unsigned NOT NULL COMMENT '分组ID',
  `deviceId` varchar(64) NOT NULL COMMENT '设备ID',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_device` (`groupId`, `deviceId`),
  KEY `idx_deviceId` (`deviceId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组成员表';
