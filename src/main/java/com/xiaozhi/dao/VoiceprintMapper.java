package com.xiaozhi.dao;

import com.xiaozhi.entity.SysVoiceprint;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 声纹数据 数据层
 */
public interface VoiceprintMapper {

    /**
     * 插入声纹记录
     */
    int insert(SysVoiceprint voiceprint);

    /**
     * 根据用户ID查询有效声纹列表
     */
    List<SysVoiceprint> selectByUserId(@Param("userId") Integer userId);

    /**
     * 根据声纹ID查询
     */
    SysVoiceprint selectById(@Param("voiceprintId") Long voiceprintId);

    /**
     * 根据设备ID关联的用户，查询该用户所有有效声纹
     */
    List<SysVoiceprint> selectByDeviceUserId(@Param("deviceId") String deviceId);

    /**
     * 根据用户ID和名称模糊匹配查询声纹
     */
    List<SysVoiceprint> selectByUserIdAndName(@Param("userId") Integer userId, @Param("name") String name);

    /**
     * 更新声纹嵌入向量和采样次数（用于多段注册的加权平均）
     */
    int updateEmbedding(@Param("voiceprintId") Long voiceprintId, @Param("embedding") byte[] embedding, @Param("sampleCount") Integer sampleCount);

    /**
     * 删除声纹（逻辑删除，设置state=0）
     */
    int deleteById(@Param("voiceprintId") Long voiceprintId, @Param("userId") Integer userId);

    /**
     * 根据用户ID查询声纹数量
     */
    int countByUserId(@Param("userId") Integer userId);
}
