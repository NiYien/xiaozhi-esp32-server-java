package com.xiaozhi.communication.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * MQTT 统一消息格式
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttMessage {

    /**
     * 消息类型，如 wakeup、status_report、command 等
     */
    private String type;

    /**
     * 消息时间戳（毫秒级 Unix 时间戳）
     */
    private long timestamp;

    /**
     * 消息负载（JSON 对象）
     */
    private Object payload;
}
