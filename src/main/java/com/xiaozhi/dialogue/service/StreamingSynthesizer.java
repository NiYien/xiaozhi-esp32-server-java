package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.tts.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式语音合成器，用于支持流式输出的TTS提供商。
 * 将TTS返回的PCM数据流直接传递给Player，跳过文件I/O，降低首次语音响应延迟。
 *
 * 数据流：LLM token流 → DialogueHelper分句 → 逐句调用 textToSpeechStream() → Flux<byte[]> PCM流 → Speech → Player
 */
public class StreamingSynthesizer extends Synthesizer {

    private static final Logger logger = LoggerFactory.getLogger(StreamingSynthesizer.class);

    // 保存LLM输出流的订阅引用，以便在cancel时取消上游订阅
    private volatile Disposable llmDisposable;

    private final TtsService ttsService;

    public StreamingSynthesizer(ChatSession session, TtsService ttsService, Player player) {
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

    /**
     * 将LLM输出的token流转化为语音并推送到播放器。
     * 使用 DialogueHelper 按标点分句，对每个句子调用流式TTS，
     * 将返回的PCM数据流包装为Flux<Speech>交给Player。
     *
     * @param stringFlux LLM输出的token流
     */
    @Override
    public void synthesize(Flux<String> stringFlux) {
        // TTFS profiling: 标记是否已记录首句 TTS 完成时刻
        AtomicBoolean firstTtsRecorded = new AtomicBoolean(false);

        llmDisposable = new DialogueHelper(chatSession).convert(stringFlux).subscribe(text -> {
            // 调用流式TTS，获取PCM数据块流
            Flux<byte[]> pcmStream = ttsService.textToSpeechStream(text);

            // 将PCM数据块流包装为Speech流，第一个块附带文本信息
            AtomicBoolean isFirst = new AtomicBoolean(true);
            // 用 effectively final 变量引用当前句子文本，供 onErrorResume 使用
            final String sentenceText = text;
            Flux<Speech> speechFlux = pcmStream.map(pcmData -> {
                // TTFS profiling: 记录首个PCM数据块到达的时刻
                if (firstTtsRecorded.compareAndSet(false, true)) {
                    chatSession.setTtsFirstCompletedAt(Instant.now());
                }
                // 第一个PCM块附带文本信息
                if (isFirst.compareAndSet(true, false)) {
                    return new Speech(pcmData, sentenceText);
                }
                return new Speech(pcmData);
            }).onErrorResume(e -> {
                // 流式TTS合成某句失败时记录警告并跳过，避免错误传播到Player
                logger.warn("流式TTS合成句子失败，跳过: {}", sentenceText, e);
                return Flux.empty();
            });

            // 交给Player播放（Player内部会排队处理多个Flux）
            player.play(speechFlux);
        }, error -> logger.error("流式合成管道错误", error));
    }

    /**
     * 直接合成单个文本
     * @param text 待合成的文本
     */
    @Override
    public void synthesize(String text) {
        synthesize(Flux.just(text));
    }
}
