package com.xiaozhi.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AudioUtils 单元测试
 * 测试 WAV 头部生成（44 字节正确性）、bytesToShorts/shortsToBytes 互转、saveAsWav 文件写入
 * 跳过需要 FFmpeg 的方法
 */
class AudioUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAsWav_createsValidWavFile() throws IOException {
        // 生成 100 个样本的 PCM 数据（16-bit, 小端序）
        byte[] pcmData = new byte[200]; // 100 samples * 2 bytes
        for (int i = 0; i < 100; i++) {
            short sample = (short) (i * 100);
            pcmData[i * 2] = (byte) (sample & 0xFF);
            pcmData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        Path wavPath = tempDir.resolve("test.wav");
        AudioUtils.saveAsWav(wavPath, pcmData);

        assertTrue(Files.exists(wavPath), "WAV 文件应被创建");

        byte[] wavData = Files.readAllBytes(wavPath);
        // WAV 头部应为 44 字节
        assertEquals(44 + pcmData.length, wavData.length, "WAV 文件大小 = 44 字节头 + PCM 数据");

        // 验证 RIFF 标识
        assertEquals('R', (char) wavData[0]);
        assertEquals('I', (char) wavData[1]);
        assertEquals('F', (char) wavData[2]);
        assertEquals('F', (char) wavData[3]);

        // 验证 WAVE 标识
        assertEquals('W', (char) wavData[8]);
        assertEquals('A', (char) wavData[9]);
        assertEquals('V', (char) wavData[10]);
        assertEquals('E', (char) wavData[11]);

        // 验证 fmt 标识
        assertEquals('f', (char) wavData[12]);
        assertEquals('m', (char) wavData[13]);
        assertEquals('t', (char) wavData[14]);
        assertEquals(' ', (char) wavData[15]);

        // 验证 data 标识
        assertEquals('d', (char) wavData[36]);
        assertEquals('a', (char) wavData[37]);
        assertEquals('t', (char) wavData[38]);
        assertEquals('a', (char) wavData[39]);
    }

    @Test
    void wavToPcm_roundTrip_preservesData() throws IOException {
        byte[] originalPcm = new byte[1920]; // 960 samples
        for (int i = 0; i < originalPcm.length; i++) {
            originalPcm[i] = (byte) (i % 256);
        }

        // PCM → WAV → PCM
        Path wavPath = tempDir.resolve("roundtrip.wav");
        AudioUtils.saveAsWav(wavPath, originalPcm);

        byte[] recoveredPcm = AudioUtils.wavToPcm(wavPath.toString());

        assertArrayEquals(originalPcm, recoveredPcm, "WAV 往返转换应保留原始 PCM 数据");
    }

    @Test
    void wavToPcm_fromBytes_extractsPcmCorrectly() throws IOException {
        byte[] pcmData = {0x01, 0x02, 0x03, 0x04};

        Path wavPath = tempDir.resolve("small.wav");
        AudioUtils.saveAsWav(wavPath, pcmData);

        byte[] wavBytes = Files.readAllBytes(wavPath);
        byte[] extracted = AudioUtils.wavToPcm(wavBytes);

        assertArrayEquals(pcmData, extracted, "从 WAV 字节数组提取的 PCM 应正确");
    }

    @Test
    void wavToPcm_invalidData_throwsException() {
        byte[] invalidData = new byte[10]; // 太短，不是有效 WAV
        assertThrows(IOException.class, () -> AudioUtils.wavToPcm(invalidData));
    }

    @Test
    void wavToPcm_notWavFormat_throwsException() {
        byte[] notWav = new byte[50];
        notWav[0] = 'N'; // 不是 RIFF
        assertThrows(IOException.class, () -> AudioUtils.wavToPcm(notWav));
    }

    @Test
    void resamplePcm_sameRate_returnsOriginal() {
        byte[] pcm = {0x01, 0x02, 0x03, 0x04};
        byte[] result = AudioUtils.resamplePcm(pcm, 16000, 16000);
        assertSame(pcm, result, "相同采样率应返回原数组引用");
    }

    @Test
    void resamplePcm_nullInput_returnsNull() {
        byte[] result = AudioUtils.resamplePcm(null, 16000, 8000);
        assertNull(result);
    }

    @Test
    void resamplePcm_emptyInput_returnsEmpty() {
        byte[] result = AudioUtils.resamplePcm(new byte[0], 16000, 8000);
        assertArrayEquals(new byte[0], result);
    }

    @Test
    void resamplePcm_downsample_reducesLength() {
        // 16000Hz → 8000Hz，样本数应减半
        int samples = 1600;
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            short val = (short) (Math.sin(2 * Math.PI * 440 * i / 16000) * 10000);
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }

        byte[] result = AudioUtils.resamplePcm(pcm, 16000, 8000);

        // 8000Hz 下应约有 800 个样本
        int expectedSamples = (int) Math.ceil((long) samples * 8000 / 16000);
        assertEquals(expectedSamples * 2, result.length, "降采样后字节数应正确");
    }

    @Test
    void floatToPcm16_convertsCorrectly() {
        float[] samples = {0.0f, 1.0f, -1.0f, 0.5f};
        byte[] pcm = AudioUtils.floatToPcm16(samples);

        assertEquals(8, pcm.length, "4 个 float 样本应生成 8 字节");

        ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);

        // 0.0f → 0
        assertEquals(0, buf.getShort(0));
        // 1.0f → 32767
        assertEquals(32767, buf.getShort(2));
        // -1.0f → -32767 (约)
        assertEquals((short) -32767, buf.getShort(4));
    }

    @Test
    void floatToPcm16_clampsOutOfRange() {
        float[] samples = {2.0f, -2.0f};
        byte[] pcm = AudioUtils.floatToPcm16(samples);

        ByteBuffer buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        // 超出范围应被钳制
        assertEquals(32767, buf.getShort(0), "超出 1.0 应钳制到 32767");
        assertEquals((short) -32767, buf.getShort(2), "低于 -1.0 应钳制到 -32767");
    }

    @Test
    @Disabled("需要 FFmpeg 环境，跳过 CI 测试")
    void saveAsMp3_requiresFfmpeg() {
        // 此测试需要本地安装 FFmpeg
        byte[] pcm = new byte[32000]; // 1 秒 16kHz 单声道
        String result = AudioUtils.saveAsMp3(pcm);
        assertNotNull(result);
    }

    @Test
    @Disabled("需要 FFmpeg 环境，跳过 CI 测试")
    void mp3ToPcm_requiresFfmpeg() {
        // 此测试需要本地安装 FFmpeg
    }

    @Test
    void readAsPcm_unsupportedFormat_throwsException() {
        assertThrows(IOException.class, () -> AudioUtils.readAsPcm("test.flac"),
                "不支持的格式应抛出 IOException");
    }
}
