package com.xiaozhi.communication.udp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * UDP 音频服务器
 * 基于 Java NIO DatagramChannel，处理 ESP32 设备的加密音频数据
 * 按 SSRC 路由到对应的会话，执行 AES-CTR 加解密
 */
@Component
@ConditionalOnProperty(name = "xiaozhi.udp.enabled", havingValue = "true")
@EnableConfigurationProperties(UdpProperties.class)
public class UdpAudioServer {

    private static final Logger logger = LoggerFactory.getLogger(UdpAudioServer.class);

    private final UdpProperties udpProperties;

    /** SSRC → UdpSessionContext 映射 */
    private final ConcurrentHashMap<Integer, UdpSessionContext> sessionMap = new ConcurrentHashMap<>();

    /** 音频数据回调：(sessionId, decryptedOpusData) */
    private volatile BiConsumer<String, byte[]> audioCallback;

    private DatagramChannel channel;
    private volatile boolean running = false;

    public UdpAudioServer(UdpProperties udpProperties) {
        this.udpProperties = udpProperties;
    }

    /**
     * 设置音频数据回调
     */
    public void setAudioCallback(BiConsumer<String, byte[]> callback) {
        this.audioCallback = callback;
    }

    @PostConstruct
    public void start() {
        try {
            channel = DatagramChannel.open();
            channel.configureBlocking(true); // 使用阻塞模式 + 虚拟线程
            channel.bind(new InetSocketAddress(udpProperties.getPort()));
            running = true;

            // 使用虚拟线程作为接收循环
            Thread.startVirtualThread(this::receiveLoop);

            logger.info("UDP 音频服务启动成功，端口: {}", udpProperties.getPort());
        } catch (IOException e) {
            logger.error("UDP 音频服务启动失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException e) {
            logger.error("关闭 UDP channel 失败", e);
        }
        logger.info("UDP 音频服务已停止");
    }

    /**
     * 注册会话
     */
    public void registerSession(UdpSessionContext context) {
        sessionMap.put(context.getSsrc(), context);
        logger.info("UDP 会话已注册 - SSRC: 0x{}, SessionId: {}",
                Integer.toHexString(context.getSsrc()), context.getSessionId());
    }

    /**
     * 注销会话
     */
    public void unregisterSession(int ssrc) {
        UdpSessionContext removed = sessionMap.remove(ssrc);
        if (removed != null) {
            removed.deactivate();
            logger.info("UDP 会话已注销 - SSRC: 0x{}, SessionId: {}",
                    Integer.toHexString(ssrc), removed.getSessionId());
        }
    }

    /**
     * 发送加密音频数据到设备
     *
     * @param context  UDP 会话上下文
     * @param opusData 原始 Opus 音频数据
     */
    public void sendEncrypted(UdpSessionContext context, byte[] opusData) {
        if (!context.isActive() || context.getDeviceAddress() == null) {
            return;
        }

        try {
            int sequence = context.nextLocalSequence();
            int timestamp = sequence; // 简化：使用序列号作为时间戳

            // 构造 nonce（16 字节头）
            byte[] nonce = buildSendNonce(context, opusData.length, timestamp, sequence);

            // AES-CTR 加密
            byte[] encrypted = AesCtrCodec.encrypt(context.getAesKey(), nonce, opusData);

            // 组装完整数据包：16 字节头 + encrypted payload
            byte[] packet = new byte[UdpAudioPacket.HEADER_SIZE + encrypted.length];
            System.arraycopy(nonce, 0, packet, 0, UdpAudioPacket.HEADER_SIZE);
            System.arraycopy(encrypted, 0, packet, UdpAudioPacket.HEADER_SIZE, encrypted.length);

            // per-session 发送限速：避免 burst 发包导致 UDP NAT 丢包
            long now = System.nanoTime();
            long elapsed = now - context.getLastSendTimeNanos();
            if (context.getLastSendTimeNanos() > 0 && elapsed >= 0 && elapsed < 2_000_000L) {
                long waitNanos = 2_000_000L - elapsed;
                try {
                    Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            ByteBuffer buf = ByteBuffer.wrap(packet);
            channel.send(buf, context.getDeviceAddress());
            context.setLastSendTimeNanos(System.nanoTime());
        } catch (Exception e) {
            logger.error("发送 UDP 音频失败 - SessionId: {}", context.getSessionId(), e);
        }
    }

    /**
     * 构造发送用的 nonce
     * 基于 nonce 模板，填入 payload_len、timestamp、sequence
     */
    private byte[] buildSendNonce(UdpSessionContext context, int payloadLen, int timestamp, int sequence) {
        byte[] nonce = context.getNonceTemplate().clone();
        // [2:3] = payload_len (big-endian)
        nonce[2] = (byte) ((payloadLen >> 8) & 0xFF);
        nonce[3] = (byte) (payloadLen & 0xFF);
        // [8:11] = timestamp (big-endian)
        nonce[8] = (byte) ((timestamp >> 24) & 0xFF);
        nonce[9] = (byte) ((timestamp >> 16) & 0xFF);
        nonce[10] = (byte) ((timestamp >> 8) & 0xFF);
        nonce[11] = (byte) (timestamp & 0xFF);
        // [12:15] = sequence (big-endian)
        nonce[12] = (byte) ((sequence >> 24) & 0xFF);
        nonce[13] = (byte) ((sequence >> 16) & 0xFF);
        nonce[14] = (byte) ((sequence >> 8) & 0xFF);
        nonce[15] = (byte) (sequence & 0xFF);
        return nonce;
    }

    /**
     * 接收循环
     */
    private void receiveLoop() {
        ByteBuffer buffer = ByteBuffer.allocate(udpProperties.getBufferSize());

        while (running) {
            try {
                buffer.clear();
                InetSocketAddress sender = (InetSocketAddress) channel.receive(buffer);
                if (sender == null) continue;

                buffer.flip();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);

                // 处理收到的包
                processReceivedPacket(data, sender);

            } catch (IOException e) {
                if (running) {
                    logger.error("UDP 接收异常", e);
                }
            }
        }
    }

    /**
     * 处理收到的数据包：解析头 → 按 SSRC 路由 → AES 解密 → 序列号校验 → 回调音频数据
     */
    private void processReceivedPacket(byte[] data, InetSocketAddress sender) {
        if (data.length < UdpAudioPacket.HEADER_SIZE) {
            logger.warn("收到的 UDP 包过小: {} 字节", data.length);
            return;
        }

        try {
            UdpAudioPacket packet = UdpAudioPacket.parse(data);

            // 检查包类型
            if (packet.getType() != UdpAudioPacket.TYPE_AUDIO) {
                logger.debug("忽略非音频包，type: 0x{}", String.format("%02x", packet.getType()));
                return;
            }

            // 按 SSRC 路由
            int ssrc = packet.getSsrc();
            UdpSessionContext context = sessionMap.get(ssrc);
            if (context == null) {
                logger.warn("未知 SSRC: 0x{}", Integer.toHexString(ssrc));
                return;
            }

            // 更新设备地址
            context.setDeviceAddress(sender);

            // 序列号校验：0=旧包丢弃，1=连续，2=非连续（有跳跃）
            int sequence = packet.getSequence();
            int seqResult = context.validateRemoteSequence(sequence);
            if (seqResult == 0) {
                logger.debug("丢弃旧包 - SSRC: 0x{}, seq: {}, last: {}",
                        Integer.toHexString(ssrc), sequence, context.getRemoteSequence());
                return;
            }
            if (seqResult == 2) {
                logger.warn("非连续包 - SSRC: 0x{}, seq: {}, expected: {}, 可能有丢包",
                        Integer.toHexString(ssrc), sequence, context.getRemoteSequence() - 1);
            }

            // AES-CTR 解密：使用 16 字节头作为 nonce
            byte[] nonce = packet.getHeader();
            byte[] decrypted = AesCtrCodec.decrypt(context.getAesKey(), nonce, packet.getPayload());

            // 回调音频数据
            BiConsumer<String, byte[]> callback = this.audioCallback;
            if (callback != null) {
                callback.accept(context.getSessionId(), decrypted);
            }

        } catch (Exception e) {
            logger.error("处理 UDP 包异常", e);
        }
    }
}
