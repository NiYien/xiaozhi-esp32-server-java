package com.xiaozhi.dialogue.tts.clone;

/**
 * 音色克隆服务接口
 * 与 TTS Provider 解耦，不是所有 TTS Provider 都支持音色克隆
 */
public interface VoiceCloneService {

    /**
     * 获取提供商名称
     */
    String getProviderName();

    /**
     * 提交音色克隆训练任务
     *
     * @param samplePath 音频样本文件路径
     * @param cloneName  用户自定义音色名称
     * @param configId   TTS配置ID
     * @return 云API返回的训练任务ID
     */
    String submitCloneTask(String samplePath, String cloneName, int configId);

    /**
     * 查询训练任务状态
     *
     * @param taskId   云API的训练任务ID
     * @param configId TTS配置ID
     * @return 训练状态: training / ready / failed
     */
    VoiceCloneStatus queryStatus(String taskId, int configId);

    /**
     * 获取训练完成后的音色ID
     *
     * @param taskId   云API的训练任务ID
     * @param configId TTS配置ID
     * @return 云端生成的音色ID
     */
    String getVoiceId(String taskId, int configId);

    /**
     * 删除云端音色
     *
     * @param voiceId  云端音色ID
     * @param configId TTS配置ID
     */
    void deleteVoice(String voiceId, int configId);
}
