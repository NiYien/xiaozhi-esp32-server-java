package com.xiaozhi.dialogue.stt.contract;

import com.xiaozhi.dialogue.stt.SttService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STT 行为契约测试
 * <p>
 * 定义所有 SttService 实现必须满足的行为契约，
 * Phase 1 提取基类时各提供商测试应继承此类。
 * </p>
 * <p>
 * 子类需实现 {@link #createService()} 提供具体实现实例。
 * 默认提供一个 stub 实现用于验证契约结构本身。
 * </p>
 */
abstract class SttBehaviorContractTest {

    /**
     * 子类提供待测的 SttService 实现
     */
    protected abstract SttService createService();

    // ==================== streamRecognition 成功路径 ====================

    @Test
    void streamRecognition_withValidAudio_returnsText() {
        SttService service = createService();
        if (!service.supportsStreaming()) {
            return; // 非流式服务跳过
        }

        // 构造一个简单的音频数据流
        byte[] fakeAudioChunk = new byte[]{0x01, 0x02, 0x03, 0x04};
        Flux<byte[]> audioStream = Flux.just(fakeAudioChunk, fakeAudioChunk)
                .delayElements(Duration.ofMillis(10));

        String result = service.streamRecognition(audioStream);

        // 契约：返回结果不能为 null
        assertNotNull(result, "streamRecognition 不应返回 null");
    }

    // ==================== streamRecognition 超时处理 ====================

    @Test
    void streamRecognition_timeout_completesWithinReasonableTime() throws Exception {
        SttService service = createService();
        if (!service.supportsStreaming()) {
            return;
        }

        // 构造一个永远不完成的流，模拟超时场景
        Sinks.Many<byte[]> sink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<byte[]> neverEndingStream = sink.asFlux();

        // 服务应该有内置超时机制（通常 90 秒），不应永远阻塞
        // 用 ExecutorService 验证不会无限阻塞
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = executor.submit(() -> service.streamRecognition(neverEndingStream));

            // 契约：超时后应返回结果（空字符串或部分结果），不超过 95 秒
            String result = future.get(95, TimeUnit.SECONDS);
            assertNotNull(result, "超时后应返回非 null 结果");
        } catch (TimeoutException e) {
            fail("streamRecognition 超过 95 秒未返回，违反超时契约");
        } finally {
            sink.tryEmitComplete();
            executor.shutdownNow();
        }
    }

    // ==================== streamRecognition 错误恢复 ====================

    @Test
    void streamRecognition_withErrorStream_doesNotThrow() {
        SttService service = createService();
        if (!service.supportsStreaming()) {
            return;
        }

        // 构造一个发送数据后抛出错误的流
        Flux<byte[]> errorStream = Flux.concat(
                Flux.just(new byte[]{0x01, 0x02}),
                Flux.error(new RuntimeException("模拟 WebSocket 断开"))
        );

        // 契约：不应抛出异常，应优雅处理错误
        String result = assertDoesNotThrow(
                () -> service.streamRecognition(errorStream),
                "streamRecognition 遇到错误流时不应崩溃");

        assertNotNull(result, "错误恢复后应返回非 null 结果");
    }

    // ==================== streamRecognition 空音频 ====================

    @Test
    void streamRecognition_emptyAudio_returnsEmptyString() {
        SttService service = createService();
        if (!service.supportsStreaming()) {
            return;
        }

        // 空流：立即完成
        Flux<byte[]> emptyStream = Flux.empty();

        String result = service.streamRecognition(emptyStream);

        // 契约：空音频应返回空字符串
        assertNotNull(result, "空音频不应返回 null");
        assertTrue(result.isEmpty(), "空音频应返回空字符串，实际返回: " + result);
    }
}

/**
 * Stub 实现验证契约测试结构本身可运行
 */
class StubSttBehaviorContractTest extends SttBehaviorContractTest {

    @Override
    protected SttService createService() {
        return new SttService() {
            @Override
            public String getProviderName() {
                return "stub";
            }

            @Override
            public String recognition(byte[] audioData) {
                return "stub-text";
            }

            @Override
            public String streamRecognition(Flux<byte[]> audioSink) {
                // 消费流并返回结果，内置超时机制
                StringBuilder sb = new StringBuilder();
                try {
                    audioSink.doOnNext(chunk -> sb.append("x"))
                            .doOnError(e -> { /* 优雅忽略 */ })
                            .onErrorReturn(new byte[0])
                            .timeout(Duration.ofSeconds(3))
                            .onErrorComplete()
                            .blockLast(Duration.ofSeconds(5));
                } catch (Exception e) {
                    // 超时或其他异常 → 返回已收集的结果
                }
                return sb.toString();
            }

            @Override
            public boolean supportsStreaming() {
                return true;
            }
        };
    }
}
