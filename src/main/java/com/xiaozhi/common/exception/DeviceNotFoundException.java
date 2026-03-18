package com.xiaozhi.common.exception;

/**
 * 设备未找到异常
 */
public class DeviceNotFoundException extends DeviceException {

    public DeviceNotFoundException(String message) {
        super(message);
    }

    public DeviceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
