package com.xiaozhi.communication.udp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UDP 音频数据包解析与构建测试")
class UdpAudioPacketTest {

    @Test
    @DisplayName("解析合法数据包 - 各字段正确")
    void parse_validPacket() {
        // 构造已知的 16+4 字节数组
        byte type = 0x01;
        byte flags = 0x02;
        short payloadLen = 4;
        int ssrc = 0x12345678;
        int timestamp = 1000;
        int sequence = 42;
        byte[] payloadData = {0x0A, 0x0B, 0x0C, 0x0D};

        ByteBuffer buf = ByteBuffer.allocate(16 + payloadData.length).order(ByteOrder.BIG_ENDIAN);
        buf.put(type);
        buf.put(flags);
        buf.putShort(payloadLen);
        buf.putInt(ssrc);
        buf.putInt(timestamp);
        buf.putInt(sequence);
        buf.put(payloadData);

        UdpAudioPacket packet = UdpAudioPacket.parse(buf.array());

        assertEquals(type, packet.getType());
        assertEquals(flags, packet.getFlags());
        assertEquals(payloadLen, packet.getPayloadLen());
        assertEquals(ssrc, packet.getSsrc());
        assertEquals(timestamp, packet.getTimestamp());
        assertEquals(sequence, packet.getSequence());
        assertArrayEquals(payloadData, packet.getPayload());
    }

    @Test
    @DisplayName("数据包长度小于 16 字节抛出 IllegalArgumentException")
    void parse_tooShort() {
        byte[] tooShort = new byte[15];
        assertThrows(IllegalArgumentException.class, () -> UdpAudioPacket.parse(tooShort));
    }

    @Test
    @DisplayName("parse 后 getHeader() 得到的 16 字节与原始头一致")
    void getHeader_matchesOriginal() {
        byte[] rawHeader = new byte[16];
        ByteBuffer buf = ByteBuffer.wrap(rawHeader).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 0x01);    // type
        buf.put((byte) 0x00);    // flags
        buf.putShort((short) 8); // payload_len
        buf.putInt(0xAABBCCDD);  // ssrc
        buf.putInt(5000);        // timestamp
        buf.putInt(99);          // sequence

        byte[] payload = new byte[8];
        byte[] fullPacket = new byte[16 + 8];
        System.arraycopy(rawHeader, 0, fullPacket, 0, 16);
        System.arraycopy(payload, 0, fullPacket, 16, 8);

        UdpAudioPacket packet = UdpAudioPacket.parse(fullPacket);
        byte[] header = packet.getHeader();

        assertArrayEquals(rawHeader, header);
    }

    @Test
    @DisplayName("build() 构造数据包再 parse 回来各字段一致")
    void build_producesCorrectFormat() {
        byte type = UdpAudioPacket.TYPE_AUDIO;
        byte flags = 0x03;
        int ssrc = 0x11223344;
        int timestamp = 60000;
        int sequence = 256;
        byte[] payload = {1, 2, 3, 4, 5};

        byte[] built = UdpAudioPacket.build(type, flags, ssrc, timestamp, sequence, payload);
        UdpAudioPacket parsed = UdpAudioPacket.parse(built);

        assertEquals(type, parsed.getType());
        assertEquals(flags, parsed.getFlags());
        assertEquals(payload.length, parsed.getPayloadLen());
        assertEquals(ssrc, parsed.getSsrc());
        assertEquals(timestamp, parsed.getTimestamp());
        assertEquals(sequence, parsed.getSequence());
        assertArrayEquals(payload, parsed.getPayload());
    }

    @Test
    @DisplayName("payload_len、timestamp、sequence 是 big-endian 编码")
    void bigEndian_byteOrder() {
        byte[] payload = new byte[2];
        int ssrc = 0;
        int timestamp = 0x01020304;
        int sequence = 0x05060708;

        byte[] built = UdpAudioPacket.build(UdpAudioPacket.TYPE_AUDIO, (byte) 0, ssrc, timestamp, sequence, payload);

        // payload_len = 2 => big-endian: 0x00, 0x02 (offset 2-3)
        assertEquals((byte) 0x00, built[2]);
        assertEquals((byte) 0x02, built[3]);

        // timestamp at offset 8-11: 0x01, 0x02, 0x03, 0x04
        assertEquals((byte) 0x01, built[8]);
        assertEquals((byte) 0x02, built[9]);
        assertEquals((byte) 0x03, built[10]);
        assertEquals((byte) 0x04, built[11]);

        // sequence at offset 12-15: 0x05, 0x06, 0x07, 0x08
        assertEquals((byte) 0x05, built[12]);
        assertEquals((byte) 0x06, built[13]);
        assertEquals((byte) 0x07, built[14]);
        assertEquals((byte) 0x08, built[15]);
    }

    @Test
    @DisplayName("HEADER_SIZE 常量为 16")
    void headerSize_is16() {
        assertEquals(16, UdpAudioPacket.HEADER_SIZE);
    }

    @Test
    @DisplayName("TYPE_AUDIO 常量为 0x01")
    void typeAudio_is0x01() {
        assertEquals((byte) 0x01, UdpAudioPacket.TYPE_AUDIO);
    }
}
