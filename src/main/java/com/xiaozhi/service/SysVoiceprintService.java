package com.xiaozhi.service;

import com.xiaozhi.entity.SysVoiceprint;

import java.util.List;

/**
 * 声纹服务接口
 */
public interface SysVoiceprintService {

    /**
     * 注册声纹
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param name     声纹名称
     * @param embedding 嵌入向量（256维float32）
     * @return 新创建的声纹记录
     */
    SysVoiceprint register(Integer userId, String deviceId, String name, float[] embedding);

    /**
     * 追加声纹采样
     * 将新音频的嵌入向量与已有向量加权平均，提高声纹精度
     *
     * @param voiceprintId 声纹ID
     * @param pcmData      新录制的PCM音频数据
     * @return 更新后的声纹记录，失败返回null
     */
    SysVoiceprint addSample(Long voiceprintId, byte[] pcmData);

    /**
     * 查询用户的所有有效声纹
     */
    List<SysVoiceprint> listByUserId(Integer userId);

    /**
     * 根据设备关联的用户查询声纹列表
     */
    List<SysVoiceprint> listByDeviceId(String deviceId);

    /**
     * 根据用户ID和名称模糊匹配查询声纹
     */
    List<SysVoiceprint> listByUserIdAndName(Integer userId, String name);

    /**
     * 删除声纹（逻辑删除）
     */
    int delete(Long voiceprintId, Integer userId);

    /**
     * 查询用户声纹数量
     */
    int countByUserId(Integer userId);
}
