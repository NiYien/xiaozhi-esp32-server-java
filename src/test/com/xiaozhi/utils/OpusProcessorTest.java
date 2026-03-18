package com.xiaozhi.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpusProcessor 单元测试
 * 测试编解码往返、无效数据处理、空输入处理
 * 如果 Opus 本地库（Concentus Java 实现）加载失败，标记跳过
 */
class OpusProcessorTest {

    private OpusProcessor processor;

    /**
     * 检测 Opus 编解码器是否可用
     */
    static boolean isOpusUnavailable() {
        try {
            new OpusProcessor();
            return false;
        } catch (Exception | Error e) {
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        processor = new OpusProcessor();
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void pcmToOpus_andBack_roundTrip() throws Exception {
        // 生成 960 个样本（一帧）的 PCM 数据 - 正弦波 440Hz
        int samples = AudioUtils.FRAME_SIZE;
        byte[] pcm = generateSineWave(samples, 440);

        // 编码
        List<byte[]> opusFrames = processor.pcmToOpus(pcm, false);
        assertFalse(opusFrames.isEmpty(), "应产生至少一个 Opus 帧");

        // 解码
        OpusProcessor decoder = new OpusProcessor();
        byte[] decoded = decoder.opusToPcm(opusFrames.get(0));
        assertNotNull(decoded);
        assertTrue(decoded.length > 0, "解码后的 PCM 应有数据");

        // Opus 有损编码，不期望完全相同，但长度应匹配
        assertEquals(samples * 2, decoded.length, "解码后的 PCM 字节数应与原始帧大小一致");
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void pcmToOpus_multipleFrames() {
        // 生成 3 帧的 PCM 数据
        int samples = AudioUtils.FRAME_SIZE * 3;
        byte[] pcm = generateSineWave(samples, 440);

        List<byte[]> opusFrames = processor.pcmToOpus(pcm, false);
        assertEquals(3, opusFrames.size(), "3 帧的 PCM 应编码为 3 个 Opus 帧");
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void pcmToOpus_emptyInput_returnsEmptyList() {
        List<byte[]> result = processor.pcmToOpus(new byte[0], false);
        assertTrue(result.isEmpty(), "空输入应返回空列表");
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void pcmToOpus_nullInput_returnsEmptyList() {
        List<byte[]> result = processor.pcmToOpus(null, false);
        assertTrue(result.isEmpty(), "null 输入应返回空列表");
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void opusToPcm_invalidData_doesNotCrash() throws Exception {
        // 畸形 Opus 帧 — Concentus 解码器可能容忍部分畸形数据
        // 验证不会崩溃，且返回非 null 结果
        byte[] invalidOpus = {0x01, 0x02, 0x03, 0x04, 0x05};
        byte[] result = processor.opusToPcm(invalidOpus);
        assertNotNull(result, "无效数据解码不应返回 null");
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void opusToPcm_emptyInput_returnsEmptyArray() throws Exception {
        byte[] result = processor.opusToPcm(new byte[0]);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void opusToPcm_nullInput_returnsEmptyArray() throws Exception {
        byte[] result = processor.opusToPcm(null);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void pcmToOpus_streamMode_handlesLeftover() {
        // 发送不完整帧（少于 FRAME_SIZE 个样本）
        int partialSamples = AudioUtils.FRAME_SIZE / 2; // 半帧
        byte[] pcm = generateSineWave(partialSamples, 440);

        List<byte[]> frames = processor.pcmToOpus(pcm, true);
        // 半帧数据在流模式下应被缓存，不产生输出帧
        assertTrue(frames.isEmpty(), "不足一帧时流模式应缓存，不输出");

        // 发送另外半帧 + 额外数据以凑满一帧
        byte[] more = generateSineWave(partialSamples, 440);
        List<byte[]> frames2 = processor.pcmToOpus(more, true);
        // 现在应该有一帧了
        assertEquals(1, frames2.size(), "凑满一帧后应输出一个 Opus 帧");
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void flushLeftover_withPendingData_producesFrame() {
        // 发送不完整帧
        int partialSamples = 100;
        byte[] pcm = generateSineWave(partialSamples, 440);

        processor.pcmToOpus(pcm, true);

        // 刷新残留数据
        List<byte[]> flushed = processor.flushLeftover();
        assertEquals(1, flushed.size(), "flushLeftover 应产生最后一帧");
    }

    @Test
    @DisabledIf("isOpusUnavailable")
    void flushLeftover_noPendingData_returnsEmpty() {
        List<byte[]> flushed = processor.flushLeftover();
        assertTrue(flushed.isEmpty(), "无残留数据时应返回空列表");
    }

    /**
     * 生成正弦波 PCM 数据（16-bit 小端序）
     */
    private byte[] generateSineWave(int samples, double frequency) {
        byte[] pcm = new byte[samples * 2];
        ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            short sample = (short) (Math.sin(2 * Math.PI * frequency * i / AudioUtils.SAMPLE_RATE) * 10000);
            buf.putShort(sample);
        }
        return pcm;
    }
}
