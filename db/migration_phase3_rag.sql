-- ============================================================
-- Phase 3 RAG 知识库增量迁移脚本
-- 适用于已有数据库（从 Phase 2 升级到 Phase 3）
-- 幂等设计：可安全重复执行
-- ============================================================

USE `xiaozhi`;

-- ------------------------------------------------------------
-- 1. 知识库表：sys_knowledge_base
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_knowledge_base` (
  `knowledgeBaseId` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库ID，主键',
  `knowledgeBaseName` varchar(200) NOT NULL COMMENT '知识库名称',
  `description` varchar(500) DEFAULT NULL COMMENT '知识库描述',
  `userId` int NOT NULL COMMENT '所属用户ID',
  `embeddingConfigId` int DEFAULT NULL COMMENT '向量模型配置ID（关联 sys_config）',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-有效，0-删除',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`knowledgeBaseId`),
  KEY `idx_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- ------------------------------------------------------------
-- 2. 知识库文档表：sys_knowledge_doc（通过 knowledgeBaseId 关联知识库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_knowledge_doc` (
  `docId` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID，主键',
  `knowledgeBaseId` bigint NOT NULL COMMENT '所属知识库ID',
  `userId` int NOT NULL COMMENT '所属用户ID',
  `docName` varchar(255) NOT NULL COMMENT '文档名称（原始文件名）',
  `docType` varchar(20) NOT NULL COMMENT '文档类型：txt, pdf, md, docx',
  `filePath` varchar(500) DEFAULT NULL COMMENT '原始文件存储路径',
  `fileSize` bigint DEFAULT 0 COMMENT '文件大小（字节）',
  `chunkCount` int DEFAULT 0 COMMENT '分块数量',
  `charCount` int DEFAULT 0 COMMENT '总字符数',
  `status` varchar(20) NOT NULL DEFAULT 'uploading' COMMENT '处理状态：uploading-上传中, processing-处理中, ready-就绪, failed-失败',
  `errorMsg` varchar(500) DEFAULT NULL COMMENT '处理失败时的错误信息',
  `state` enum('1','0') DEFAULT '1' COMMENT '状态：1-有效，0-删除',
  `createTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`docId`),
  KEY `idx_knowledgeBaseId` (`knowledgeBaseId`),
  KEY `idx_userId` (`userId`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- ------------------------------------------------------------
-- 3. sys_role 表增加 knowledgeBaseIds 和 RAG 检索参数字段
-- ------------------------------------------------------------
-- 幂等：使用 IF NOT EXISTS 方式检查列是否已存在（MySQL 存储过程）
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS `add_role_knowledge_columns`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'xiaozhi' AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'knowledgeBaseIds'
    ) THEN
        ALTER TABLE `sys_role` ADD COLUMN `knowledgeBaseIds` varchar(500) DEFAULT NULL COMMENT '关联的知识库ID列表，逗号分隔';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'xiaozhi' AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'ragTopK'
    ) THEN
        ALTER TABLE `sys_role` ADD COLUMN `ragTopK` int DEFAULT NULL COMMENT 'RAG 检索返回条数（覆盖全局配置）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'xiaozhi' AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'ragThreshold'
    ) THEN
        ALTER TABLE `sys_role` ADD COLUMN `ragThreshold` double DEFAULT NULL COMMENT 'RAG 相似度阈值（覆盖全局配置）';
    END IF;
END//
DELIMITER ;

CALL `add_role_knowledge_columns`();
DROP PROCEDURE IF EXISTS `add_role_knowledge_columns`;

-- ------------------------------------------------------------
-- 4. 知识库管理菜单权限（幂等插入）
-- ------------------------------------------------------------
INSERT IGNORE INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(0, '知识库', 'system:knowledge', 'menu', '/knowledge', 'page/knowledge/KnowledgeView', 'ReadOutlined', 6, '1', '1');

-- 管理员角色赋予知识库管理权限
INSERT IGNORE INTO `sys_role_permission` (`roleId`, `permissionId`)
SELECT 1, permissionId FROM `sys_permission` WHERE `permissionKey` = 'system:knowledge'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    JOIN `sys_permission` p ON rp.permissionId = p.permissionId
    WHERE rp.roleId = 1 AND p.permissionKey = 'system:knowledge'
);

-- 普通用户角色赋予知识库管理权限
INSERT IGNORE INTO `sys_role_permission` (`roleId`, `permissionId`)
SELECT 2, permissionId FROM `sys_permission` WHERE `permissionKey` = 'system:knowledge'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    JOIN `sys_permission` p ON rp.permissionId = p.permissionId
    WHERE rp.roleId = 2 AND p.permissionKey = 'system:knowledge'
);
