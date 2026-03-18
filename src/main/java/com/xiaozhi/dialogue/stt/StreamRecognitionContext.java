package com.xiaozhi.dialogue.stt;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式语音识别上下文
 * 封装流式识别过程中的同步原语，避免子类直接操作分散的并发组件
 */
public class StreamRecognitionContext {

    /** 识别完成闭锁 */
    private final CountDownLatch recognitionLatch = new CountDownLatch(1);

    /** 音频流是否已完成（Flux onComplete/onError 后置为 true） */
    private final AtomicBoolean isCompleted = new AtomicBoolean(false);

    /** 闭锁是否已释放（保护 countDown 只执行一次） */
    private final AtomicBoolean latchReleased = new AtomicBoolean(false);

    /** 最终识别结果 */
    private final AtomicReference<String> finalResult = new AtomicReference<>("");

    /** 音频数据缓冲队列 */
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();

    /**
     * 安全释放闭锁（仅首次调用生效）
     */
    public void releaseLatch() {
        if (latchReleased.compareAndSet(false, true)) {
            recognitionLatch.countDown();
        }
    }

    /**
     * 设置最终识别结果
     *
     * @param result 识别文本
     */
    public void setResult(String result) {
        finalResult.set(result);
    }

    /**
     * 获取最终识别结果
     *
     * @return 识别文本
     */
    public String getResult() {
        return finalResult.get();
    }

    /**
     * 音频流是否已完成
     */
    public boolean isCompleted() {
        return isCompleted.get();
    }

    /**
     * 标记音频流已完成
     */
    public void markCompleted() {
        isCompleted.set(true);
    }

    /**
     * 获取识别完成闭锁
     */
    public CountDownLatch getRecognitionLatch() {
        return recognitionLatch;
    }

    /**
     * 获取音频数据缓冲队列
     */
    public BlockingQueue<byte[]> getAudioQueue() {
        return audioQueue;
    }
}
