package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * 语音合成器，用于非流式TTS（先生成完整音频文件再播放）。
 * 适用于不支持流式输出的TTS Provider
 *
 * 数据流：LLM token流 → DialogueHelper分句 → 逐句调用TTS生成完整音频文件 → 读取PCM → 交给播放器播放
 */
public class FileSynthesizer extends Synthesizer {

    private static final Logger logger = LoggerFactory.getLogger(FileSynthesizer.class);

    // 保存LLM输出流的订阅引用，以便在cancel时取消上游订阅
    private volatile Disposable llmDisposable;

    private final TtsService ttsService;

    public FileSynthesizer(ChatSession session, TtsService ttsService, Player player) {
        super(session, player);
        this.ttsService = ttsService;
    }

    @Override
    public void cancel() {
        if (llmDisposable != null && !llmDisposable.isDisposed()) {
            llmDisposable.dispose();
        }
    }

    @Override
    public boolean isActive() {
        return llmDisposable != null && !llmDisposable.isDisposed();
    }

    // TTS 预提交滑动窗口大小：最多同时 2 个 TTS 请求
    private static final int TTS_PREFETCH_WINDOW = 2;

    /**
     * 将LLM输出的token流转化为语音并推送到播放器。
     * 使用 DialogueHelper 按标点分句，采用有限并行预提交策略（2-size 滑动窗口）：
     * 当第 N 句正在 TTS 合成时，第 N+1 句也同时提交 TTS，合成结果按原始顺序交给 Player。
     *
     * @param stringFlux LLM输出的token流
     */
    @Override
    public void synthesize(Flux<String> stringFlux) {
        // 信号量控制并行 TTS 请求数量（滑动窗口）
        Semaphore ttsSemaphore = new Semaphore(TTS_PREFETCH_WINDOW);

        llmDisposable = new DialogueHelper().convert(stringFlux).subscribe(text -> {
            try {
                // 获取信号量，限制并行 TTS 请求数
                ttsSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // 在虚拟线程中异步提交 TTS 合成
            CompletableFuture<Speech> ttsFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    String audioPath = ttsService.textToSpeech(text);
                    if (audioPath != null) {
                        byte[] audioData = AudioUtils.readAsPcm(audioPath);
                        return new Speech(audioData, text);
                    } else {
                        logger.error("TTS服务返回空音频文件 - SessionId: {}", chatSession.getSessionId());
                        return null;
                    }
                } catch (Exception e) {
                    logger.error("TTS合成出错: {} - SessionId: {}", e.getMessage(), chatSession.getSessionId());
                    return null;
                }
            }, runnable -> Thread.startVirtualThread(runnable));

            // 将 Future 包装为 Flux，按顺序交给 Player（play 内部会排队）
            Flux<Speech> lazyTtsFlux = Flux.create(sink -> {
                try {
                    Speech speech = ttsFuture.join();
                    if (speech != null) {
                        sink.next(speech);
                    }
                } catch (Exception e) {
                    logger.error("等待TTS合成结果出错: {} - SessionId: {}", e.getMessage(), chatSession.getSessionId());
                } finally {
                    // 释放信号量，允许下一句 TTS 提交
                    ttsSemaphore.release();
                    sink.complete();
                }
            });
            player.play(lazyTtsFlux);
        });
    }

    /**
     * 直接合成单个文本
     * @param text 待合成的文本
     */
    @Override
    public void synthesize(String text){
        synthesize(Flux.just(text));
    }

}
