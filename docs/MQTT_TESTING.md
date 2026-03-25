# MQTT 功能测试指南

本文档提供 MQTT 模块的完整测试方案，包括 Broker 搭建、设备模拟、API 验证和前端页面验证。

## 1. 搭建 MQTT Broker（Docker Mosquitto）

### 1.1 创建配置文件

在项目根目录创建 `mosquitto/` 目录和配置文件：

```bash
mkdir -p mosquitto/config mosquitto/data mosquitto/log
```

创建 `mosquitto/config/mosquitto.conf`：

```conf
# 监听端口
listener 1883

# 允许匿名访问（仅开发测试用）
allow_anonymous true

# 持久化
persistence true
persistence_location /mosquitto/data/

# 日志
log_dest file /mosquitto/log/mosquitto.log
log_dest stdout
```

### 1.2 Docker Compose 配置

创建 `docker-compose-mqtt.yml`（或添加到已有的 `docker-compose.yml`）：

```yaml
version: '3.8'
services:
  mosquitto:
    image: eclipse-mosquitto:2
    container_name: xiaozhi-mosquitto
    ports:
      - "1883:1883"
      - "9001:9001"
    volumes:
      - ./mosquitto/config/mosquitto.conf:/mosquitto/config/mosquitto.conf
      - ./mosquitto/data:/mosquitto/data
      - ./mosquitto/log:/mosquitto/log
    restart: unless-stopped
```

### 1.3 启动 Broker

```bash
docker-compose -f docker-compose-mqtt.yml up -d
```

验证 Broker 运行：

```bash
docker logs xiaozhi-mosquitto
# 应看到类似：mosquitto version 2.x.x running
```

## 2. 服务端 MQTT 启用配置

修改 `src/main/resources/application.yml`（或 `application-dev.yml`）：

```yaml
xiaozhi:
  mqtt:
    enabled: true                    # 改为 true
    broker-url: tcp://localhost:1883  # Broker 地址
    client-id-prefix: xiaozhi-server
    topic-prefix: xiaozhi
    # username: your_username        # 匿名模式下无需配置
    # password: your_password
    connection-timeout: 10
    keep-alive-interval: 60
    automatic-reconnect: true
    max-reconnect-delay: 30
    clean-start: true
    qos: 1
```

重启服务端后，日志中应出现：

```
MQTT 已启用，正在初始化 PahoMqttService...
MQTT Broker 连接成功: tcp://localhost:1883
```

## 3. 使用 MQTTX 模拟设备

[MQTTX](https://mqttx.app/) 是一款跨平台 MQTT 客户端工具，提供图形化界面。

### 3.1 连接配置

- **Name**: `测试设备`
- **Host**: `mqtt://localhost`
- **Port**: `1883`
- **Client ID**: `test-device-001`（任意，但建议有意义的名称）
- **Username / Password**: 留空（匿名模式）

### 3.2 订阅命令 Topic

连接成功后，订阅设备的命令 Topic 以接收唤醒/通知消息：

```
xiaozhi/{userId}/device/{deviceId}/command
```

例如，假设用户ID为 `1`，设备ID为 `test-device-001`：

```
xiaozhi/1/device/test-device-001/command
```

同时订阅通知 Topic：

```
xiaozhi/1/device/test-device-001/notify
```

### 3.3 发送心跳消息

向状态 Topic 发布心跳消息，让服务端感知设备在线：

**Topic**: `xiaozhi/1/device/test-device-001/status`

**Payload**（JSON）：

```json
{
  "type": "heartbeat",
  "timestamp": 1711350000000,
  "payload": {
    "deviceId": "test-device-001"
  }
}
```

发送一次心跳后，设备状态应变为 `standby`（仅 MQTT 在线）。

### 3.4 发送上线消息

也可以发送 `online` 类型消息：

```json
{
  "type": "online",
  "timestamp": 1711350000000,
  "payload": {
    "deviceId": "test-device-001"
  }
}
```

## 4. Python paho-mqtt 模拟设备脚本

### 4.1 安装依赖

```bash
pip install paho-mqtt
```

### 4.2 模拟设备脚本

创建 `scripts/mqtt_device_simulator.py`：

```python
#!/usr/bin/env python3
"""
MQTT 设备模拟器
模拟 ESP32 设备通过 MQTT 与服务端通信
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
```

### 4.3 运行模拟器

```bash
python scripts/mqtt_device_simulator.py
```

输出示例：

```
正在连接 localhost:1883 ...
[连接成功] Broker: localhost:1883
[已订阅] xiaozhi/1/device/test-device-001/command
[已订阅] xiaozhi/1/device/test-device-001/notify
[状态发送] online -> xiaozhi/1/device/test-device-001/status
[状态发送] heartbeat -> xiaozhi/1/device/test-device-001/status
```

## 5. 端到端测试步骤

### 步骤 1：启动 Broker 和服务端

```bash
# 启动 MQTT Broker
docker-compose -f docker-compose-mqtt.yml up -d

# 启动服务端（确保 mqtt.enabled=true）
mvn clean package -DskipTests
java -jar target/xiaozhi.server-*.jar
```

确认服务端日志出现 `MQTT Broker 连接成功`。

### 步骤 2：模拟设备连接并发送心跳

运行 Python 模拟器或使用 MQTTX：

```bash
python scripts/mqtt_device_simulator.py
```

模拟器会自动发送 `online` 消息和定期心跳。

> **注意**：设备ID（如 `test-device-001`）需要在数据库 `sys_device` 表中存在且绑定到对应用户。可以通过管理后台添加设备。

### 步骤 3：前端验证设备状态

1. 登录管理后台（`http://localhost:8084`）
2. 点击左侧菜单「MQTT 管理」
3. 验证连接状态卡片显示「已连接」（绿色）
4. 验证配置信息卡片显示 Broker 地址、Topic 前缀等
5. 验证设备状态列表中模拟设备显示为 `standby`（橙色标签），通道显示 `MQTT`

### 步骤 4：测试唤醒功能

1. 在 MQTT 管理页面操作区，输入设备ID，点击「唤醒」
2. 或在设备列表中找到 standby 设备，点击「唤醒」按钮
3. 检查 Python 模拟器或 MQTTX 收到的 command 消息：

```json
{
  "type": "wakeup",
  "timestamp": 1711350000000,
  "payload": {
    "action": "wakeup",
    "deviceId": "test-device-001"
  }
}
```

### 步骤 5：测试通知功能

1. 在操作区输入设备ID和通知文本，点击「发送通知」
2. 检查模拟器收到 notify Topic 的消息：

```json
{
  "type": "notify",
  "timestamp": 1711350000000,
  "payload": {
    "deviceId": "test-device-001",
    "text": "你好，这是一条测试通知"
  }
}
```

### 步骤 6：测试离线检测

1. 停止 Python 模拟器（Ctrl+C）
2. 等待约 3 分钟（3 倍心跳间隔 = 3 × 60s = 180s）
3. 刷新 MQTT 管理页面
4. 验证设备状态变为 `offline`（灰色标签）
5. 服务端日志应出现：`设备 MQTT 心跳超时，标记为离线`

## 6. 常见问题

### Broker 连接失败

- 检查 Docker 容器是否正常运行：`docker ps | grep mosquitto`
- 检查端口是否被占用：`netstat -an | grep 1883`
- 检查防火墙是否放行 1883 端口

### 设备状态不更新

- 确认心跳消息的 Topic 格式正确：`xiaozhi/{userId}/device/{deviceId}/status`
- 确认消息 JSON 格式正确，`type` 字段为 `heartbeat` / `online` / `offline`
- 检查服务端日志是否有 `设备 MQTT 心跳` 相关日志

### 前端页面不显示 MQTT 菜单

- 确认已重新构建前端：`cd web && npm run build`
- 清除浏览器缓存后刷新
