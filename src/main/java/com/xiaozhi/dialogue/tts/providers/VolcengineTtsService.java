package com.xiaozhi.dialogue.tts.providers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaozhi.common.exception.TtsException;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.HttpUtil;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class VolcengineTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(VolcengineTtsService.class);

    private static final String PROVIDER_NAME = "volcengine";
    private static final String API_URL = "https://openspeech.bytedance.com/api/v1/tts";
    private static final String API_URL_V3 = "https://openspeech.bytedance.com/api/v3/tts/unidirectional";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 重试机制常量
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;


    // 音频名称
    private String voiceName;

    // 音频输出路径
    private String outputPath;

    // API相关
    private String appId;
    private String accessToken; // 对应 apiKey
    // 从 configName 读取的集群/模型参数
    private String cluster;

    // 语音参数
    private Float pitch;
    private Float speed;

    private final OkHttpClient client = HttpUtil.client;

    public VolcengineTtsService(SysConfig config, String voiceName, Float pitch, Float speed, String outputPath) {
        this.voiceName = voiceName;
        this.pitch = pitch;
        this.speed = speed;
        this.outputPath = outputPath;
        this.appId = config.getAppId();
        this.accessToken = config.getApiKey();
        // 从 configName 读取集群/模型参数，为空时默认 volcano_tts
        String configName = config.getConfigName();
        this.cluster = (configName != null && !configName.isEmpty()) ? configName : "volcano_tts";
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String getVoiceName() {
        return voiceName;
    }

    @Override
    public Float getSpeed() {
        return speed;
    }

    @Override
    public Float getPitch() {
        return pitch;
    }

    @Override
    public String textToSpeech(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            logger.warn("文本内容为空！");
            return null;
        }

        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                // 生成音频文件名（克隆音色使用 mp3 格式）
                String audioFileName;
                if (voiceName != null && voiceName.startsWith("S_")) {
                    audioFileName = UUID.randomUUID().toString().replace("-", "") + ".mp3";
                } else {
                    audioFileName = getAudioFileName();
                }
                String audioFilePath = outputPath + audioFileName;

                // 发送POST请求
                boolean success = sendRequest(text, audioFilePath);

                if (success) {
                    return audioFilePath;
                } else {
                    throw new Exception("语音合成失败");
                }
            } catch (Exception e) {
                attempts++;
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    logger.warn("火山语音合成失败，正在重试 ({}/{}): {}", attempts, MAX_RETRY_ATTEMPTS, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("重试等待被中断", ie);
                        throw e;
                    }
                } else {
                    logger.error("火山语音合成失败，已达到最大重试次数", e);
                    throw e;
                }
            }
        }
        throw new Exception("语音合成失败");
    }

    /**
     * 发送POST请求到火山引擎API，获取语音合成结果
     * 克隆音色（S_开头）使用V3 API，普通音色使用V1 API
     */
    private boolean sendRequest(String text, String audioFilePath) throws Exception {
        if (voiceName != null && voiceName.startsWith("S_")) {
            return sendRequestV3(text, audioFilePath);
        }
        return sendRequestV1(text, audioFilePath);
    }

    /**
     * V1 API：普通音色合成
     */
    private boolean sendRequestV1(String text, String audioFilePath) throws Exception {
        try {
            // 构建请求参数
            JsonObject requestJson = new JsonObject();

            // app部分
            JsonObject app = new JsonObject();
            app.addProperty("appid", appId);
            app.addProperty("token", accessToken);
            app.addProperty("cluster", cluster);
            requestJson.add("app", app);

            // user部分
            JsonObject user = new JsonObject();
            user.addProperty("uid", UUID.randomUUID().toString());
            requestJson.add("user", user);

            // audio部分
            JsonObject audio = new JsonObject();
            audio.addProperty("voice_type", voiceName);
            audio.addProperty("encoding", "wav");
            audio.addProperty("speed_ratio", speed);
            audio.addProperty("volume_ratio", 1.0);
            audio.addProperty("pitch_ratio", pitch);
            audio.addProperty("rate", AudioUtils.SAMPLE_RATE);
            requestJson.add("audio", audio);

            // request部分
            JsonObject request_JsonObject = new JsonObject();
            request_JsonObject.addProperty("reqid", UUID.randomUUID().toString());
            request_JsonObject.addProperty("text", text);
            request_JsonObject.addProperty("text_type", "plain");
            request_JsonObject.addProperty("operation", "query");
            request_JsonObject.addProperty("with_frontend", 1);
            request_JsonObject.addProperty("frontend_type", "unitTson");
            requestJson.add("request", request_JsonObject);

            // 使用Bearer Token鉴权方式
            String bearerToken = "Bearer; " + accessToken; // 注意分号是火山引擎的特殊格式

            RequestBody requestBody = RequestBody.create(JSON, requestJson.toString());

            // 设置请求头和请求体
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", bearerToken) // 添加Authorization头
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    logger.error("TTS请求失败: {} {}, 错误信息: {}, 原始内容: {}", response.code(), response.message(), errorBody, text);
                    return false;
                }

                // 解析响应
                if (response.body() != null) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                    // 检查响应是否包含错误
                    if (jsonResponse.has("code") && jsonResponse.get("code").getAsInt() != 3000) {
                        logger.error("TTS请求返回错误: code={}, message={}",
                                jsonResponse.get("code").getAsInt(),
                                jsonResponse.get("message").getAsString());
                        return false;
                    }

                    // 获取音频数据
                    if (jsonResponse.has("data")) {
                        String base64Audio = jsonResponse.get("data").getAsString();
                        byte[] audioData = Base64.getDecoder().decode(base64Audio);

                        // 保存音频文件
                        File audioFile = new File(audioFilePath);
                        try (FileOutputStream fout = new FileOutputStream(audioFile)) {
                            fout.write(audioData);
                        }

                        return true;
                    } else {
                        logger.error("TTS响应中未找到音频数据: {}", responseBody);
                        return false;
                    }
                } else {
                    logger.error("TTS响应体为空");
                    return false;
                }
            }
        } catch (java.io.IOException e) {
            logger.error("发送TTS请求时发生IO错误", e);
            throw new TtsException("发送TTS请求失败", e);
        } catch (Exception e) {
            logger.error("发送TTS请求时发生错误", e);
            throw new TtsException("发送TTS请求失败", e);
        }
    }

    /**
     * V3 API：克隆音色合成（HTTP Chunked NDJSON）
     */
    private boolean sendRequestV3(String text, String audioFilePath) throws Exception {
        try {
            // 构建 V3 请求参数
            JsonObject requestJson = new JsonObject();

            // user
            JsonObject user = new JsonObject();
            user.addProperty("uid", UUID.randomUUID().toString());
            requestJson.add("user", user);

            // req_params
            JsonObject reqParams = new JsonObject();
            reqParams.addProperty("text", text);
            reqParams.addProperty("speaker", voiceName);
            requestJson.add("req_params", reqParams);

            // audio_params（流式接口用 mp3 避免多次 wav header 问题）
            JsonObject audioParams = new JsonObject();
            audioParams.addProperty("format", "mp3");
            audioParams.addProperty("sample_rate", AudioUtils.SAMPLE_RATE);
            reqParams.add("audio_params", audioParams);

            RequestBody requestBody = RequestBody.create(JSON, requestJson.toString());

            Request request = new Request.Builder()
                    .url(API_URL_V3)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Api-App-Key", appId)
                    .addHeader("X-Api-Access-Key", accessToken)
                    .addHeader("X-Api-Resource-Id", "seed-icl-1.0")
                    .addHeader("X-Api-Request-Id", UUID.randomUUID().toString())
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    logger.error("V3 TTS请求失败: {} {}, 错误信息: {}", response.code(), response.message(), errorBody);
                    return false;
                }

                if (response.body() == null) {
                    logger.error("V3 TTS响应体为空");
                    return false;
                }

                // V3 响应是 Chunked NDJSON，逐行解析并拼接音频数据
                ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        JsonObject chunk = JsonParser.parseString(line).getAsJsonObject();

                        // 检查是否有音频数据
                        if (chunk.has("data") && !chunk.get("data").isJsonNull()) {
                            String base64Audio = chunk.get("data").getAsString();
                            if (!base64Audio.isEmpty()) {
                                byte[] audioData = Base64.getDecoder().decode(base64Audio);
                                audioBuffer.write(audioData);
                            }
                        }

                        // 检查结束标识或错误
                        if (chunk.has("code")) {
                            int code = chunk.get("code").getAsInt();
                            if (code != 20000000 && code != 0) {
                                String message = chunk.has("message") ? chunk.get("message").getAsString() : "未知错误";
                                logger.error("V3 TTS返回错误: code={}, message={}", code, message);
                                return false;
                            }
                        }
                    }
                }

                // 保存音频文件
                byte[] allAudioData = audioBuffer.toByteArray();
                if (allAudioData.length == 0) {
                    logger.error("V3 TTS未返回音频数据");
                    return false;
                }

                File audioFile = new File(audioFilePath);
                try (FileOutputStream fout = new FileOutputStream(audioFile)) {
                    fout.write(allAudioData);
                }
                return true;
            }
        } catch (java.io.IOException e) {
            logger.error("V3 TTS请求IO错误", e);
            throw new TtsException("V3 TTS请求失败", e);
        } catch (Exception e) {
            logger.error("V3 TTS请求错误", e);
            throw new TtsException("V3 TTS请求失败", e);
        }
    }

}