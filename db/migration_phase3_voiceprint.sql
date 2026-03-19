-- ============================================================
-- Phase3: 声纹识别 - 数据库迁移脚本
-- ============================================================

-- 声纹表：存储用户的声纹嵌入向量
-- 注意：voiceprintId 使用 BIGINT/Long 类型（与设计文档的差异说明：BIGINT 更适合自增主键，避免溢出）
CREATE TABLE IF NOT EXISTS `xiaozhi`.`sys_voiceprint` (
  `voiceprintId` bigint NOT NULL AUTO_INCREMENT COMMENT '声纹ID，主键',
  `userId` int NOT NULL COMMENT '用户ID，关联sys_user表',
  `deviceId` varchar(255) NOT NULL COMMENT '采集声纹时使用的设备ID',
  `voiceprintName` varchar(100) NOT NULL COMMENT '声纹名称，用于标识说话人',
  `embedding` blob NOT NULL COMMENT '声纹嵌入向量，192维float32，768字节',
  `sampleCount` int DEFAULT 1 COMMENT '采样次数，多次录制通过加权平均提高精度',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-有效，0-无效',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`voiceprintId`),
  INDEX `idx_userId` (`userId`),
  INDEX `idx_deviceId` (`deviceId`),
  INDEX `idx_userId_state` (`userId`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户声纹表';

-- 已有表的增量迁移（如果表已存在但缺少新字段）
-- ALTER TABLE `xiaozhi`.`sys_voiceprint` ADD COLUMN `voiceprintName` varchar(100) NOT NULL DEFAULT '' COMMENT '声纹名称，用于标识说话人' AFTER `deviceId`;
-- ALTER TABLE `xiaozhi`.`sys_voiceprint` ADD COLUMN `sampleCount` int DEFAULT 1 COMMENT '采样次数，多次录制通过加权平均提高精度' AFTER `embedding`;
