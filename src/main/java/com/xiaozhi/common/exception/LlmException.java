package com.xiaozhi.common.exception;

/**
 * 大语言模型(LLM)调用异常
 */
public class LlmException extends DialogueException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
