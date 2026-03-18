package com.xiaozhi.dialogue.tts;

import com.xiaozhi.common.exception.TtsException;
import com.xiaozhi.dialogue.DialogueConstants;

import cn.hutool.core.util.StrUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * TTS服务抽象基类
 * 封装重试机制、文件保存等公共逻辑，子类只需实现 doTextToSpeech() 方法
 */
public abstract class AbstractTtsService implements TtsService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // 公共字段
    protected final String providerName;
    protected final String voiceName;
    protected final Float speed;
    protected final Float pitch;

    protected AbstractTtsService(String providerName, String voiceName, Float speed, Float pitch) {
        this.providerName = providerName;
        this.voiceName = voiceName;
        this.speed = speed;
        this.pitch = pitch;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public String getVoiceName() {
        return voiceName;
    }

    @Override
    public Float getSpeed() {
        return speed;
    }

    @Override
    public Float getPitch() {
        return pitch;
    }

    /**
     * 模板方法：带重试的语音合成
     * 默认实现调用 synthesizeWithRetry()，不需要重试的子类可直接覆盖此方法
     */
    @Override
    public String textToSpeech(String text) throws Exception {
        return synthesizeWithRetry(text);
    }

    /**
     * 带重试的语音合成框架
     * 捕获子类 doTextToSpeech() 的异常并进行重试
     */
    protected String synthesizeWithRetry(String text) throws Exception {
        int attempts = 0;
        while (attempts < DialogueConstants.MAX_RETRY_ATTEMPTS) {
            try {
                return doTextToSpeech(text);
            } catch (InterruptedException e) {
                // 线程被中断（用户打断对话），属于正常流程，不重试
                Thread.currentThread().interrupt();
                throw e;
            } catch (TtsException e) {
                attempts++;
                if (attempts < DialogueConstants.MAX_RETRY_ATTEMPTS) {
                    logger.warn("[{}] TTS失败，正在重试 ({}/{}): {}",
                            getProviderName(), attempts, DialogueConstants.MAX_RETRY_ATTEMPTS, e.getMessage());
                    Thread.sleep(DialogueConstants.RETRY_DELAY_MS);
                } else {
                    logger.error("[{}] TTS失败，已达到最大重试次数", getProviderName(), e);
                    throw e;
                }
            } catch (Exception e) {
                attempts++;
                if (attempts < DialogueConstants.MAX_RETRY_ATTEMPTS) {
                    logger.warn("[{}] TTS失败，正在重试 ({}/{}): {}",
                            getProviderName(), attempts, DialogueConstants.MAX_RETRY_ATTEMPTS, e.getMessage());
                    Thread.sleep(DialogueConstants.RETRY_DELAY_MS);
                } else {
                    logger.error("[{}] TTS失败，已达到最大重试次数", getProviderName(), e);
                    throw new TtsException("TTS合成失败: " + e.getMessage(), e);
                }
            }
        }
        return StrUtil.EMPTY;
    }

    /**
     * 子类实现的实际语音合成逻辑
     * 合成失败时应抛出异常以触发重试
     *
     * @param text 要转换为语音的文本
     * @return 生成的音频文件路径
     * @throws Exception 合成失败时抛出异常
     */
    protected abstract String doTextToSpeech(String text) throws Exception;

    // ========== 工具方法 ==========

    /**
     * 将音频字节数据保存到文件
     *
     * @param data     音频字节数据
     * @param filePath 目标文件路径
     * @throws IOException 写入失败时抛出异常
     */
    protected void saveAudioFile(byte[] data, String filePath) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    /**
     * 生成音频文件的完整路径
     *
     * @param outputPath 输出目录路径
     * @return 完整的文件路径
     */
    protected String generateFilePath(String outputPath) {
        return outputPath + getAudioFileName();
    }
}
