package com.xiaozhi.communication.mqtt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 配置属性
 * 通过 xiaozhi.mqtt 前缀绑定配置项
 */
@Data
@ConfigurationProperties(prefix = "xiaozhi.mqtt")
public class MqttProperties {

    /**
     * 是否启用 MQTT 功能，默认关闭
     */
    private boolean enabled = false;

    /**
     * MQTT Broker 地址，例如 tcp://localhost:1883
     */
    private String brokerUrl = "tcp://localhost:1883";

    /**
     * 客户端ID前缀，实际ID为 prefix + 随机后缀
     */
    private String clientIdPrefix = "xiaozhi-server";

    /**
     * 连接用户名（可选）
     */
    private String username;

    /**
     * 连接密码（可选）
     */
    private String password;

    /**
     * Topic 前缀
     */
    private String topicPrefix = "xiaozhi";

    /**
     * 连接超时时间（秒）
     */
    private int connectionTimeout = 10;

    /**
     * 心跳间隔（秒）
     */
    private int keepAliveInterval = 60;

    /**
     * 是否自动重连
     */
    private boolean automaticReconnect = true;

    /**
     * 断线重连最大延迟（秒）
     */
    private int maxReconnectDelay = 30;

    /**
     * 是否使用 Clean Start
     */
    private boolean cleanStart = true;

    /**
     * 默认消息服务质量等级 (0, 1, 2)
     */
    private int qos = 1;
}
