package com.xiaozhi.communication.udp;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UDP 会话上下文
 * 存储每个 MQTT+UDP 设备连接的加密密钥、地址和序列号状态
 */
public class UdpSessionContext {

    /** 设备 UDP 地址（收到第一个包后更新） */
    private volatile InetSocketAddress deviceAddress;

    /** AES-128 密钥（16 字节） */
    private final byte[] aesKey;

    /** 初始 nonce 模板（16 字节） */
    private final byte[] nonceTemplate;

    /** SSRC（同步源标识符） */
    private final int ssrc;

    /** 本地发送序列号（从 1 开始递增） */
    private final AtomicInteger localSequence = new AtomicInteger(1);

    /** 远端接收序列号（跟踪设备发来的最大序列号） */
    private final AtomicInteger remoteSequence = new AtomicInteger(0);

    /** 关联的 session ID */
    private final String sessionId;

    /** 是否活跃 */
    private volatile boolean active = true;

    /** 上次发包时间（纳秒），per-session 限速避免 burst 丢包 */
    private volatile long lastSendTimeNanos = 0;

    public UdpSessionContext(String sessionId, int ssrc, byte[] aesKey, byte[] nonceTemplate) {
        this.sessionId = sessionId;
        this.ssrc = ssrc;
        this.aesKey = aesKey;
        this.nonceTemplate = nonceTemplate;
    }

    /**
     * 获取并递增本地发送序列号
     */
    public int nextLocalSequence() {
        return localSequence.getAndIncrement();
    }

    /**
     * 校验远端序列号
     *
     * @param sequence 收到的序列号
     * @return 0 = 旧包应丢弃，1 = 连续有效包，2 = 非连续有效包（有跳跃）
     */
    public int validateRemoteSequence(int sequence) {
        int prev = remoteSequence.get();
        if (Integer.compareUnsigned(sequence, prev) <= 0) {
            return 0; // 旧包，丢弃
        }
        remoteSequence.set(sequence);
        // 判断是否连续：sequence == prev + 1
        if (sequence == prev + 1) {
            return 1; // 连续
        }
        return 2; // 非连续（有跳跃）
    }

    public void deactivate() {
        this.active = false;
    }

    // Getters and setters
    public InetSocketAddress getDeviceAddress() { return deviceAddress; }
    public void setDeviceAddress(InetSocketAddress deviceAddress) { this.deviceAddress = deviceAddress; }
    public byte[] getAesKey() { return aesKey; }
    public byte[] getNonceTemplate() { return nonceTemplate; }
    public int getSsrc() { return ssrc; }
    public String getSessionId() { return sessionId; }
    public boolean isActive() { return active; }
    public long getLastSendTimeNanos() { return lastSendTimeNanos; }
    public void setLastSendTimeNanos(long nanos) { this.lastSendTimeNanos = nanos; }
    public int getRemoteSequence() { return remoteSequence.get(); }
}
