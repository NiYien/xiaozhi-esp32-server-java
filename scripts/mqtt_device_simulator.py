#!/usr/bin/env python3
"""
MQTT 设备模拟器
模拟 ESP32 设备通过 MQTT 与服务端通信

使用方法：
  pip install paho-mqtt
  python scripts/mqtt_device_simulator.py

可通过环境变量或修改下方配置来调整参数。
"""

import json
import time
import paho.mqtt.client as mqtt

# ===== 配置 =====
BROKER_HOST = "localhost"
BROKER_PORT = 1883
USER_ID = "1"                          # 用户ID（需要在数据库中存在）
DEVICE_ID = "test-device-001"          # 设备ID（需要在 sys_device 表中存在）
TOPIC_PREFIX = "xiaozhi"
HEARTBEAT_INTERVAL = 30                # 心跳间隔（秒）

# Topic 定义
STATUS_TOPIC = f"{TOPIC_PREFIX}/{USER_ID}/device/{DEVICE_ID}/status"
COMMAND_TOPIC = f"{TOPIC_PREFIX}/{USER_ID}/device/{DEVICE_ID}/command"
NOTIFY_TOPIC = f"{TOPIC_PREFIX}/{USER_ID}/device/{DEVICE_ID}/notify"


def on_connect(client, userdata, flags, rc, properties=None):
    """连接成功回调"""
    if rc == 0:
        print(f"[连接成功] Broker: {BROKER_HOST}:{BROKER_PORT}")
        # 订阅命令和通知 Topic
        client.subscribe(COMMAND_TOPIC, qos=1)
        client.subscribe(NOTIFY_TOPIC, qos=1)
        print(f"[已订阅] {COMMAND_TOPIC}")
        print(f"[已订阅] {NOTIFY_TOPIC}")
        # 发送上线消息
        send_status(client, "online")
    else:
        print(f"[连接失败] 返回码: {rc}")


def on_message(client, userdata, msg):
    """收到消息回调"""
    try:
        payload = json.loads(msg.payload.decode("utf-8"))
        msg_type = payload.get("type", "unknown")
        print(f"\n[收到消息] Topic: {msg.topic}")
        print(f"  类型: {msg_type}")
        print(f"  内容: {json.dumps(payload, ensure_ascii=False, indent=2)}")
    except json.JSONDecodeError:
        print(f"\n[收到消息] Topic: {msg.topic}, Payload: {msg.payload.decode('utf-8')}")


def on_disconnect(client, userdata, rc, properties=None):
    """断开连接回调"""
    print(f"[连接断开] 返回码: {rc}")


def send_status(client, status_type):
    """发送状态消息"""
    message = {
        "type": status_type,
        "timestamp": int(time.time() * 1000),
        "payload": {
            "deviceId": DEVICE_ID
        }
    }
    client.publish(STATUS_TOPIC, json.dumps(message), qos=1)
    print(f"[状态发送] {status_type} -> {STATUS_TOPIC}")


def main():
    # 创建 MQTT v5 客户端
    client = mqtt.Client(
        client_id=f"simulator-{DEVICE_ID}",
        protocol=mqtt.MQTTv5
    )
    client.on_connect = on_connect
    client.on_message = on_message
    client.on_disconnect = on_disconnect

    print(f"正在连接 {BROKER_HOST}:{BROKER_PORT} ...")
    client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)

    # 启动网络循环（非阻塞）
    client.loop_start()

    try:
        while True:
            time.sleep(HEARTBEAT_INTERVAL)
            send_status(client, "heartbeat")
    except KeyboardInterrupt:
        print("\n正在断开连接...")
        send_status(client, "offline")
        time.sleep(1)
        client.loop_stop()
        client.disconnect()
        print("已断开连接")


if __name__ == "__main__":
    main()
