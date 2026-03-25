-- MQTT 扩展功能数据库迁移脚本
-- 新增传感器数据表和设备分组表

-- 传感器数据表
CREATE TABLE IF NOT EXISTS `xiaozhi`.`sys_sensor_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deviceId` varchar(255) NOT NULL COMMENT '设备ID',
  `temperature` float DEFAULT NULL COMMENT '温度（摄氏度）',
  `battery` int DEFAULT NULL COMMENT '电量百分比（0-100）',
  `freeHeap` int DEFAULT NULL COMMENT '剩余堆内存（字节）',
  `wifiRssi` int DEFAULT NULL COMMENT 'WiFi信号强度（dBm）',
  `uptime` bigint DEFAULT NULL COMMENT '运行时长（秒）',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
  PRIMARY KEY (`id`),
  KEY `idx_deviceId` (`deviceId`),
  KEY `idx_createTime` (`createTime`),
  KEY `idx_device_time` (`deviceId`, `createTime` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备传感器数据表';

-- 设备分组表
CREATE TABLE IF NOT EXISTS `xiaozhi`.`sys_device_group` (
  `groupId` int unsigned NOT NULL AUTO_INCREMENT COMMENT '分组ID',
  `groupName` varchar(50) NOT NULL COMMENT '分组名称',
  `description` varchar(200) DEFAULT NULL COMMENT '分组描述',
  `userId` int NOT NULL COMMENT '所属用户ID',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`groupId`),
  KEY `idx_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组表';

-- 设备分组成员表
CREATE TABLE IF NOT EXISTS `xiaozhi`.`sys_device_group_member` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `groupId` int unsigned NOT NULL COMMENT '分组ID',
  `deviceId` varchar(255) NOT NULL COMMENT '设备ID',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_device` (`groupId`, `deviceId`),
  KEY `idx_deviceId` (`deviceId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组成员表';
