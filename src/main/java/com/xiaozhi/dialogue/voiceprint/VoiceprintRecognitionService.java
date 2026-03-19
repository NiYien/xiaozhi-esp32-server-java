package com.xiaozhi.dialogue.voiceprint;

import com.xiaozhi.entity.SysVoiceprint;
import com.xiaozhi.event.ChatSessionCloseEvent;
import com.xiaozhi.event.DeviceOnlineEvent;
import com.xiaozhi.service.SysVoiceprintService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 声纹识别服务
 * 负责声纹比对和识别，维护设备关联用户声纹的内存缓存。
 * 设备连接时加载声纹缓存，设备断开时清理缓存。
 */
@Service
public class VoiceprintRecognitionService {
    private static final Logger logger = LoggerFactory.getLogger(VoiceprintRecognitionService.class);

    /**
     * 余弦相似度阈值，高于此值认为是同一说话人
     */
    @Value("${voiceprint.similarity.threshold:0.75}")
    private float similarityThreshold;

    @Resource
    private SpeakerEmbeddingService speakerEmbeddingService;

    @Resource
    private SysVoiceprintService voiceprintService;

    /**
     * 设备ID -> 该设备关联用户的声纹缓存列表
     * 设备上线时加载，断开时清理
     */
    private final ConcurrentHashMap<String, List<CachedVoiceprint>> deviceVoiceprintCache = new ConcurrentHashMap<>();

    /**
     * 缓存的声纹数据（预解析embedding，避免每次比对都反序列化）
     * 使用 deviceId 做缓存 key 的原因：设备连接时按设备加载更高效，
     * 避免遍历所有用户的声纹数据，且设备连接/断开事件天然对应缓存的加载/清理生命周期。
     */
    public record CachedVoiceprint(Long voiceprintId, Integer userId, String voiceprintName, float[] embedding) {}

    /**
     * 声纹识别结果
     */
    public record RecognitionResult(Integer userId, Long voiceprintId, String voiceprintName, float similarity) {}

    /**
     * 声纹识别功能是否可用
     */
    public boolean isAvailable() {
        return speakerEmbeddingService.isEnabled();
    }

    /**
     * 从PCM音频提取嵌入向量
     *
     * @param pcmData 16kHz 16bit单声道PCM数据
     * @return 192维float32嵌入向量，音频过短或提取失败返回null
     */
    public float[] extractEmbedding(byte[] pcmData) {
        return speakerEmbeddingService.extractEmbedding(pcmData);
    }

    /**
     * 识别说话人
     * 将输入的嵌入向量与设备缓存中的声纹进行比对，返回最佳匹配结果。
     *
     * @param deviceId  设备ID
     * @param embedding 待识别的嵌入向量
     * @return 识别结果，未匹配返回null
     */
    public RecognitionResult recognize(String deviceId, float[] embedding) {
        if (!isAvailable() || embedding == null) {
            return null;
        }

        List<CachedVoiceprint> cachedList = deviceVoiceprintCache.get(deviceId);
        if (cachedList == null || cachedList.isEmpty()) {
            return null;
        }

        float bestSimilarity = -1.0f;
        CachedVoiceprint bestMatch = null;

        for (CachedVoiceprint cached : cachedList) {
            float similarity = SpeakerEmbeddingService.cosineSimilarity(embedding, cached.embedding());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = cached;
            }
        }

        if (bestMatch != null && bestSimilarity >= similarityThreshold) {
            // S2: SLF4J 不支持 {:.4f} 格式，使用 String.format 手动格式化
            logger.debug("声纹识别成功: deviceId={}, userId={}, name={}, 相似度={}, 阈值={}",
                    deviceId, bestMatch.userId(), bestMatch.voiceprintName(),
                    String.format("%.4f", bestSimilarity), similarityThreshold);
            return new RecognitionResult(bestMatch.userId(), bestMatch.voiceprintId(), bestMatch.voiceprintName(), bestSimilarity);
        }

        // S2: SLF4J 不支持 {:.4f} 格式，使用 String.format 手动格式化
        logger.debug("声纹识别未匹配: deviceId={}, 最高相似度={}, 阈值={}",
                deviceId, String.format("%.4f", bestSimilarity), similarityThreshold);
        return null;
    }

    /**
     * 加载设备关联用户的声纹到内存缓存
     */
    public void loadDeviceVoiceprints(String deviceId) {
        if (!isAvailable()) {
            return;
        }

        try {
            List<SysVoiceprint> voiceprints = voiceprintService.listByDeviceId(deviceId);
            if (voiceprints == null || voiceprints.isEmpty()) {
                deviceVoiceprintCache.remove(deviceId);
                logger.debug("设备 {} 无关联声纹数据", deviceId);
                return;
            }

            List<CachedVoiceprint> cachedList = voiceprints.stream()
                    .map(vp -> {
                        float[] emb = SpeakerEmbeddingService.bytesToEmbedding(vp.getEmbedding());
                        return emb != null ? new CachedVoiceprint(vp.getVoiceprintId(), vp.getUserId(), vp.getVoiceprintName(), emb) : null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();

            deviceVoiceprintCache.put(deviceId, cachedList);
            logger.info("设备 {} 声纹缓存已加载，共 {} 条", deviceId, cachedList.size());
        } catch (Exception e) {
            logger.error("加载设备 {} 声纹缓存失败: {}", deviceId, e.getMessage());
        }
    }

    /**
     * 清理设备的声纹缓存
     */
    public void clearDeviceVoiceprints(String deviceId) {
        deviceVoiceprintCache.remove(deviceId);
    }

    /**
     * 刷新设备的声纹缓存（注册新声纹后调用）
     */
    public void refreshDeviceVoiceprints(String deviceId) {
        loadDeviceVoiceprints(deviceId);
    }

    /**
     * 设备上线事件：加载声纹缓存
     */
    @EventListener
    public void onDeviceOnline(DeviceOnlineEvent event) {
        String deviceId = event.getDeviceId();
        Thread.startVirtualThread(() -> loadDeviceVoiceprints(deviceId));
    }

    /**
     * 会话关闭事件：清理声纹缓存
     */
    @EventListener
    public void onSessionClose(ChatSessionCloseEvent event) {
        var session = event.getSession();
        if (session != null && session.getSysDevice() != null) {
            String deviceId = session.getSysDevice().getDeviceId();
            clearDeviceVoiceprints(deviceId);
            logger.debug("设备 {} 声纹缓存已清理", deviceId);
        }
    }
}
