package com.xiaozhi.common.exception;

/**
 * 配置错误异常
 */
public class ConfigurationException extends XiaozhiException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
