package com.xiaozhi.common.exception;

/**
 * 小智项目统一异常基类
 */
public class XiaozhiException extends RuntimeException {

    public XiaozhiException(String message) {
        super(message);
    }

    public XiaozhiException(String message, Throwable cause) {
        super(message, cause);
    }
}
