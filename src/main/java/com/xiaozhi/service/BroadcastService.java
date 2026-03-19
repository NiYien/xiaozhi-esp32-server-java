package com.xiaozhi.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.service.*;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.utils.AudioUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 广播服务
 * 负责将文本或音频广播到指定设备组的所有在线设备
 * 核心设计：TTS合成一次，PCM数据共享到多个Player
 */
@Service
public class BroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);

    @Resource
    private SessionManager sessionManager;

    @Resource
    private SysDeviceGroupService deviceGroupService;

    @Resource
    private TtsServiceFactory ttsFactory;

    @Resource
    private SysConfigService configService;

    @Resource
    private MessageService messageService;

    /**
     * 广播结果
     */
    public record BroadcastResult(int totalDevices, int onlineDevices, int offlineDevices, String message) {}

    /**
     * 向设备组广播文本消息
     * TTS合成一次，PCM数据共享到多个在线设备的Player
     *
     * @param groupId 分组ID
     * @param text    要广播的文本
     * @param sourceSession 发起广播的会话（用于获取TTS配置）
     * @return 广播结果
     */
    public BroadcastResult broadcastMessage(Integer groupId, String text, ChatSession sourceSession) {
        // 获取分组内所有设备ID
        List<String> deviceIds = deviceGroupService.getDeviceIds(groupId);
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new BroadcastResult(0, 0, 0, "分组内没有设备");
        }

        // 收集在线的目标设备会话（排除发起者自身）
        List<ChatSession> onlineSessions = new ArrayList<>();
        int offlineCount = 0;
        String sourceDeviceId = sourceSession != null && sourceSession.getSysDevice() != null
                ? sourceSession.getSysDevice().getDeviceId() : null;

        for (String deviceId : deviceIds) {
            // 跳过发起广播的设备自身
            if (deviceId.equals(sourceDeviceId)) {
                continue;
            }
            ChatSession targetSession = sessionManager.getSessionByDeviceId(deviceId);
            if (targetSession != null && targetSession.isOpen()) {
                onlineSessions.add(targetSession);
            } else {
                offlineCount++;
            }
        }

        if (onlineSessions.isEmpty()) {
            return new BroadcastResult(deviceIds.size(), 0, offlineCount, "没有在线设备可以接收广播");
        }

        // TTS合成一次
        TtsService ttsService = getTtsService(sourceSession);
        if (ttsService == null) {
            return new BroadcastResult(deviceIds.size(), onlineSessions.size(), offlineCount, "无法获取TTS服务");
        }

        try {
            String audioPath = ttsService.textToSpeech(text);
            if (audioPath == null) {
                return new BroadcastResult(deviceIds.size(), onlineSessions.size(), offlineCount, "TTS合成失败");
            }

            // 读取PCM数据
            byte[] pcmData = AudioUtils.readAsPcm(audioPath);

            // 扇出广播：将PCM数据推送到每个在线设备的Player
            int successCount = 0;
            for (ChatSession targetSession : onlineSessions) {
                try {
                    playOnSession(targetSession, pcmData, text);
                    successCount++;
                } catch (Exception e) {
                    logger.error("向设备广播失败 - DeviceId: {}, 错误: {}",
                            targetSession.getSysDevice() != null ? targetSession.getSysDevice().getDeviceId() : "unknown",
                            e.getMessage());
                }
            }

            String msg = String.format("已向 %d 个设备广播", successCount);
            if (offlineCount > 0) {
                msg += String.format("（%d 个离线）", offlineCount);
            }
            return new BroadcastResult(deviceIds.size(), successCount, offlineCount, msg);

        } catch (Exception e) {
            logger.error("广播消息失败", e);
            return new BroadcastResult(deviceIds.size(), onlineSessions.size(), offlineCount, "广播失败: " + e.getMessage());
        }
    }

    /**
     * 向设备组广播音频文件
     *
     * @param groupId   分组ID
     * @param audioPath 音频文件路径
     * @param sourceSession 发起广播的会话
     * @return 广播结果
     */
    public BroadcastResult broadcastAudio(Integer groupId, String audioPath, ChatSession sourceSession) {
        // 获取分组内所有设备ID
        List<String> deviceIds = deviceGroupService.getDeviceIds(groupId);
        if (deviceIds == null || deviceIds.isEmpty()) {
            return new BroadcastResult(0, 0, 0, "分组内没有设备");
        }

        // 收集在线的目标设备会话
        List<ChatSession> onlineSessions = new ArrayList<>();
        int offlineCount = 0;
        String sourceDeviceId = sourceSession != null && sourceSession.getSysDevice() != null
                ? sourceSession.getSysDevice().getDeviceId() : null;

        for (String deviceId : deviceIds) {
            if (deviceId.equals(sourceDeviceId)) {
                continue;
            }
            ChatSession targetSession = sessionManager.getSessionByDeviceId(deviceId);
            if (targetSession != null && targetSession.isOpen()) {
                onlineSessions.add(targetSession);
            } else {
                offlineCount++;
            }
        }

        if (onlineSessions.isEmpty()) {
            return new BroadcastResult(deviceIds.size(), 0, offlineCount, "没有在线设备可以接收广播");
        }

        try {
            // 读取PCM数据
            byte[] pcmData = AudioUtils.readAsPcm(audioPath);

            // 扇出广播
            int successCount = 0;
            for (ChatSession targetSession : onlineSessions) {
                try {
                    playOnSession(targetSession, pcmData, "");
                    successCount++;
                } catch (Exception e) {
                    logger.error("向设备广播音频失败 - DeviceId: {}",
                            targetSession.getSysDevice() != null ? targetSession.getSysDevice().getDeviceId() : "unknown", e);
                }
            }

            String msg = String.format("已向 %d 个设备广播", successCount);
            if (offlineCount > 0) {
                msg += String.format("（%d 个离线）", offlineCount);
            }
            return new BroadcastResult(deviceIds.size(), successCount, offlineCount, msg);

        } catch (Exception e) {
            logger.error("广播音频失败", e);
            return new BroadcastResult(deviceIds.size(), onlineSessions.size(), offlineCount, "广播失败: " + e.getMessage());
        }
    }

    /**
     * 在目标会话上播放PCM音频数据
     * 使用目标设备的Player进行播放
     */
    private void playOnSession(ChatSession targetSession, byte[] pcmData, String text) {
        Player player = targetSession.getPlayer();
        if (player == null) {
            logger.warn("目标设备没有初始化播放器 - DeviceId: {}",
                    targetSession.getSysDevice() != null ? targetSession.getSysDevice().getDeviceId() : "unknown");
            // 尝试创建播放器
            player = new ScheduledPlayer(targetSession, messageService, null, null);
            targetSession.setPlayer(player);
        }
        // 将PCM数据包装为Speech并推送到Player
        Speech speech = new Speech(pcmData, text);
        player.play(Flux.just(speech));
    }

    /**
     * 获取发起者会话的TTS服务
     */
    private TtsService getTtsService(ChatSession sourceSession) {
        if (sourceSession == null || sourceSession.getSysDevice() == null) {
            // 使用默认TTS
            return ttsFactory.getTtsService(null, null, null, null);
        }

        SysRole role = null;
        if (sourceSession.getPersona() != null && sourceSession.getPersona().getConversation() != null) {
            role = sourceSession.getPersona().getConversation().role();
        }

        if (role == null) {
            return ttsFactory.getTtsService(null, null, null, null);
        }

        SysConfig ttsConfig = null;
        if (role.getTtsId() != null) {
            ttsConfig = configService.selectConfigById(role.getTtsId());
        }
        return ttsFactory.getTtsService(ttsConfig, role.getVoiceName(), role.getTtsPitch(), role.getTtsSpeed());
    }
}
