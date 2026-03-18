package com.xiaozhi.common.exception;

/**
 * 语音合成(TTS)异常
 */
public class TtsException extends DialogueException {

    public TtsException(String message) {
        super(message);
    }

    public TtsException(String message, Throwable cause) {
        super(message, cause);
    }
}
