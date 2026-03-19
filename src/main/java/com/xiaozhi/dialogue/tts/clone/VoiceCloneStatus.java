package com.xiaozhi.dialogue.tts.clone;

/**
 * 音色克隆训练状态枚举
 */
public enum VoiceCloneStatus {

    /** 训练中 */
    TRAINING("training"),

    /** 训练完成，可使用 */
    READY("ready"),

    /** 训练失败 */
    FAILED("failed");

    private final String value;

    VoiceCloneStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
