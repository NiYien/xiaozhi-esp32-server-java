package com.xiaozhi.common.exception;

/**
 * 语音识别(STT)异常
 */
public class SttException extends DialogueException {

    public SttException(String message) {
        super(message);
    }

    public SttException(String message, Throwable cause) {
        super(message, cause);
    }
}
