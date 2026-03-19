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
 * 阿里云 NLS 音色克隆服务实现
 * 调用阿里云 NLS 音色克隆 API 进行音色训练
 */
@Service
public class AliyunVoiceCloneService implements VoiceCloneService {

    private static final Logger logger = LoggerFactory.getLogger(AliyunVoiceCloneService.class);

    private static final String PROVIDER_NAME = "aliyun-nls";

    // 阿里云NLS音色克隆API地址
    private static final String CLONE_API_URL = "https://nls-gateway.aliyuncs.com/rest/v1/voice/clone";
    private static final String STATUS_API_URL = "https://nls-gateway.aliyuncs.com/rest/v1/voice/clone/status";
    private static final String DELETE_API_URL = "https://nls-gateway.aliyuncs.com/rest/v1/voice/clone/delete";
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

            // 构建请求参数
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("appkey", config.getAppId());
            requestJson.addProperty("voice_name", cloneName);
            requestJson.addProperty("audio_data", audioBase64);
            requestJson.addProperty("audio_format", samplePath.endsWith(".mp3") ? "mp3" : "wav");

            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(CLONE_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-NLS-Token", config.getApiKey())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    throw new RuntimeException("提交克隆任务失败: " + response.code() + " " + errorBody);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (jsonResponse.has("task_id")) {
                    return jsonResponse.get("task_id").getAsString();
                }
                // 返回自生成的taskId
                return UUID.randomUUID().toString();
            }
        } catch (IOException e) {
            logger.error("[阿里云NLS] 提交音色克隆任务失败", e);
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
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("appkey", config.getAppId());
            requestJson.addProperty("task_id", taskId);

            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(STATUS_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-NLS-Token", config.getApiKey())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("[阿里云NLS] 查询克隆任务状态失败: {}", response.code());
                    return VoiceCloneStatus.TRAINING;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (jsonResponse.has("status")) {
                    String status = jsonResponse.get("status").getAsString();
                    return switch (status) {
                        case "success", "ready", "completed" -> VoiceCloneStatus.READY;
                        case "failed", "error" -> VoiceCloneStatus.FAILED;
                        default -> VoiceCloneStatus.TRAINING;
                    };
                }
                return VoiceCloneStatus.TRAINING;
            }
        } catch (IOException e) {
            logger.error("[阿里云NLS] 查询克隆任务状态异常", e);
            return VoiceCloneStatus.TRAINING;
        }
    }

    @Override
    public String getVoiceId(String taskId, int configId) {
        SysConfig config = configService.selectConfigById(configId);
        if (config == null) {
            return null;
        }

        try {
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("appkey", config.getAppId());
            requestJson.addProperty("task_id", taskId);

            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(STATUS_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-NLS-Token", config.getApiKey())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }
                String responseBody = response.body() != null ? response.body().string() : "";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                if (jsonResponse.has("voice_id")) {
                    return jsonResponse.get("voice_id").getAsString();
                }
                return taskId;
            }
        } catch (IOException e) {
            logger.error("[阿里云NLS] 获取克隆音色ID失败", e);
            return null;
        }
    }

    @Override
    public void deleteVoice(String voiceId, int configId) {
        SysConfig config = configService.selectConfigById(configId);
        if (config == null) {
            logger.warn("[阿里云NLS] 删除音色时未找到配置，configId={}", configId);
            return;
        }

        try {
            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("appkey", config.getAppId());
            requestJson.addProperty("voice_id", voiceId);

            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(DELETE_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-NLS-Token", config.getApiKey())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("[阿里云NLS] 删除克隆音色成功: voiceId={}", voiceId);
                } else {
                    logger.warn("[阿里云NLS] 删除克隆音色失败: voiceId={}, status={}", voiceId, response.code());
                }
            }
        } catch (IOException e) {
            logger.error("[阿里云NLS] 删除克隆音色异常: voiceId={}", voiceId, e);
        }
    }
}
