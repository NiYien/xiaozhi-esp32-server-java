package com.xiaozhi.dialogue;

/**
 * 对话子系统常量
 */
public final class DialogueConstants {

    private DialogueConstants() {
    }

    /** STT 语音识别超时时间（毫秒） */
    public static final int RECOGNITION_TIMEOUT_MS = 90000;

    /** 队列轮询超时时间（毫秒） */
    public static final int QUEUE_POLL_TIMEOUT_MS = 100;

    /** 最大重试次数 */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    /** 重试间隔时间（毫秒） */
    public static final int RETRY_DELAY_MS = 1000;
}
