package com.xiaozhi.service.impl;

import com.xiaozhi.dao.VoiceprintMapper;
import com.xiaozhi.dialogue.voiceprint.SpeakerEmbeddingService;
import com.xiaozhi.entity.SysVoiceprint;
import com.xiaozhi.service.SysVoiceprintService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 声纹服务实现
 */
@Service
public class SysVoiceprintServiceImpl implements SysVoiceprintService {
    private static final Logger logger = LoggerFactory.getLogger(SysVoiceprintServiceImpl.class);

    /**
     * 每个用户最多注册的声纹数量
     */
    private static final int MAX_VOICEPRINTS_PER_USER = 5;

    @Resource
    private VoiceprintMapper voiceprintMapper;

    @Resource
    private SpeakerEmbeddingService speakerEmbeddingService;

    @Override
    public SysVoiceprint register(Integer userId, String deviceId, String name, float[] embedding) {
        // 检查用户声纹数量上限
        int count = voiceprintMapper.countByUserId(userId);
        if (count >= MAX_VOICEPRINTS_PER_USER) {
            logger.warn("用户 {} 声纹数量已达上限 {}，注册失败", userId, MAX_VOICEPRINTS_PER_USER);
            return null;
        }

        SysVoiceprint voiceprint = new SysVoiceprint();
        voiceprint.setUserId(userId);
        voiceprint.setDeviceId(deviceId);
        voiceprint.setVoiceprintName(name != null ? name : "默认声纹");
        voiceprint.setEmbedding(SpeakerEmbeddingService.embeddingToBytes(embedding));
        voiceprint.setSampleCount(1);
        voiceprint.setState("1");

        voiceprintMapper.insert(voiceprint);
        logger.info("用户 {} 声纹注册成功，voiceprintId: {}, name: {}", userId, voiceprint.getVoiceprintId(), voiceprint.getVoiceprintName());
        return voiceprint;
    }

    @Override
    public SysVoiceprint addSample(Long voiceprintId, byte[] pcmData) {
        // 查询已有声纹记录
        SysVoiceprint existing = voiceprintMapper.selectById(voiceprintId);
        if (existing == null) {
            logger.warn("声纹记录不存在: voiceprintId={}", voiceprintId);
            return null;
        }

        // 提取新音频的嵌入向量
        float[] newEmbedding = speakerEmbeddingService.extractEmbedding(pcmData);
        if (newEmbedding == null) {
            logger.warn("新采样声纹提取失败: voiceprintId={}", voiceprintId);
            return null;
        }

        // 反序列化已有嵌入向量
        float[] existingEmbedding = SpeakerEmbeddingService.bytesToEmbedding(existing.getEmbedding());
        if (existingEmbedding == null) {
            logger.warn("已有声纹嵌入向量解析失败: voiceprintId={}", voiceprintId);
            return null;
        }

        // 加权平均：已有向量权重为 sampleCount，新向量权重为 1
        int currentCount = existing.getSampleCount() != null ? existing.getSampleCount() : 1;
        float[] averaged = new float[SpeakerEmbeddingService.EMBEDDING_DIM];
        for (int i = 0; i < SpeakerEmbeddingService.EMBEDDING_DIM; i++) {
            averaged[i] = (existingEmbedding[i] * currentCount + newEmbedding[i]) / (currentCount + 1);
        }

        // L2归一化
        float sumSquares = 0.0f;
        for (float v : averaged) {
            sumSquares += v * v;
        }
        float norm = (float) Math.sqrt(sumSquares);
        if (norm > 1e-10f) {
            for (int i = 0; i < averaged.length; i++) {
                averaged[i] /= norm;
            }
        }

        // 更新数据库
        int newCount = currentCount + 1;
        byte[] embeddingBytes = SpeakerEmbeddingService.embeddingToBytes(averaged);
        voiceprintMapper.updateEmbedding(voiceprintId, embeddingBytes, newCount);

        existing.setEmbedding(embeddingBytes);
        existing.setSampleCount(newCount);
        logger.info("声纹采样追加成功: voiceprintId={}, sampleCount={}", voiceprintId, newCount);
        return existing;
    }

    @Override
    public List<SysVoiceprint> listByUserId(Integer userId) {
        return voiceprintMapper.selectByUserId(userId);
    }

    @Override
    public List<SysVoiceprint> listByDeviceId(String deviceId) {
        return voiceprintMapper.selectByDeviceUserId(deviceId);
    }

    @Override
    public List<SysVoiceprint> listByUserIdAndName(Integer userId, String name) {
        return voiceprintMapper.selectByUserIdAndName(userId, name);
    }

    @Override
    public int delete(Long voiceprintId, Integer userId) {
        return voiceprintMapper.deleteById(voiceprintId, userId);
    }

    @Override
    public int countByUserId(Integer userId) {
        return voiceprintMapper.countByUserId(userId);
    }
}
