package com.xiaozhi.communication.common;

import reactor.core.publisher.Sinks;

/**
 * 音频流管理器
 * 负责管理 Reactor Sinks 的创建、数据发送、完成、关闭
 */
public class AudioStreamManager {

    private final SessionRegistry sessionRegistry;

    public AudioStreamManager(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 创建音频数据流
     *
     * @param sessionId 会话ID
     */
    public void createAudioStream(String sessionId) {
        Sinks.Many<byte[]> sink = Sinks.many().multicast().onBackpressureBuffer();
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            chatSession.setAudioSinks(sink);
        }
    }

    /**
     * 获取音频数据流
     *
     * @param sessionId 会话ID
     * @return 音频数据流
     */
    public Sinks.Many<byte[]> getAudioStream(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            return chatSession.getAudioSinks();
        }
        return null;
    }

    /**
     * 发送音频数据
     *
     * @param sessionId 会话ID
     * @param data      音频数据
     */
    public void sendAudioData(String sessionId, byte[] data) {
        Sinks.Many<byte[]> sink = getAudioStream(sessionId);
        if (sink != null) {
            sink.tryEmitNext(data);
        }
    }

    /**
     * 完成音频流
     *
     * @param sessionId 会话ID
     */
    public void completeAudioStream(String sessionId) {
        Sinks.Many<byte[]> sink = getAudioStream(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    /**
     * 关闭音频流
     *
     * @param sessionId 会话ID
     */
    public void closeAudioStream(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            chatSession.setAudioSinks(null);
        }
    }
}
