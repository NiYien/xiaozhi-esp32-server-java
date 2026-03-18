package com.xiaozhi.common.config;

import com.xiaozhi.communication.server.websocket.WebSocketConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 服务器地址管理服务
 * 负责初始化和提供WebSocket地址、OTA地址、服务器地址
 */
@Component
public class ServerAddressProvider {

    private final ServerIpDetector serverIpDetector;

    // 新增的全局变量
    private String websocketAddress = null;
    private String otaAddress = null;
    private String serverAddress = null;

    @Value("${server.port:8091}")
    private int port;

    @Value("${xiaozhi.server.domain:}")
    private String domain;

    /**
     * 通过构造器注入ServerIpDetector，保证初始化顺序
     */
    public ServerAddressProvider(ServerIpDetector serverIpDetector) {
        this.serverIpDetector = serverIpDetector;
    }

    // 初始化websocketAddress、otaAddress
    @PostConstruct
    private void initializeAddresses() {
        if (domain != null && !domain.isEmpty()) {
            websocketAddress = "wss://ws." + domain + WebSocketConfig.WS_PATH;
            otaAddress = "https://" + domain + "/api/device/ota";
            serverAddress = "https://" + domain;
        } else {
            String configuredIp = serverIpDetector.getConfiguredIp();
            String serverIp = (configuredIp != null && !configuredIp.isEmpty()) ? configuredIp : serverIpDetector.getServerIp();
            websocketAddress = "ws://" + serverIp + ":" + port + WebSocketConfig.WS_PATH; // 默认WebSocket端口
            otaAddress = "http://" + serverIp + ":" + port + "/api/device/ota";
            serverAddress = "http://" + serverIp + ":" + port;
        }
    }

    // WebSocket地址
    public String getWebsocketAddress() {
        return websocketAddress;
    }

    // OTA地址
    public String getOtaAddress() {
        return otaAddress;
    }

    // Server地址
    public String getServerAddress() {
        return serverAddress;
    }
}
