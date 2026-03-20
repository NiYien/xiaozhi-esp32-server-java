-- Phase 4: 对话标题自动生成
-- 在 sys_message 表中添加 session_title 字段，用于存储 LLM 生成的会话标题

ALTER TABLE `xiaozhi`.`sys_message` ADD COLUMN `session_title` VARCHAR(100) DEFAULT NULL COMMENT '会话标题（LLM生成）' AFTER `sessionId`;
