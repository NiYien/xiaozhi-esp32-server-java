-- ============================================================
-- Phase 2 增量迁移脚本
-- 适用于已有数据库（从 Phase 1 升级到 Phase 2）
-- 幂等设计：可安全重复执行
-- ============================================================

USE `xiaozhi`;

-- ------------------------------------------------------------
-- 1. 安抚词：sys_role 添加 comfort_words 列
--    MySQL 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS，
--    使用存储过程实现条件检查
-- ------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `add_comfort_words_column`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'xiaozhi'
          AND TABLE_NAME = 'sys_role'
          AND COLUMN_NAME = 'comfort_words'
    ) THEN
        ALTER TABLE `sys_role` ADD COLUMN `comfort_words` JSON DEFAULT NULL
            COMMENT '安抚词列表，JSON数组格式，工具调用前随机播放一条安抚用户'
            AFTER `vadSilenceMs`;
    END IF;
END //
DELIMITER ;

CALL `add_comfort_words_column`();
DROP PROCEDURE IF EXISTS `add_comfort_words_column`;

-- ------------------------------------------------------------
-- 2. 长期记忆：创建 sys_user_memory 表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user_memory` (
  `memoryId` bigint NOT NULL AUTO_INCREMENT COMMENT '记忆ID，主键',
  `userId` int NOT NULL COMMENT '用户ID，记忆按用户存储，跨设备跨角色共享',
  `category` varchar(20) NOT NULL DEFAULT 'other' COMMENT '记忆分类：preference-偏好，fact-事实，habit-习惯，relationship-关系，other-其他',
  `content` varchar(500) NOT NULL COMMENT '记忆内容',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-有效，0-删除',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`memoryId`),
  KEY `idx_userId` (`userId`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户长期记忆表';

-- ------------------------------------------------------------
-- 3. 固件管理：创建 sys_firmware 表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_firmware` (
  `firmwareId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '固件ID，主键',
  `firmwareName` varchar(100) NOT NULL COMMENT '固件名称',
  `version` varchar(50) NOT NULL COMMENT '版本号',
  `chipModelName` varchar(100) DEFAULT NULL COMMENT '适用芯片型号，NULL表示通用',
  `deviceType` varchar(50) DEFAULT NULL COMMENT '适用设备类型，NULL表示通用',
  `url` varchar(500) NOT NULL COMMENT '固件下载地址',
  `fileSize` bigint DEFAULT NULL COMMENT '固件文件大小（字节）',
  `fileHash` varchar(64) DEFAULT NULL COMMENT 'SHA-256哈希值',
  `description` text COMMENT '版本说明',
  `isDefault` enum('1','0') DEFAULT '0' COMMENT '是否为默认固件：1-是，0-否',
  `userId` int DEFAULT NULL COMMENT '上传用户',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`firmwareId`),
  KEY `chipModelName` (`chipModelName`),
  KEY `deviceType` (`deviceType`),
  KEY `isDefault` (`isDefault`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='固件信息表';

-- ------------------------------------------------------------
-- 4. 固件管理菜单权限（幂等插入）
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(7, '固件管理', 'system:config:firmware', 'menu', '/config/firmware', 'page/config/FirmwareConfig', NULL, 5, '1', '1');

-- 管理员角色赋予固件管理权限（仅在权限存在且未关联时插入）
INSERT IGNORE INTO `sys_role_permission` (`roleId`, `permissionId`)
SELECT 1, permissionId FROM `sys_permission` WHERE `permissionKey` = 'system:config:firmware'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    JOIN `sys_permission` p ON rp.permissionId = p.permissionId
    WHERE rp.roleId = 1 AND p.permissionKey = 'system:config:firmware'
);
