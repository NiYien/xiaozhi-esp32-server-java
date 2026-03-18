package com.xiaozhi.common.exception;

/**
 * 设备初始化异常
 */
public class DeviceInitializationException extends DeviceException {

    public DeviceInitializationException(String message) {
        super(message);
    }

    public DeviceInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
