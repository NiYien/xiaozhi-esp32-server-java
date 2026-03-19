-- 安抚词功能升级脚本
-- 在 sys_role 表中添加 comfort_words 字段

ALTER TABLE `xiaozhi`.`sys_role`
ADD COLUMN `comfort_words` JSON DEFAULT NULL COMMENT '安抚词列表，JSON数组格式，工具调用前随机播放一条安抚用户'
AFTER `memoryType`;
