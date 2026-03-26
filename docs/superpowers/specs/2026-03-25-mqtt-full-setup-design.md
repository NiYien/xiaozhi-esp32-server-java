# MQTT 完整方案设计：设备管理 + 音频对话（MQTT+UDP）

**日期**: 2026-03-25
**状态**: 已批准

## 1. 背景与目标

小智 ESP32 Server Java 当前通过 WebSocket 实现设备音频对话。ESP32 固件已实现 MQTT+UDP 混合协议（MQTT 传控制消息，UDP 传加密音频），但服务端缺少对应的音频对话层实现。服务端已有完整的 MQTT 设备管理层代码（唤醒、通知、OTA、传感器、分组），但未启用。

**目标**：
1. 启用 MQTT 设备管理层，搭建 EMQX Broker
2. 新增 MQTT+UDP 音频对话通道，与 WebSocket 并行共存
3. 开发阶段 Docker 部署，最终目标生产环境高并发

## 2. 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| MQTT Broker | EMQX 开源版 | 高并发（百万级连接）、原生集群、Dashboard 监控、生产就绪 |
| UDP 音频服务部署 | 内嵌 Spring Boot JVM | 低延迟共享内存、与 WebSocket 架构一致、部署简单 |
| 通道策略 | WebSocket + MQTT+UDP 并行共存 | 渐进迁移，不影响现有设备 |
| 设备认证 | 先统一凭据，后续切一机一密 | 快速跑通，生产前加强 |
| 音频加密 | AES-128-CTR | 与 ESP32 固件协议一致 |

## 3. 整体架构

```
┌───────────────────────────────────────────────────────────────┐
│                      Spring Boot 服务                          │
│                                                               │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐          │
│  │ WebSocket    │  │ MQTT 对话   │  │ MQTT 设备管理 │          │
│  │ Handler      │  │ 处理器      │  │ (现有代码)    │          │
│  └──────┬──────┘  └──────┬──────┘  └──────┬───────┘          │
│         │                │                │                   │
│         ▼                ▼                │                   │
│  ┌─────────────────────────────┐          │                   │
│  │  ChatSession (abstract)     │          │                   │
│  │  ┌─────────────┐ ┌────────┐│          │                   │
│  │  │WebSocket    │ │MqttUdp ││          │                   │
│  │  │Session      │ │Session ││          │                   │
│  │  └─────────────┘ └───┬────┘│          │                   │
│  └──────────┬────────────┼─────┘          │                   │
│             ▼            │                │                   │
│  ┌─────────────────────────────┐          │                   │
│  │  DialogueService + Handler  │          │                   │
│  │  (STT → LLM → TTS 管线)    │          │                   │
│  └─────────────────────────────┘          │                   │
│                          │                │                   │
│                ┌─────────┘                │                   │
│                ▼                          │                   │
│  ┌──────────────────┐                    │                   │
│  │ UdpAudioServer   │                    │                   │
│  │ (NIO, 端口 8888)  │                    │                   │
│  └────────┬─────────┘                    │                   │
└───────────┼──────────────────────────────┼───────────────────┘
            │                              │
            │           ┌──────────────────────────────────┐
            │           │          EMQX Broker              │
            │           │  (Docker, 端口 1883/8883/18083)   │
            │           └──────────┬───────────┬───────────┘
            │                      │           │
            │  UDP 音频包           │ MQTT      │ MQTT
            │  (AES-CTR加密)       │ 控制消息   │ 管理消息
            │  直连，不经过 Broker   │           │
            │                      │           │
            ▼                      ▼           ▼
┌───────────────────────────────────────────────────────────────┐
│                        ESP32 设备                              │
│               mqtt_protocol.cc (MQTT控制 + UDP音频)            │
│                                                               │
│   ┌─ UDP 音频 ──→ 直连 Spring Boot:8888                       │
│   └─ MQTT 控制 ──→ EMQX Broker ──→ Spring Boot               │
└───────────────────────────────────────────────────────────────┘
```

**关键区分**：
- **UDP 音频包**：ESP32 ←→ Spring Boot UdpAudioServer，**直连**，不经过 EMQX
- **MQTT 控制消息**（hello/listen/abort/goodbye）：ESP32 ←→ EMQX ←→ Spring Boot
- **MQTT 管理消息**（heartbeat/online/offline/sensor）：ESP32 ←→ EMQX ←→ Spring Boot

## 4. 第一部分：基础设施 + 设备管理层

### 4.1 EMQX Docker 部署

在 `docker-compose.yml` 中新增 EMQX 服务：

```yaml
emqx:
  image: emqx/emqx:5.8
  ports:
    - "1883:1883"      # MQTT
    - "8883:8883"      # MQTTS
    - "18083:18083"    # Dashboard
  environment:
    - EMQX_NAME=xiaozhi-emqx
    - EMQX_LISTENERS__TCP__DEFAULT__MAX_CONNECTIONS=100000
  volumes:
    - emqx-data:/opt/emqx/data
    - emqx-log:/opt/emqx/log
  networks:
    - app-network
  healthcheck:
    test: ["CMD", "emqx", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
  restart: unless-stopped
```

Server 服务的 MQTT broker URL 应使用 Docker 内部网络名：
- `XIAOZHI_MQTT_BROKER_URL=tcp://emqx:1883`（替换现有的 `tcp://host.docker.internal:1883`）
- Server 服务需添加 `depends_on` 并使用 `condition: service_healthy`（与 MySQL 的 depends_on 模式一致）

### 4.2 EMQX Auto Subscribe 配置

ESP32 固件不主动调用 `Subscribe()`，依赖 Broker 端自动订阅（与官方 mqtt.xiaozhi.me 行为一致）。

通过 EMQX Dashboard（`http://localhost:18083`）或 API 配置 Auto Subscribe 规则：

**Dashboard 路径**：Management → MQTT Settings → Auto Subscribe

**规则**：客户端连接后，根据 `client_id`（格式 `xiaozhi_{deviceId}`）自动订阅对应的 command topic：

```
Topic:   xiaozhi/+/device/${clientid_suffix}/command
QoS:     1
```

其中 `${clientid_suffix}` 需要从 `client_id` 中提取 deviceId 部分。如果 EMQX Auto Subscribe 不支持 clientid 截取，替代方案：

**方案 A（推荐）**：调整 `MqttConfigGenerator` 中的 `client_id` 直接使用 `deviceId`（而非 `xiaozhi_{deviceId}`），Auto Subscribe 规则设为：
```
Topic:   xiaozhi/+/device/${clientid}/command
QoS:     1
```

**方案 B**：使用 EMQX Rule Engine，在 `client.connected` 事件中执行订阅动作，可以做更灵活的 topic 映射。

> 注意：此配置确保 ESP32 固件零修改即可接收服务端下发的 hello 响应、对话控制消息、唤醒/通知/OTA 等命令。

### 4.2 服务端配置

`application.yml` 中 MQTT 已有完整配置，核心变更：
- `xiaozhi.mqtt.enabled: true`
- `xiaozhi.mqtt.broker-url: tcp://emqx:1883`（Docker 内部网络）
- 配置 `device-username` / `device-password` 用于设备认证

### 4.3 数据库迁移

执行 `db/migration_mqtt_extended.sql`，创建：
- `sys_sensor_data` — 传感器数据表
- `sys_device_group` — 设备分组表
- `sys_device_group_member` — 分组成员表

### 4.4 验证清单

- EMQX Dashboard 可访问（`http://localhost:18083`）
- 服务端 `PahoMqttService` 成功连接 Broker
- `/api/mqtt/status` 返回 connected
- Python 模拟器可上报设备状态和传感器数据
- 通过 `/api/mqtt/wakeup/{deviceId}` 可下发唤醒命令
- 前端 MQTT 管理页面数据正常显示

## 5. 第二部分：音频对话层（MQTT+UDP 通道）

### 5.1 MqttUdpSession —— 复用现有 ChatSession 继承体系

现有架构中 `ChatSession` 是抽象基类，定义了四个抽象方法：
- `isOpen()` / `close()` / `sendTextMessage(String)` / `sendBinaryMessage(byte[])`

`WebSocketSession extends ChatSession` 通过 WebSocket 实现这些方法。所有下游代码（`MessageHandler`、`DialogueService`、`Player` 等）均通过 `ChatSession` 的抽象方法操作，无需感知底层通道类型。

**方案：新建 `MqttUdpSession extends ChatSession`，与 `WebSocketSession` 平级。**

这样做的优势：
- 零改动下游代码 —— 所有调用 `chatSession.sendBinaryMessage()` 的地方自动兼容
- 架构一致 —— 与现有继承体系完全匹配
- 最小改动面 —— 无需引入额外的接口层

```java
/**
 * MQTT+UDP 通道的 ChatSession 实现
 * MQTT 传 JSON 控制消息，UDP 传加密音频数据
 */
public class MqttUdpSession extends ChatSession {
    private final MqttService mqttService;
    private final UdpAudioServer udpAudioServer;
    private final UdpSessionContext udpContext;  // AES 密钥、nonce、序列号、设备地址
    private final String responseTopic;          // 服务端回复设备的 command topic

    @Override
    public boolean isOpen() { /* 检查 MQTT 连接和 UDP 通道是否活跃 */ }

    @Override
    public void close() { /* 关闭 UDP 通道，发送 goodbye */ }

    @Override
    public void sendTextMessage(String message) {
        // 通过 MQTT command topic 发送 JSON 控制消息
        mqttService.publish(responseTopic, message, 1);
    }

    @Override
    public void sendBinaryMessage(byte[] opusData) {
        // 通过 UDP 发送 AES-CTR 加密的音频数据
        udpAudioServer.sendEncrypted(udpContext, opusData);
    }
}
```

**不引入 AudioChannel 接口**，避免双重抽象和大面积下游改动。

### 5.2 UDP 音频服务

新包 `com.xiaozhi.communication.udp`：

**UdpAudioServer**：
- 基于 Java NIO `DatagramChannel`，非阻塞模式
- 在 Spring Boot 启动时绑定配置的 UDP 端口
- 接收数据包 → 解析 16 字节头 → 通过 SSRC 路由到对应 session → AES-CTR 解密 → 回调音频数据
- 发送数据包 → AES-CTR 加密 → 组装 16 字节头 → 发送到设备地址

**UdpAudioPacket**：
- 数据包结构定义，与 ESP32 固件 `mqtt_protocol.cc` 中的格式完全对齐

```
偏移  字段          大小     说明
0     type          1B      0x01 = 音频数据
1     flags         1B      保留
2     payload_len   2B      网络字节序（big-endian），Opus 数据长度
4     ssrc          4B      同步源ID，标识 session
8     timestamp     4B      网络字节序，毫秒时间戳
12    sequence      4B      网络字节序，单调递增（从 1 开始）
16    payload       变长     AES-CTR 加密的 Opus 数据
```

**关键设计：16 字节包头 = AES-CTR 的 nonce/IV（双重用途）**

ESP32 固件中，数据包的前 16 字节既是包头元数据，也直接用作 AES-CTR 加解密的 IV。具体机制：

- **发送端**（ESP32 `SendAudio()`，mqtt_protocol.cc 第 166-189 行）：
  1. 复制 hello 响应中获得的初始 nonce（16 字节）
  2. 在 nonce 的特定偏移写入本包的 payload_len（字节 2-3）、timestamp（字节 8-11）、sequence（字节 12-15）
  3. 用修改后的 16 字节 nonce 作为 AES-CTR IV 加密 opus payload
  4. 发出的数据包 = 这 16 字节 nonce + 加密后的 payload

- **接收端**（ESP32 UDP OnMessage，mqtt_protocol.cc 第 243-287 行）：
  1. 取收到数据包的前 16 字节，直接作为 AES-CTR IV 解密
  2. 从字节 8-11 解析 timestamp，从字节 12-15 解析 sequence
  3. payload 从偏移 16 开始

- **每包独立加密**：每次调用 `mbedtls_aes_crypt_ctr()` 时 `nc_off=0`，不延续上一次的 counter 状态
- **上下行共用同一个 AES key**（同一个 `aes_ctx_`），通过不同的 nonce 内容区分每个包

**UdpSessionContext**：
- 存储每个 UDP session 的状态：设备地址、AES 密钥、初始 nonce 模板、本地/远程序列号、SSRC

### 5.3 AES-CTR 加解密

新工具类 `AesCtrCodec`：
- 使用 JDK 内置 `javax.crypto.Cipher`（AES/CTR/NoPadding）
- 每个数据包独立加解密（每次 new Cipher 或 reinit，不复用 counter 状态）
- 密钥和初始 nonce 在 hello 握手时随机生成，通过 MQTT 下发给设备

**加解密流程**：
```
加密（服务端发送）：
  1. 复制初始 nonce 模板（16 字节）
  2. 在 nonce 中写入：payload_len(字节2-3), ssrc(字节4-7), timestamp(字节8-11), sequence(字节12-15)
  3. Cipher.init(ENCRYPT, key, new IvParameterSpec(nonce))
  4. encrypted = Cipher.doFinal(opusData)
  5. 发送：nonce(16字节) + encrypted

解密（服务端接收）：
  1. 取数据包前 16 字节作为 nonce
  2. Cipher.init(DECRYPT, key, new IvParameterSpec(nonce))
  3. decrypted = Cipher.doFinal(payload)
```

**参数说明**：
- 密钥：16 字节随机生成（AES-128），hello 响应中以 32 字符 hex 下发
- 初始 Nonce：16 字节随机生成，hello 响应中以 32 字符 hex 下发，作为 nonce 模板
- 每包的 nonce 不同（因为 sequence/timestamp 不同），保证 CTR 模式安全性
- 与 ESP32 固件 `mqtt_protocol.cc` 中 `mbedtls_aes_crypt_ctr()` 的行为一致

### 5.4 MQTT 对话处理器

新类 `MqttDialogueHandler`：

**职责**：监听设备通过 MQTT 发来的对话控制消息，管理对话生命周期。

**Topic 路由策略**（与现有 `MqttConfigGenerator` 一致）：
- 设备发布到 **status topic**：`xiaozhi/{userId}/device/{deviceId}/status`
- 设备订阅 **command topic**：`xiaozhi/{userId}/device/{deviceId}/command`
- 服务端 MqttDialogueHandler 订阅：`xiaozhi/+/device/+/status`（监听设备消息）
- 服务端回复发布到：`xiaozhi/{userId}/device/{deviceId}/command`（下发给设备）

> 注意：对话控制消息（hello/listen/abort/goodbye）与设备管理消息（online/offline/heartbeat）共享 status/command topic，通过消息中的 `type` 字段区分。`MqttDeviceStatusListener` 处理管理类消息，`MqttDialogueHandler` 处理对话类消息。

**消息处理流程**：

1. **hello 消息**：
   - 分配 session_id 和 SSRC
   - 随机生成 AES-128 密钥和 nonce
   - 创建 `UdpSessionContext` 和 `MqttUdpSession`
   - 注册到 `SessionManager`
   - 通过 MQTT command topic 回复 hello 响应（含 UDP server/port/key/nonce）

2. **listen / abort / goodbye 消息**：
   - 查找对应 session
   - **委托给现有 `MessageHandler.handleMessage()` 处理**，复用已有的消息路由逻辑
   - 这样 IoT 描述符上报、Device MCP 等消息类型也自动支持

3. **IoT / DeviceMcp 等其他消息类型**：
   - 同样通过 `MessageHandler.handleMessage()` 路由，无需重复实现

**hello 响应格式**（与 ESP32 固件 `ParseServerHello()` 对齐）：
```json
{
  "type": "hello",
  "transport": "udp",
  "session_id": "uuid",
  "audio_params": {
    "format": "opus",
    "sample_rate": 24000,
    "channels": 1,
    "frame_duration": 60
  },
  "udp": {
    "server": "<服务端IP>",
    "port": 8888,
    "key": "<32字符hex>",
    "nonce": "<32字符hex>"
  }
}
```

### 5.5 Session 管理扩展

**SessionManager 变更**：
- 支持两种 session 来源：WebSocket 连接事件 / MQTT hello 消息
- `ChatSession` 的多态方法自动适配不同通道类型
- 设备同时只能有一个活跃 session（新连接踢掉旧 session）

**关键修复 —— `closeSession()` 中的 `instanceof WebSocketSession` 硬编码**：
当前 `SessionManager.closeSession()` 第 130 行有 `if(chatSession instanceof WebSocketSession)` 判断，导致非 WebSocket 类型的 session 无法正常关闭和清理。需要改为通用逻辑：
```java
public void closeSession(ChatSession chatSession) {
    if (chatSession == null) return;
    try {
        sessionRegistry.remove(chatSession.getSessionId());
        chatSession.close();
        applicationContext.publishEvent(new ChatSessionCloseEvent(chatSession));
        chatSession.clearAudioSinks();
        logger.info("会话已关闭 - SessionId: {} SessionType: {}",
            chatSession.getSessionId(), chatSession.getClass().getSimpleName());
    } catch (Exception e) {
        logger.error("清理会话资源时发生错误 - SessionId: {}", chatSession.getSessionId(), e);
    }
}
```

**设备状态三态整合**：
- WebSocket 连接 → online（现有逻辑不变）
- MQTT hello → online
- MQTT 心跳 → 更新最后活跃时间
- 断开 → offline

**MQTT Session 心跳与超时**：
- 复用现有 `inactive.timeout.seconds` 配置作为 session 超时时间
- UDP 通道无数据超时：如果 MQTT 连接仍在但 UDP 长时间无音频包（超过 session 超时时间），视为空闲 session，不主动关闭（等待设备下一次 hello）
- MQTT Last Will Testament (LWT)：设备连接 EMQX 时配置 LWT 消息，非正常断开时 Broker 自动发布 offline 消息到 status topic，`MqttDeviceStatusListener` 检测到后清理 session

### 5.6 OTA 配置下发

现有 `MqttConfigGenerator` 已实现（无需修改），OTA 响应中包含：
```json
{
  "mqtt": {
    "endpoint": "emqx-host:1883",
    "client_id": "xiaozhi_{deviceId}",
    "username": "xiaozhi-device",
    "password": "shared-password",
    "keepalive": 240,
    "publish_topic": "xiaozhi/{userId}/device/{deviceId}/status",
    "subscribe_topic": "xiaozhi/{userId}/device/{deviceId}/command"
  }
}
```

> 注意：`publish_topic` 是 status topic（设备发布），`subscribe_topic` 是 command topic（设备订阅）。与 `MqttConfigGenerator` 代码中 `buildStatusTopic()` / `buildCommandTopic()` 一致。

ESP32 收到后存入 NVS，下次启动自动选择 MQTT 协议。

### 5.7 配置项

`application.yml` 新增：
```yaml
xiaozhi:
  udp:
    enabled: true
    port: 8888              # UDP 音频端口
    buffer-size: 4096       # 接收缓冲区大小
```

`docker-compose.yml` 暴露 UDP 端口：
```yaml
server:
  ports:
    - "8091:8091"      # HTTP
    - "8888:8888/udp"  # UDP 音频
```

## 6. 数据流详细对比

### WebSocket 方案（现有）
```
设备 → WebSocket 二进制帧(Opus) → ChatSession → Opus解码 → VAD → STT
                                                                  ↓
设备 ← WebSocket 二进制帧(Opus) ← ChatSession ← Opus编码 ← TTS ← LLM
```

### MQTT+UDP 方案（新增）
```
设备 → UDP(加密Opus) → UdpAudioServer → AES解密 → MqttUdpSession → ChatSession → Opus解码 → VAD → STT
                                                                                                          ↓
设备 ← UDP(加密Opus) ← UdpAudioServer ← AES加密 ← MqttUdpSession ← ChatSession ← Opus编码 ← TTS ← LLM

设备 → MQTT(JSON控制) → MqttDialogueHandler → SessionManager → DialogueService
设备 ← MQTT(JSON控制) ← MqttDialogueHandler ←
```

## 7. 新增/修改文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `communication/udp/UdpAudioServer.java` | UDP 音频服务端，NIO 非阻塞 |
| `communication/udp/UdpAudioPacket.java` | UDP 数据包结构定义 |
| `communication/udp/UdpSessionContext.java` | UDP session 状态（密钥、序列号、地址） |
| `communication/udp/UdpProperties.java` | UDP 配置属性类 |
| `communication/udp/AesCtrCodec.java` | AES-128-CTR 加解密工具 |
| `communication/common/MqttUdpSession.java` | MQTT+UDP 通道的 ChatSession 实现 |
| `communication/mqtt/MqttDialogueHandler.java` | MQTT 对话控制消息处理器 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `docker-compose.yml` | 新增 EMQX 服务（含 networks），暴露 UDP 端口，server 添加 depends_on |
| `application.yml` | 新增 UDP 配置，MQTT enabled 默认值 |
| `application-dev.yml` | MQTT/UDP 开发环境配置 |
| `communication/common/SessionManager.java` | 修复 `closeSession()` 的 `instanceof WebSocketSession` 硬编码，改为通用逻辑 |
| `communication/common/SessionActivityMonitor.java` | 修复第 100、125 行 `instanceof WebSocketSession` 硬编码，使超时检查和清理逻辑兼容 `MqttUdpSession` |
| `communication/common/BoundDeviceInitializer.java` | 修复第 62 行 `instanceof WebSocketSession` 判断，`MqttUdpSession` 也应设为 ONLINE 状态 |
| `pom.xml` | 无需新增依赖（AES/NIO 均为 JDK 内置） |

### 测试文件

| 文件 | 说明 |
|------|------|
| `test/.../udp/AesCtrCodecTest.java` | AES-CTR 加解密单元测试，验证与 ESP32 mbedtls 兼容 |
| `test/.../udp/UdpAudioPacketTest.java` | UDP 数据包序列化/反序列化测试 |
| `test/.../common/SessionManagerTest.java` | SessionManager 多类型 session 关闭测试 |

## 8. 推进顺序

1. **第一部分**：EMQX Docker 部署 + 设备管理层启用（基础设施，现有代码对接）
2. **第二部分 Step 1**：SessionManager 修复 + MqttUdpSession 类创建（最小改动，不影响现有功能）
3. **第二部分 Step 2**：UDP 音频服务 + AES-CTR 加解密 + 单元测试（核心新组件）
4. **第二部分 Step 3**：MQTT 对话处理器 + 委托 MessageHandler（对接对话引擎）
5. **第二部分 Step 4**：OTA 配置验证 + ESP32 设备联调
6. **集成测试**：端到端验证 MQTT+UDP 语音对话

## 9. UDP 丢包与乱序策略

实时音频对延迟敏感，不适合重传。策略：
- **服务端不做 jitter buffer**，收到即处理（与 WebSocket 方案一致）
- **丢包**：由 Opus 解码器内置 PLC（Packet Loss Concealment）补偿
- **乱序**：如果收到的 sequence 小于已处理的最大 sequence，直接丢弃
- **大幅跳跃**：如果 sequence 跳跃超过 100，记录警告日志但仍正常处理

## 10. ESP32 固件 UDP 数据包格式参考

以下结构与 ESP32 固件 `mqtt_protocol.cc` 中 `SendAudio()` 和 UDP 接收逻辑对齐：

```c
// ESP32 端 UDP 数据包结构（mqtt_protocol.cc 第 245-248 行注释）
// |type 1u|flags 1u|payload_len 2u|ssrc 4u|timestamp 4u|sequence 4u|payload payload_len|
//
// 前 16 字节（header）同时用作 AES-CTR 的 nonce/IV
struct UdpAudioHeader {  // 16 字节 = AES-CTR nonce
    uint8_t  type;          // 偏移 0:  0x01 = audio
    uint8_t  flags;         // 偏移 1:  保留，当前为 0
    uint16_t payload_len;   // 偏移 2:  网络字节序 (big-endian)
    uint32_t ssrc;          // 偏移 4:  同步源ID
    uint32_t timestamp;     // 偏移 8:  网络字节序，毫秒
    uint32_t sequence;      // 偏移 12: 网络字节序，从 1 开始单调递增
};
// 紧跟 header 之后是 AES-CTR 加密的 Opus 数据
// 加密时 IV = 这 16 字节 header，每包独立加密（nc_off=0）
```

**ESP32 发送端关键代码**（mqtt_protocol.cc 第 172-184 行）：
```cpp
// 1. 复制初始 nonce 作为模板
std::string nonce = aes_nonce_;
// 2. 在 nonce 特定偏移写入本包元数据
*(uint16_t*)&nonce[2] = htons(packet->payload.size());
*(uint32_t*)&nonce[8] = htonl(packet->timestamp);
*(uint32_t*)&nonce[12] = htonl(++local_sequence_);
// 3. 用修改后的 nonce 作为 IV 加密
size_t nc_off = 0;
mbedtls_aes_crypt_ctr(&aes_ctx_, ..., &nc_off, (uint8_t*)nonce.c_str(), ...);
// 4. 发出的包 = nonce(16B) + encrypted_opus
```

**ESP32 接收端关键代码**（mqtt_protocol.cc 第 253-277 行）：
```cpp
// 1. 检查包类型
if (data[0] != 0x01) return;
// 2. 取前 16 字节直接作为 AES-CTR IV
uint8_t* nonce = (uint8_t*)data.data();
size_t nc_off = 0;
// 3. payload 从偏移 16 开始
mbedtls_aes_crypt_ctr(&aes_ctx_, decrypted_size, &nc_off, nonce, ...);
```

服务端 `UdpAudioPacket.java` 和 `AesCtrCodec.java` 必须与此行为完全一致。

## 11. 安全演进路线

**阶段一（当前）**：统一凭据
- 所有设备共用 `device-username` / `device-password`
- 在 EMQX 中配置 File Auth（内置认证）

**阶段二（生产前）**：一机一密
- 通过 EMQX HTTP Auth Plugin 对接 MySQL `sys_device` 表
- 每个设备注册时生成独立密码，存入数据库
- OTA 下发时使用设备专属凭据

## 12. 风险与注意事项

1. **NAT 穿透**：UDP 在 NAT 环境下可能受阻，云部署时服务器需有公网 IP 且 UDP 端口可达
2. **防火墙**：部分网络环境会封锁非常规 UDP 端口，需确认目标网络策略
3. **SessionManager 改动风险**：修改 `closeSession()` 时需确保 WebSocket session 的关闭行为不受影响，通过测试验证
4. **EMQX 资源占用**：开发环境内存建议至少预留 512MB 给 EMQX
