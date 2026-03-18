package com.xiaozhi.common.exception;

/**
 * 对话相关异常基类
 */
public class DialogueException extends XiaozhiException {

    public DialogueException(String message) {
        super(message);
    }

    public DialogueException(String message, Throwable cause) {
        super(message, cause);
    }
}
