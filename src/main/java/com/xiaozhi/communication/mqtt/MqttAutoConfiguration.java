package com.xiaozhi.communication.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 自动配置类
 * 根据 xiaozhi.mqtt.enabled 配置项决定创建哪个实现
 */
@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttAutoConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(MqttAutoConfiguration.class);

    /**
     * MQTT 启用时，创建 PahoMqttService 并自动连接 Broker
     */
    @Bean
    @ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "true")
    public MqttService pahoMqttService(MqttProperties mqttProperties) {
        logger.info("MQTT 已启用，正在初始化 PahoMqttService...");
        PahoMqttService service = new PahoMqttService(mqttProperties);
        service.connect();
        return service;
    }

    /**
     * MQTT 未启用时，创建 NoOpMqttService 空实现
     */
    @Bean
    @ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "false", matchIfMissing = true)
    public MqttService noOpMqttService() {
        logger.info("MQTT 未启用，使用 NoOpMqttService 空实现");
        return new NoOpMqttService();
    }
}
