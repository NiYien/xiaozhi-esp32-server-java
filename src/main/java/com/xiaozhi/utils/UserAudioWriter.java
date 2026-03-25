package com.xiaozhi.utils;

import org.gagravarr.opus.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 用户音频写入工具类
 * 将 Opus 帧列表写入 OGG Opus 文件（16kHz），用于保存用户语音
 */
public class UserAudioWriter {
    private static final Logger logger = LoggerFactory.getLogger(UserAudioWriter.class);

    /**
     * 每帧对应的 granule position 增量
     * OGG Opus 规范要求以 48kHz 计数：48000Hz * 60ms = 2880 samples per frame
     * 但用户音频是 16kHz，Opus 内部仍以 48kHz 计算 granule position
     */
    private static final long GRANULE_INCREMENT = 2880;

    /**
     * 最短语音时长阈值（秒），低于此值不保存
     */
    private static final double MIN_DURATION_SECONDS = 0.5;

    /**
     * 将 Opus 帧写入 OGG Opus 文件
     *
     * @param opusFrames Opus 帧数据列表
     * @param filePath   保存文件路径
     * @return 是否成功写入
     */
    public static boolean writeOpusFile(List<byte[]> opusFrames, Path filePath) {
        if (opusFrames == null || opusFrames.isEmpty()) {
            return false;
        }

        // 检查最短时长：每帧 60ms
        double durationSeconds = opusFrames.size() * AudioUtils.OPUS_FRAME_DURATION_MS / 1000.0;
        if (durationSeconds < MIN_DURATION_SECONDS) {
            logger.debug("用户语音时长 {}s 低于阈值 {}s，不保存", String.format("%.1f", durationSeconds), MIN_DURATION_SECONDS);
            return false;
        }

        try {
            // 确保目录存在
            Files.createDirectories(filePath.getParent());

            // 创建 OpusInfo 对象，设置基本参数（用户音频为 16kHz）
            OpusInfo oi = new OpusInfo();
            oi.setSampleRate(AudioUtils.STT_SAMPLE_RATE);
            oi.setNumChannels(AudioUtils.CHANNELS);
            oi.setPreSkip(0);

            // 创建 OpusTags 对象
            OpusTags ot = new OpusTags();
            ot.addComment("TITLE", "User Audio");
            ot.addComment("ARTIST", "Xiaozhi ESP32 Server");

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile());
                 OpusFile opusFile = new OpusFile(fos, oi, ot)) {

                long granulePosition = 0;
                for (byte[] frame : opusFrames) {
                    OpusAudioData audioData = new OpusAudioData(frame);
                    granulePosition += GRANULE_INCREMENT;
                    audioData.setGranulePosition(granulePosition);
                    opusFile.writeAudioData(audioData);
                }
            }

            logger.debug("用户音频已保存: {}, 帧数: {}, 时长: {}s", filePath, opusFrames.size(), String.format("%.1f", durationSeconds));
            return true;

        } catch (IOException e) {
            logger.error("写入用户音频文件失败: {}", filePath, e);
            return false;
        }
    }

    /**
     * 生成用户音频文件路径
     * 格式：audio/{date}/{deviceId}/{roleId}/{timestamp}-user.opus
     *
     * @param deviceId 设备ID
     * @param roleId   角色ID
     * @param instant  时间戳
     * @return 文件路径
     */
    public static Path generatePath(String deviceId, Integer roleId, Instant instant) {
        instant = instant.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        String date = localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String datetime = localDateTime.format(DateTimeFormatter.ISO_DATE_TIME).replace(":", "");
        // 设备ID可能是MAC地址，需要替换冒号
        String safeDeviceId = deviceId.replace(":", "-");
        String filename = "%s-user.opus".formatted(datetime);
        return Path.of(AudioUtils.AUDIO_PATH, date, safeDeviceId, roleId.toString(), filename);
    }
}
