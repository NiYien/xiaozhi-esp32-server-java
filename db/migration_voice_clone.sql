-- 音色克隆表
CREATE TABLE IF NOT EXISTS sys_voice_clone (
    cloneId INT AUTO_INCREMENT PRIMARY KEY COMMENT '克隆音色ID',
    userId INT NOT NULL COMMENT '用户ID',
    cloneName VARCHAR(100) NOT NULL COMMENT '用户自定义名称',
    provider VARCHAR(50) NOT NULL COMMENT '服务提供商(volcengine/aliyun-nls)',
    configId INT NOT NULL COMMENT 'TTS配置ID(FK to sys_config)',
    taskId VARCHAR(200) COMMENT '云API训练任务ID',
    voiceId VARCHAR(200) COMMENT '训练完成后的音色ID',
    samplePath VARCHAR(500) COMMENT '上传的音频样本路径',
    status ENUM('uploading','training','ready','failed') DEFAULT 'uploading' COMMENT '训练状态',
    errorMessage VARCHAR(500) COMMENT '失败错误信息',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user (userId),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音色克隆表';
