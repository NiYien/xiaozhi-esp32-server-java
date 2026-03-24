package com.xiaozhi.dialogue.tts.clone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.utils.HttpUtil;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

/**
 * 火山引擎音色克隆服务实现
 * 调用火山引擎声音复刻 V3 API 进行音色训练
 * 文档：https://www.volcengine.com/docs/6561/2227958
 */
@Service
public class VolcengineVoiceCloneService implements VoiceCloneService {

    private static final Logger logger = LoggerFactory.getLogger(VolcengineVoiceCloneService.class);

    private static final String PROVIDER_NAME = "volcengine";

    // 声音复刻 V3 API 地址
    private static final String CLONE_API_URL = "https://openspeech.bytedance.com/api/v3/tts/voice_clone";
    private static final String STATUS_API_URL = "https://openspeech.bytedance.com/api/v3/tts/get_voice";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client = HttpUtil.client;

    @Resource
    private SysConfigService configService;

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String submitCloneTask(String samplePath, String cloneName, int configId) {
        SysConfig config = configService.selectConfigById(configId);
        if (config == null) {
            throw new RuntimeException("未找到TTS配置，configId=" + configId);
        }

        try {
            // 读取音频文件并转为Base64
            byte[] audioBytes = Files.readAllBytes(Path.of(samplePath));
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

            // speaker_id 由前端传入（从火山引擎控制台获取）
            String speakerId = cloneName;

            // 构建 V3 请求参数
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("speaker_id", speakerId);
            requestJson.addProperty("language", 0);

            // 音频数据（V3 格式：audio 对象，字段名为 data 和 format）
            JsonObject audio = new JsonObject();
            audio.addProperty("data", audioBase64);
            audio.addProperty("format", samplePath.endsWith(".mp3") ? "mp3" : "wav");
            requestJson.add("audio", audio);

            logger.info("[火山引擎] 提交音色克隆(V3): appId={}, speakerId={}", config.getAppId(), speakerId);

            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            // V3 认证方式：X-Api-App-Key + X-Api-Access-Key
            Request request = new Request.Builder()
                    .url(CLONE_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Api-App-Key", config.getAppId())
                    .addHeader("X-Api-Access-Key", config.getApiKey())
                    .addHeader("X-Api-Request-Id", UUID.randomUUID().toString())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "无响应体";
                logger.info("[火山引擎] 音色克隆响应: code={}, body={}", response.code(), responseBody);
                if (!response.isSuccessful()) {
                    throw new RuntimeException("提交克隆任务失败: " + response.code() + " " + responseBody);
                }

                // 返回 speakerId 作为任务标识
                return speakerId;
            }
        } catch (IOException e) {
            logger.error("[火山引擎] 提交音色克隆任务失败", e);
            throw new RuntimeException("提交音色克隆任务失败: " + e.getMessage(), e);
        }
    }

    @Override
    public VoiceCloneStatus queryStatus(String taskId, int configId) {
        SysConfig config = configService.selectConfigById(configId);
        if (config == null) {
            return VoiceCloneStatus.FAILED;
        }

        try {
            // V3 状态查询只需 speaker_id
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("speaker_id", taskId);

            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(STATUS_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Api-App-Key", config.getAppId())
                    .addHeader("X-Api-Access-Key", config.getApiKey())
                    .addHeader("X-Api-Request-Id", UUID.randomUUID().toString())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("[火山引擎] 查询克隆任务状态失败: {}", response.code());
                    return VoiceCloneStatus.TRAINING;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                // V3 status: 0=NotFound, 1=Training, 2=Success, 3=Failed, 4=Active
                if (jsonResponse.has("status")) {
                    int status = jsonResponse.get("status").getAsInt();
                    return switch (status) {
                        case 2, 4 -> VoiceCloneStatus.READY;
                        case 3 -> VoiceCloneStatus.FAILED;
                        default -> VoiceCloneStatus.TRAINING;
                    };
                }
                return VoiceCloneStatus.TRAINING;
            }
        } catch (IOException e) {
            logger.error("[火山引擎] 查询克隆任务状态异常", e);
            return VoiceCloneStatus.TRAINING;
        }
    }

    @Override
    public String getVoiceId(String taskId, int configId) {
        // taskId 即 speakerId，训练完成后直接用于 TTS 调用
        return taskId;
    }

    @Override
    public void deleteVoice(String voiceId, int configId) {
        // 火山引擎声音复刻目前不提供删除API
        logger.info("[火山引擎] 删除克隆音色: voiceId={}, configId={}", voiceId, configId);
    }
}
