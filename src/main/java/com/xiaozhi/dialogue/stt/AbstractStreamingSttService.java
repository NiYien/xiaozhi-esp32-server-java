package com.xiaozhi.dialogue.stt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

import static com.xiaozhi.dialogue.DialogueConstants.QUEUE_POLL_TIMEOUT_MS;
import static com.xiaozhi.dialogue.DialogueConstants.RECOGNITION_TIMEOUT_MS;

/**
 * 流式语音识别服务抽象基类
 * <p>
 * 使用模板方法模式，将 WebSocket 流式识别的公共骨架提取到基类中：
 * 初始化上下文 → 订阅音频流 → 建立连接 → 等待识别结果 → 清理资源
 * <p>
 * 子类只需实现以下抽象方法：
 * <ul>
 *   <li>{@link #isConfigValid()} — 检查 API Key 等配置是否完整</li>
 *   <li>{@link #openConnection(StreamRecognitionContext)} — 建立 WebSocket 连接、设置回调</li>
 *   <li>{@link #cleanup(StreamRecognitionContext)} — 关闭连接、释放资源</li>
 * </ul>
 */
public abstract class AbstractStreamingSttService implements SttService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /**
     * 检查服务配置是否有效（API Key、Secret 等）
     *
     * @return 配置有效返回 true
     */
    protected abstract boolean isConfigValid();

    /**
     * 建立 WebSocket 连接并设置回调
     * <p>
     * 子类在此方法中完成：构建请求、创建 WebSocket、注册消息/错误/关闭回调、
     * 启动音频发送线程。可通过 ctx 获取 audioQueue、设置结果、释放 latch。
     *
     * @param ctx 流式识别上下文
     */
    protected abstract void openConnection(StreamRecognitionContext ctx);

    /**
     * 清理连接和资源
     * <p>
     * 在识别完成或超时后调用，子类应在此关闭 WebSocket 连接。
     *
     * @param ctx 流式识别上下文
     */
    protected abstract void cleanup(StreamRecognitionContext ctx);

    /**
     * 模板方法：流式语音识别
     * <p>
     * 固定的执行流程：配置检查 → 初始化上下文 → 订阅音频流 → 建立连接 → 等待结果 → 清理资源
     *
     * @param audioFlux 音频数据流
     * @return 识别的文本结果
     */
    @Override
    public String streamRecognition(Flux<byte[]> audioFlux) {
        // 1. 配置检查
        if (!isConfigValid()) {
            logger.error("{}语音识别配置未设置，无法进行识别", getProviderName());
            return null;
        }

        // 2. 初始化上下文
        StreamRecognitionContext ctx = new StreamRecognitionContext();

        // 3. 订阅音频流，将数据放入队列
        audioFlux.subscribe(
                data -> ctx.getAudioQueue().offer(data),
                error -> {
                    logger.error("音频流处理错误", error);
                    ctx.markCompleted();
                },
                () -> ctx.markCompleted()
        );

        // 4. 建立连接（子类实现）
        openConnection(ctx);

        try {
            // 5. 等待识别完成或超时
            boolean recognized = ctx.getRecognitionLatch().await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!recognized) {
                logger.warn("{}识别超时", getProviderName());
            }
        } catch (InterruptedException e) {
            logger.error("等待识别结果时被中断", e);
            Thread.currentThread().interrupt();
        } finally {
            // 6. 清理资源（子类实现）
            cleanup(ctx);
        }

        return ctx.getResult();
    }
}
