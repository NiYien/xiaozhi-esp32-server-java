package com.xiaozhi.communication.mqtt;

/**
 * MQTT 服务接口
 * 封装发布、订阅、连接查询等操作
 */
public interface MqttService {

    /**
     * 发布消息到指定 Topic
     *
     * @param topic   目标 Topic
     * @param payload JSON 消息内容
     * @param qos     服务质量等级 (0, 1, 2)
     */
    void publish(String topic, String payload, int qos);

    /**
     * 发布消息到指定 Topic（默认 QoS 1）
     *
     * @param topic   目标 Topic
     * @param payload JSON 消息内容
     */
    default void publish(String topic, String payload) {
        publish(topic, payload, 1);
    }

    /**
     * 订阅指定 Topic
     *
     * @param topicFilter Topic 过滤器（支持通配符）
     * @param qos         服务质量等级
     * @param listener    消息监听器
     */
    void subscribe(String topicFilter, int qos, MqttMessageListener listener);

    /**
     * 取消订阅
     *
     * @param topicFilter Topic 过滤器
     */
    void unsubscribe(String topicFilter);

    /**
     * 查询 MQTT 连接是否正常
     *
     * @return true 表示已连接
     */
    boolean isConnected();

    /**
     * 获取当前客户端 ID
     *
     * @return 客户端ID，未连接时返回空字符串
     */
    default String getClientId() {
        return "";
    }

    /**
     * 构建设备命令 Topic
     * 格式: {topicPrefix}/{userId}/device/{deviceId}/command
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 完整的 Topic 路径
     */
    String buildCommandTopic(String userId, String deviceId);

    /**
     * 构建设备状态 Topic
     * 格式: {topicPrefix}/{userId}/device/{deviceId}/status
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 完整的 Topic 路径
     */
    String buildStatusTopic(String userId, String deviceId);

    /**
     * 构建设备通知 Topic
     * 格式: {topicPrefix}/{userId}/device/{deviceId}/notify
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 完整的 Topic 路径
     */
    String buildNotifyTopic(String userId, String deviceId);

    /**
     * 构建设备传感器数据 Topic
     * 格式: {topicPrefix}/{userId}/device/{deviceId}/sensor
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 完整的 Topic 路径
     */
    String buildSensorTopic(String userId, String deviceId);

    /**
     * 构建分组命令 Topic
     * 格式: {topicPrefix}/{userId}/group/{groupId}/command
     *
     * @param userId  用户ID
     * @param groupId 分组ID
     * @return 完整的 Topic 路径
     */
    String buildGroupCommandTopic(String userId, String groupId);

    /**
     * 构建分组通知 Topic
     * 格式: {topicPrefix}/{userId}/group/{groupId}/notify
     *
     * @param userId  用户ID
     * @param groupId 分组ID
     * @return 完整的 Topic 路径
     */
    String buildGroupNotifyTopic(String userId, String groupId);

    /**
     * MQTT 消息监听器
     */
    @FunctionalInterface
    interface MqttMessageListener {
        /**
         * 收到消息时的回调
         *
         * @param topic   消息来源 Topic
         * @param payload 消息内容
         */
        void onMessage(String topic, String payload);
    }
}
