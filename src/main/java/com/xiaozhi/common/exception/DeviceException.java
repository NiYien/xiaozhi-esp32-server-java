package com.xiaozhi.common.exception;

/**
 * 设备相关异常基类
 */
public class DeviceException extends XiaozhiException {

    public DeviceException(String message) {
        super(message);
    }

    public DeviceException(String message, Throwable cause) {
        super(message, cause);
    }
}
