-- ============================================================
-- Phase 3 提醒闹钟 增量迁移脚本
-- 适用于已有数据库（从 Phase 2 升级）
-- 幂等设计：可安全重复执行
-- ============================================================

USE `xiaozhi`;

-- ------------------------------------------------------------
-- 1. 提醒闹钟：创建 sys_reminder 表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_reminder` (
  `reminderId` int NOT NULL AUTO_INCREMENT COMMENT '提醒ID，主键',
  `userId` int NOT NULL COMMENT '用户ID',
  `deviceId` varchar(64) NOT NULL COMMENT '设备ID',
  `message` varchar(500) NOT NULL COMMENT '提醒内容',
  `triggerTime` datetime NOT NULL COMMENT '下次触发时间',
  `repeatType` enum('once','daily','weekly') DEFAULT 'once' COMMENT '重复类型：once-一次性，daily-每天，weekly-每周',
  `repeatDays` varchar(20) DEFAULT NULL COMMENT '周重复的星期几列表，如 "1,3,5" 表示周一三五',
  `status` enum('active','triggered','cancelled') DEFAULT 'active' COMMENT '状态：active-活跃，triggered-已触发，cancelled-已取消',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`reminderId`),
  INDEX `idx_status_trigger` (`status`, `triggerTime`),
  INDEX `idx_user_device` (`userId`, `deviceId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提醒闹钟表';
