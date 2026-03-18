package com.xiaozhi.communication.common;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码生成追踪器
 * 负责追踪设备验证码生成状态，防止重复生成
 * 完全独立，不依赖 SessionRegistry
 */
public class CaptchaGenerationTracker {

    // 存储验证码生成状态
    private final ConcurrentHashMap<String, Boolean> captchaState = new ConcurrentHashMap<>();

    /**
     * 标记设备正在生成验证码
     *
     * @param deviceId 设备ID
     * @return 如果设备之前没有在生成验证码，返回true；否则返回false
     */
    public boolean markCaptchaGeneration(String deviceId) {
        return captchaState.putIfAbsent(deviceId, Boolean.TRUE) == null;
    }

    /**
     * 取消设备验证码生成标记
     *
     * @param deviceId 设备ID
     */
    public void unmarkCaptchaGeneration(String deviceId) {
        captchaState.remove(deviceId);
    }
}
