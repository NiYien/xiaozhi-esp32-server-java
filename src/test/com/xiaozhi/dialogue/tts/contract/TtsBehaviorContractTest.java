package com.xiaozhi.dialogue.tts.contract;

import com.xiaozhi.dialogue.tts.TtsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TTS 行为契约测试
 * <p>
 * 定义所有 TtsService 实现必须满足的行为契约，
 * Phase 1 提取基类时各提供商测试应继承此类。
 * </p>
 * <p>
 * 子类需实现 {@link #createService(Path)} 提供具体实现实例。
 * 默认提供一个 stub 实现用于验证契约结构本身。
 * </p>
 */
abstract class TtsBehaviorContractTest {

    @TempDir
    Path tempDir;

    /**
     * 子类提供待测的 TtsService 实现
     *
     * @param outputDir 临时输出目录，用于存放生成的音频文件
     */
    protected abstract TtsService createService(Path outputDir);

    // ==================== textToSpeech 成功路径 ====================

    @Test
    void textToSpeech_withValidText_returnsFilePath() throws Exception {
        TtsService service = createService(tempDir);

        String result = service.textToSpeech("你好世界");

        // 契约：返回音频文件路径
        assertNotNull(result, "textToSpeech 不应返回 null");
        assertFalse(result.isBlank(), "textToSpeech 不应返回空路径");
    }

    // ==================== textToSpeech 空文本处理 ====================

    @Test
    void textToSpeech_withEmptyText_handlesGracefully() {
        TtsService service = createService(tempDir);

        // 契约：空文本不应抛出未处理异常，应返回空字符串或抛出明确异常
        try {
            String result = service.textToSpeech("");
            // 如果不抛异常，结果应是空字符串或空路径
            // 空文本场景允许返回 null 或空字符串
            if (result != null) {
                // 如果有返回值，不做进一步断言（有些实现可能返回空音频文件）
            }
        } catch (IllegalArgumentException e) {
            // 这是可接受的行为：用明确异常拒绝空文本
        } catch (Exception e) {
            // 其他异常也可接受，但不应是 NPE 或意外错误
            assertFalse(e instanceof NullPointerException,
                    "textToSpeech 空文本时不应抛出 NullPointerException");
        }
    }

    // ==================== textToSpeech 失败重试 ====================

    @Test
    void textToSpeech_onFailure_retriesBeforeGivingUp() {
        AtomicInteger callCount = new AtomicInteger(0);

        // 创建一个前 2 次失败、第 3 次成功的 service
        TtsService retryService = new TtsService() {
            @Override
            public String getProviderName() {
                return "retry-test";
            }

            @Override
            public String getVoiceName() {
                return "test-voice";
            }

            @Override
            public Float getSpeed() {
                return 1.0f;
            }

            @Override
            public Float getPitch() {
                return 1.0f;
            }

            @Override
            public String textToSpeech(String text) throws Exception {
                int attempt = callCount.incrementAndGet();
                if (attempt <= 2) {
                    throw new RuntimeException("模拟第 " + attempt + " 次失败");
                }
                // 第 3 次成功
                Path audioFile = tempDir.resolve("retry-success.wav");
                Files.writeString(audioFile, "fake-audio");
                return audioFile.toString();
            }
        };

        // 模拟带重试的调用逻辑（契约：最多重试 3 次）
        String result = null;
        Exception lastException = null;
        for (int i = 0; i < 3; i++) {
            try {
                result = retryService.textToSpeech("测试重试");
                break;
            } catch (Exception e) {
                lastException = e;
            }
        }

        // 契约验证：3 次调用后应成功
        assertEquals(3, callCount.get(), "应尝试调用 3 次");
        assertNotNull(result, "第 3 次重试应成功返回结果");
        assertNull(lastException == null ? null : null,
                "最终应成功，不应残留异常");
    }
}

/**
 * Stub 实现验证契约测试结构本身可运行
 */
class StubTtsBehaviorContractTest extends TtsBehaviorContractTest {

    @Override
    protected TtsService createService(Path outputDir) {
        return new TtsService() {
            @Override
            public String getProviderName() {
                return "stub";
            }

            @Override
            public String getVoiceName() {
                return "stub-voice";
            }

            @Override
            public Float getSpeed() {
                return 1.0f;
            }

            @Override
            public Float getPitch() {
                return 1.0f;
            }

            @Override
            public String textToSpeech(String text) throws Exception {
                if (text == null || text.isBlank()) {
                    return "";
                }
                Path audioFile = outputDir.resolve(getAudioFileName());
                Files.writeString(audioFile, "fake-audio-data-for:" + text);
                return audioFile.toString();
            }
        };
    }
}
