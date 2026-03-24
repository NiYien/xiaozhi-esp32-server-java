-- 清理旧维度声纹数据
-- 说话人嵌入模型从 ONNX Runtime（192维）升级到 sherpa-onnx（256维）后，
-- 旧的 192 维声纹数据（768 字节）无法与新的 256 维嵌入向量（1024 字节）匹配。
-- 执行此脚本删除旧维度的声纹数据，用户需要重新注册声纹。

-- 查看受影响的声纹数量（先预览再删除）
SELECT COUNT(*) AS old_dimension_count
FROM sys_voiceprint
WHERE LENGTH(embedding) != 1024;

-- 删除非 256 维（1024 字节）的声纹数据
DELETE FROM sys_voiceprint
WHERE LENGTH(embedding) != 1024;
