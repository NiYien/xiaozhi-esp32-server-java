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
 * 调用火山引擎音色克隆 REST API 进行音色训练
 */
@Service
public class VolcengineVoiceCloneService implements VoiceCloneService {

    private static final Logger logger = LoggerFactory.getLogger(VolcengineVoiceCloneService.class);

    private static final String PROVIDER_NAME = "volcengine";

    // 火山引擎音色克隆API地址
    private static final String CLONE_API_URL = "https://openspeech.bytedance.com/api/v1/mega_tts/audio/upload";
    private static final String STATUS_API_URL = "https://openspeech.bytedance.com/api/v1/mega_tts/status";
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

            JsonObject app = new JsonObject();
            app.addProperty("appid", config.getAppId());
            app.addProperty("token", config.getApiKey());
            app.addProperty("cluster", "volcano_mega");
            requestJson.add("app", app);

            JsonObject user = new JsonObject();
            user.addProperty("uid", UUID.randomUUID().toString());
            requestJson.add("user", user);

            JsonObject audio = new JsonObject();
            audio.addProperty("audio_data", audioBase64);
            audio.addProperty("audio_format", samplePath.endsWith(".mp3") ? "mp3" : "wav");
            audio.addProperty("speaker_id", cloneName);
            requestJson.add("audio", audio);

            JsonObject request_obj = new JsonObject();
            String taskId = UUID.randomUUID().toString();
            request_obj.addProperty("reqid", taskId);
            request_obj.addProperty("operation", "submit");
            requestJson.add("request", request_obj);

            String bearerToken = "Bearer " + config.getApiKey();
            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(CLONE_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", bearerToken)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    throw new RuntimeException("提交克隆任务失败: " + response.code() + " " + errorBody);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                // 检查是否有taskId返回
                if (jsonResponse.has("task_id")) {
                    return jsonResponse.get("task_id").getAsString();
                }

                // 使用请求中的reqid作为taskId
                return taskId;
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
            JsonObject requestJson = new JsonObject();

            JsonObject app = new JsonObject();
            app.addProperty("appid", config.getAppId());
            app.addProperty("token", config.getApiKey());
            app.addProperty("cluster", "volcano_mega");
            requestJson.add("app", app);

            JsonObject request_obj = new JsonObject();
            request_obj.addProperty("reqid", UUID.randomUUID().toString());
            request_obj.addProperty("task_id", taskId);
            request_obj.addProperty("operation", "query");
            requestJson.add("request", request_obj);

            String bearerToken = "Bearer " + config.getApiKey();
            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(STATUS_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", bearerToken)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warn("[火山引擎] 查询克隆任务状态失败: {}", response.code());
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
            logger.error("[火山引擎] 查询克隆任务状态异常", e);
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

            JsonObject app = new JsonObject();
            app.addProperty("appid", config.getAppId());
            app.addProperty("token", config.getApiKey());
            app.addProperty("cluster", "volcano_mega");
            requestJson.add("app", app);

            JsonObject request_obj = new JsonObject();
            request_obj.addProperty("reqid", UUID.randomUUID().toString());
            request_obj.addProperty("task_id", taskId);
            request_obj.addProperty("operation", "query");
            requestJson.add("request", request_obj);

            String bearerToken = "Bearer " + config.getApiKey();
            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(STATUS_API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", bearerToken)
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
                // 如果没有专门的 voice_id 字段，使用 speaker_id
                if (jsonResponse.has("speaker_id")) {
                    return jsonResponse.get("speaker_id").getAsString();
                }
                return taskId;
            }
        } catch (IOException e) {
            logger.error("[火山引擎] 获取克隆音色ID失败", e);
            return null;
        }
    }

    @Override
    public void deleteVoice(String voiceId, int configId) {
        // 火山引擎音色克隆目前不提供删除API，记录日志即可
        logger.info("[火山引擎] 删除克隆音色: voiceId={}, configId={}", voiceId, configId);
    }
}
