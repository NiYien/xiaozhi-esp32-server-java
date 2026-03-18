package com.xiaozhi.utils;

import com.xiaozhi.common.config.IpAddressValidator;
import com.xiaozhi.common.config.PublicIpInfoProvider;
import com.xiaozhi.common.config.ServerAddressProvider;
import com.xiaozhi.common.config.ServerIpDetector;
import com.xiaozhi.common.web.SessionUserProvider;
import com.xiaozhi.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CmsUtils 薄外观层
 * 所有实现已委托给专业类，本类仅保留公共方法签名以保持向后兼容
 */
@Component
public class CmsUtils {

    public static final String USER_ATTRIBUTE_KEY = SessionUserProvider.USER_ATTRIBUTE_KEY;

    @Autowired
    private ServerAddressProvider serverAddressProvider;

    @Autowired
    private ServerIpDetector serverIpDetector;

    @Autowired
    private IpAddressValidator ipAddressValidator;

    // ===== 用户会话相关（委托给 SessionUserProvider） =====

    public static SysUser getUser() {
        return SessionUserProvider.getUser();
    }

    public static void setUser(HttpServletRequest request, SysUser user) {
        SessionUserProvider.setUser(request, user);
    }

    public static Integer getUserId() {
        return SessionUserProvider.getUserId();
    }

    public static String getUsername() {
        return SessionUserProvider.getUsername();
    }

    public static String getName() {
        return SessionUserProvider.getName();
    }

    // ===== 服务器地址相关（委托给 ServerAddressProvider） =====

    public String getWebsocketAddress() {
        return serverAddressProvider.getWebsocketAddress();
    }

    public String getOtaAddress() {
        return serverAddressProvider.getOtaAddress();
    }

    public String getServerAddress() {
        return serverAddressProvider.getServerAddress();
    }

    // ===== IP检测相关（委托给 ServerIpDetector） =====

    public static String getClientIp(HttpServletRequest request) {
        return ServerIpDetector.getClientIp(request);
    }

    public String getServerIp() {
        return serverIpDetector.getServerIp();
    }

    public Map<String, Object> getEnvironmentDetails() {
        return serverIpDetector.getEnvironmentDetails();
    }

    // ===== IP/MAC验证相关（委托给 IpAddressValidator） =====

    public boolean isMacAddressValid(String mac) {
        return ipAddressValidator.isMacAddressValid(mac);
    }

    // ===== 公网IP信息相关（委托给 PublicIpInfoProvider） =====

    /**
     * IP信息类 - 保留类型别名以保持向后兼容
     */
    public static class IPInfo extends PublicIpInfoProvider.IPInfo {
        public IPInfo(String ip, String location, String isp) {
            super(ip, location, isp);
        }
    }

    public static PublicIpInfoProvider.IPInfo getIPInfoByAddress(String ipAddress) {
        return PublicIpInfoProvider.getIPInfoByAddress(ipAddress);
    }
}
