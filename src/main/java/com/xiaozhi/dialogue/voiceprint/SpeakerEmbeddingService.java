package com.xiaozhi.dialogue.voiceprint;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtLoggingLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * 说话人嵌入向量提取服务
 * 使用ONNX Runtime加载speaker_embedding模型，从PCM音频中提取192维说话人特征向量。
 * 复用与VAD相同的OrtEnvironment单例。模型文件不存在时优雅降级（enabled=false）。
 */
@Component
public class SpeakerEmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerEmbeddingService.class);

    /**
     * 嵌入向量维度：192维float32
     */
    public static final int EMBEDDING_DIM = 192;

    /**
     * 最小语音时长（秒），低于此时长不进行声纹识别
     */
    public static final float MIN_SPEECH_DURATION_SECONDS = 1.5f;

    /**
     * 采样率：16kHz
     */
    private static final int SAMPLE_RATE = 16000;

    /**
     * 最小采样点数 = 采样率 * 最小时长
     */
    private static final int MIN_SAMPLES = (int) (SAMPLE_RATE * MIN_SPEECH_DURATION_SECONDS);

    @Value("${voiceprint.model.path:models/speaker_embedding.onnx}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;
    private volatile boolean enabled = false;

    @PostConstruct
    public void initialize() {
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            logger.warn("声纹识别模型文件不存在: {}，声纹识别功能已禁用", modelPath);
            enabled = false;
            return;
        }

        try {
            // 复用OrtEnvironment单例（与VAD共享）
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            opts.setInterOpNumThreads(1);
            opts.setIntraOpNumThreads(1);
            opts.addCPU(true);

            session = env.createSession(modelPath, opts);
            enabled = true;
            logger.info("说话人嵌入模型初始化成功，嵌入维度: {}", EMBEDDING_DIM);
        } catch (UnsatisfiedLinkError e) {
            logger.error("ONNX Runtime native libraries加载失败: {}", e.getMessage());
            enabled = false;
        } catch (OrtException e) {
            logger.error("说话人嵌入模型初始化失败: {}", e.getMessage());
            enabled = false;
        }
    }

    /**
     * 是否启用声纹识别
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 从PCM音频数据提取说话人嵌入向量
     *
     * @param pcmData 16kHz 16bit单声道PCM数据
     * @return 192维float32嵌入向量，音频过短或提取失败返回null
     */
    public float[] extractEmbedding(byte[] pcmData) {
        if (!enabled || pcmData == null || pcmData.length == 0) {
            return null;
        }

        // 转换为float采样
        float[] samples = pcmToFloats(pcmData);

        // 检查最小语音时长
        if (samples.length < MIN_SAMPLES) {
            // S2: SLF4J 不支持 {:.1f} 格式化语法，使用 String.format 手动格式化浮点数
            logger.debug("音频时长不足 {}秒，跳过声纹提取（需要至少{}秒）",
                    String.format("%.1f", (float) samples.length / SAMPLE_RATE), MIN_SPEECH_DURATION_SECONDS);
            return null;
        }

        try {
            // 模型输入: [1, num_samples]
            float[][] input = new float[][] { samples };

            // S1: 使用 try-with-resources 确保 OnnxTensor 和 OrtSession.Result 正确关闭，避免资源泄漏
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, input);
                 OrtSession.Result result = session.run(Map.of("input", inputTensor))) {
                float[][] output = (float[][]) result.get(0).getValue();

                if (output != null && output.length > 0 && output[0].length == EMBEDDING_DIM) {
                    // L2归一化
                    float[] embedding = output[0];
                    normalizeL2(embedding);
                    return embedding;
                } else {
                    logger.warn("模型输出维度不匹配，期望: {}，实际: {}",
                            EMBEDDING_DIM, output != null && output.length > 0 ? output[0].length : 0);
                    return null;
                }
            }
        } catch (OrtException e) {
            logger.error("声纹嵌入提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算两个嵌入向量的余弦相似度
     *
     * @param embedding1 向量1（已L2归一化）
     * @param embedding2 向量2（已L2归一化）
     * @return 余弦相似度 [-1, 1]
     */
    public static float cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null
                || embedding1.length != EMBEDDING_DIM || embedding2.length != EMBEDDING_DIM) {
            return 0.0f;
        }

        // 已L2归一化的向量，余弦相似度等于点积
        float dotProduct = 0.0f;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dotProduct += embedding1[i] * embedding2[i];
        }
        return dotProduct;
    }

    /**
     * 将嵌入向量序列化为字节数组（用于数据库存储）
     */
    public static byte[] embeddingToBytes(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIM) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(EMBEDDING_DIM * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : embedding) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    /**
     * 从字节数组反序列化为嵌入向量
     */
    public static float[] bytesToEmbedding(byte[] bytes) {
        if (bytes == null || bytes.length != EMBEDDING_DIM * 4) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] embedding = new float[EMBEDDING_DIM];
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            embedding[i] = buffer.getFloat();
        }
        return embedding;
    }

    /**
     * PCM字节数组转换为float采样（16bit有符号小端序）
     */
    private float[] pcmToFloats(byte[] pcmData) {
        int sampleCount = pcmData.length / 2;
        float[] samples = new float[sampleCount];
        ByteBuffer buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            samples[i] = buffer.getShort() / 32768.0f;
        }
        return samples;
    }

    /**
     * L2归一化（原地修改）
     */
    private void normalizeL2(float[] vector) {
        float sumSquares = 0.0f;
        for (float v : vector) {
            sumSquares += v * v;
        }
        float norm = (float) Math.sqrt(sumSquares);
        if (norm > 1e-10f) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            enabled = false;
            logger.info("说话人嵌入模型资源已释放");
        } catch (OrtException e) {
            logger.error("关闭说话人嵌入模型失败: {}", e.getMessage());
        }
    }
}
