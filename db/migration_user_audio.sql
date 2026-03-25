-- 用户音频保存功能：sys_message 表新增 audio_group 字段
-- 用于标识被 VAD 切断的连续语音段，同组共享同一 UUID
ALTER TABLE `xiaozhi`.`sys_message`
ADD COLUMN `audio_group` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '音频分组标识，同组表示被VAD切断的连续语音'
AFTER `toolCalls`;
