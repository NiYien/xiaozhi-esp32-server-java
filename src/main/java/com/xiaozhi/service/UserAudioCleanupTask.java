package com.xiaozhi.service;

import com.xiaozhi.dao.MessageMapper;
import com.xiaozhi.entity.SysMessage;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * 用户音频自动清理定时任务
 * 按配置的保留天数清理过期的用户音频文件并更新数据库记录
 * 默认关闭，需在 application.yml 中设置 audio.cleanup.enabled=true 开启
 *
 * 注意：此任务与 com.xiaozhi.task.AudioCleanupTask 职责不同：
 * - AudioCleanupTask：按日期目录清理物理音频文件（所有类型）
 * - UserAudioCleanupTask：按数据库记录清理用户(user)音频文件并更新数据库
 */
@Component
@ConditionalOnProperty(name = "audio.cleanup.enabled", havingValue = "true")
public class UserAudioCleanupTask {
    private static final Logger logger = LoggerFactory.getLogger(UserAudioCleanupTask.class);

    @Value("${audio.cleanup.retention-days:30}")
    private int retentionDays;

    @Resource
    private MessageMapper messageMapper;

    /**
     * 定时清理过期用户音频
     * cron 表达式从配置文件读取，默认每天凌晨3点执行
     */
    @Scheduled(cron = "${audio.cleanup.cron:0 0 3 * * ?}")
    public void cleanupExpiredAudios() {
        logger.info("开始清理过期用户音频，保留天数: {}", retentionDays);

        try {
            // 计算过期时间
            Instant expireInstant = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            Date expireTime = Date.from(expireInstant);

            // 查询过期的用户音频消息
            List<SysMessage> expiredMessages = messageMapper.queryExpiredUserAudios(expireTime);

            if (expiredMessages.isEmpty()) {
                logger.info("没有需要清理的过期用户音频");
                return;
            }

            int deletedFiles = 0;
            long freedBytes = 0;

            for (SysMessage msg : expiredMessages) {
                String audioPath = msg.getAudioPath();
                if (audioPath != null && !audioPath.isBlank()) {
                    try {
                        Path filePath = Path.of(audioPath);
                        if (Files.exists(filePath)) {
                            freedBytes += Files.size(filePath);
                            Files.deleteIfExists(filePath);
                            deletedFiles++;
                        }
                    } catch (Exception e) {
                        logger.warn("删除音频文件失败: {}", audioPath, e);
                    }
                }
            }

            // 批量清空数据库中的音频路径
            List<Integer> messageIds = expiredMessages.stream()
                    .map(SysMessage::getMessageId)
                    .toList();
            messageMapper.clearAudioPaths(messageIds);

            logger.info("用户音频清理完成：删除文件 {} 个，释放空间 {} KB，更新记录 {} 条",
                    deletedFiles, freedBytes / 1024, messageIds.size());
        } catch (Exception e) {
            logger.error("用户音频清理任务执行失败", e);
        }
    }
}
