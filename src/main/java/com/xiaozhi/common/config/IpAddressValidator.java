package com.xiaozhi.common.config;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * IP地址和MAC地址验证工具类
 */
@Component
public class IpAddressValidator {

    /**
     * MAC地址正则表达式
     */
    private static final Pattern macPattern = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");

    /**
     * 判断MAC地址是否合法
     */
    public boolean isMacAddressValid(String mac) {
        // 正则校验格式
        if (!macPattern.matcher(mac).matches()) {
            return false;
        }
        // 校验MAC地址是否为单播地址
        String normalized = mac.toLowerCase();
        String[] parts = normalized.split("[:-]");
        int firstByte = Integer.parseInt(parts[0], 16);
        return (firstByte & 1) == 0; // 最低位为0表示单播地址，合法
    }

    /**
     * 检查是否为私有IP
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // 10.0.0.0/8
            if (first == 10) return true;
            // 172.16.0.0/12
            if (first == 172 && second >= 16 && second <= 31) return true;
            // 192.168.0.0/16
            if (first == 192 && second == 168) return true;
            // 127.0.0.0/8 (loopback)
            if (first == 127) return true;

            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查IP是否可达
     */
    public static boolean isReachable(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isReachable(1000); // 1秒超时
        } catch (Exception e) {
            return false;
        }
    }
}
