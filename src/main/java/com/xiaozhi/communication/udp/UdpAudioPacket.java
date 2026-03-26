package com.xiaozhi.communication.udp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * UDP 音频数据包
 * 16 字节头 + 变长 payload，头同时用作 AES-CTR nonce/IV
 *
 * 头部结构（big-endian）：
 * [0]    type         - 包类型（0x01 = 音频）
 * [1]    flags        - 标志位
 * [2:3]  payload_len  - payload 长度（big-endian uint16）
 * [4:7]  ssrc         - 同步源标识符
 * [8:11] timestamp    - 时间戳（big-endian uint32）
 * [12:15] sequence    - 序列号（big-endian uint32）
 */
public class UdpAudioPacket {

    public static final int HEADER_SIZE = 16;
    public static final byte TYPE_AUDIO = 0x01;

    private byte type;
    private byte flags;
    private int payloadLen;   // unsigned 16-bit
    private int ssrc;         // unsigned 32-bit (stored as int)
    private int timestamp;    // unsigned 32-bit
    private int sequence;     // unsigned 32-bit
    private byte[] payload;

    /**
     * 从接收到的原始字节构造数据包
     *
     * @param data 完整的 UDP 数据包（头 + payload）
     * @return 解析后的 UdpAudioPacket
     */
    public static UdpAudioPacket parse(byte[] data) {
        if (data.length < HEADER_SIZE) {
            throw new IllegalArgumentException("数据包长度不足: " + data.length);
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        UdpAudioPacket packet = new UdpAudioPacket();
        packet.type = buf.get();
        packet.flags = buf.get();
        packet.payloadLen = buf.getShort() & 0xFFFF;
        packet.ssrc = buf.getInt();
        packet.timestamp = buf.getInt();
        packet.sequence = buf.getInt();

        // 提取 payload
        int actualPayloadLen = Math.min(packet.payloadLen, data.length - HEADER_SIZE);
        packet.payload = new byte[actualPayloadLen];
        System.arraycopy(data, HEADER_SIZE, packet.payload, 0, actualPayloadLen);

        return packet;
    }

    /**
     * 获取 16 字节头（同时用作 AES-CTR nonce）
     */
    public byte[] getHeader() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.put(type);
        buf.put(flags);
        buf.putShort((short) payloadLen);
        buf.putInt(ssrc);
        buf.putInt(timestamp);
        buf.putInt(sequence);
        return buf.array();
    }

    /**
     * 构造发送用的数据包（头 + payload）
     *
     * @param type      包类型
     * @param flags     标志位
     * @param ssrc      SSRC
     * @param timestamp 时间戳
     * @param sequence  序列号
     * @param payload   负载数据（已加密）
     * @return 完整的 UDP 数据包字节数组
     */
    public static byte[] build(byte type, byte flags, int ssrc, int timestamp, int sequence, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length).order(ByteOrder.BIG_ENDIAN);
        buf.put(type);
        buf.put(flags);
        buf.putShort((short) payload.length);
        buf.putInt(ssrc);
        buf.putInt(timestamp);
        buf.putInt(sequence);
        buf.put(payload);
        return buf.array();
    }

    // Getters
    public byte getType() { return type; }
    public byte getFlags() { return flags; }
    public int getPayloadLen() { return payloadLen; }
    public int getSsrc() { return ssrc; }
    public int getTimestamp() { return timestamp; }
    public int getSequence() { return sequence; }
    public byte[] getPayload() { return payload; }
}
