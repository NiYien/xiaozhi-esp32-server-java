package com.xiaozhi.dialogue.stt.providers;

import cn.xfyun.api.IatClient;
import cn.xfyun.model.response.iat.IatResponse;
import cn.xfyun.model.response.iat.IatResult;
import cn.xfyun.model.response.iat.Text;
import cn.xfyun.service.iat.AbstractIatWebSocketListener;
import com.google.gson.JsonObject;
import com.xiaozhi.common.exception.SttException;
import com.xiaozhi.dialogue.stt.AbstractStreamingSttService;
import com.xiaozhi.dialogue.stt.StreamRecognitionContext;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.utils.AudioUtils;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.xfyun.util.StringUtils.gson;
import static com.xiaozhi.dialogue.DialogueConstants.QUEUE_POLL_TIMEOUT_MS;
import static com.xiaozhi.dialogue.DialogueConstants.RECOGNITION_TIMEOUT_MS;

public class XfyunSttService extends AbstractStreamingSttService {

    public static final int StatusFirstFrame = 0;
    public static final int StatusContinueFrame = 1;
    public static final int StatusLastFrame = 2;

    private static final String PROVIDER_NAME = "xfyun";

    private static final String hostUrl = "https://iat-api.xfyun.cn/v2/iat";

    private String secretId;
    private String secretKey;
    private String appId;

    /** 当前连接的 WebSocket 引用，供 cleanup 关闭 */
    private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>();
    /** 连接是否已关闭 */
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public XfyunSttService(SysConfig config) {
        if (config != null) {
            this.secretId = config.getApiKey();
            this.secretKey = config.getApiSecret();
            this.appId = config.getAppId();
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public String recognition(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            logger.warn("音频数据为空！");
            return null;
        }
        List<Text> resultSegments = new ArrayList<>();
        // 将原始音频数据转换为MP3格式并保存（用于调试）
        String fileName = AudioUtils.saveAsWav(audioData);
        File file = new File(fileName);
        CountDownLatch recognitionLatch = new CountDownLatch(1);
        try {
            // 检查配置是否已设置
            if (secretId == null || secretKey == null) {
                logger.error("讯飞云语音识别配置未设置，无法进行识别");
                return null;
            }

            // 设置听写参数,这里的appid,apiKey,apiSecret是在开放平台控制台获得
            IatClient iatClient = new IatClient.Builder()
                    .signature(appId, secretId, secretKey)
                    // 动态修正功能：值为wpgs时代表开启（包含修正功能的）流式听写
                    .dwa("wpgs")
                    .build();

            iatClient.send(file, new AbstractIatWebSocketListener() {
                @Override
                public void onSuccess(WebSocket webSocket, IatResponse iatResponse) {
                    if (iatResponse.getCode() != 0) {
                        logger.warn("code：{}, error：{}, sid：{}", iatResponse.getCode(), iatResponse.getMessage(), iatResponse.getSid());
                        logger.warn("错误码查询链接：https://www.xfyun.cn/document/error-code");
                        return;
                    }

                    if (iatResponse.getData() != null) {
                        if (iatResponse.getData().getResult() != null) {
                            // 解析服务端返回结果
                            IatResult result = iatResponse.getData().getResult();
                            Text textObject = result.getText();
                            handleResultText(textObject, resultSegments);
                            logger.info("中间识别结果：{}", getFinalResult(resultSegments));
                        }

                        if (iatResponse.getData().getStatus() == 2) {
                            // resp.data.status ==2 说明数据全部返回完毕，可以关闭连接，释放资源
                            logger.info("session end ");
                            iatClient.closeWebsocket();
                            recognitionLatch.countDown();
                        } else {
                            // 根据返回的数据自定义处理逻辑
                        }
                    }
                }

                @Override
                public void onFail(WebSocket webSocket, Throwable t, Response response) {
                    // 自定义处理逻辑
                    recognitionLatch.countDown();
                }
            });
            // 等待识别完成或超时
            boolean recognized = recognitionLatch.await(RECOGNITION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!recognized) {
                logger.warn("讯飞云识别超时");
            }
            return getFinalResult(resultSegments);
        } catch (InterruptedException e) {
            logger.error("等待识别结果时被中断", e);
            Thread.currentThread().interrupt();
            return null;
        } catch (SttException e) {
            logger.error("处理音频时发生错误！", e);
            return null;
        } catch (Exception e) {
            logger.error("处理音频时发生未预期的错误！", e);
            return null;
        }
    }

    /**
     * 处理返回结果（包括全量返回与流式返回（结果修正））
     */
    private void handleResultText(Text textObject, List<Text> resultSegments) {
        // 处理流式返回的替换结果
        if ("rpl".equals(textObject.getPgs()) && textObject.getRg() != null && textObject.getRg().length == 2) {
            // 返回结果序号sn字段的最小值为1
            int start = textObject.getRg()[0] - 1;
            int end = textObject.getRg()[1] - 1;

            // 将指定区间的结果设置为删除状态
            for (int i = start; i <= end && i < resultSegments.size(); i++) {
                resultSegments.get(i).setDeleted(true);
            }
        }

        // 通用逻辑，添加当前文本到结果列表
        resultSegments.add(textObject);
    }

    /**
     * 获取最终结果
     */
    private String getFinalResult(List<Text> resultSegments) {
        StringBuilder finalResult = new StringBuilder();
        for (Text text : resultSegments) {
            if (text != null && !text.isDeleted()) {
                finalResult.append(text.getText());
            }
        }
        return finalResult.toString();
    }

    private String getAuthUrl(String apiKey, String apiSecret) throws Exception {
        URL url = new URL(XfyunSttService.hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());

        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n")
                .append("date: ").append(date).append("\n")
                .append("GET ").append(url.getPath()).append(" HTTP/1.1");

        Charset charset = StandardCharsets.UTF_8;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "HmacSHA256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = Base64.getEncoder().encodeToString(hexDigits);

        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                apiKey, "hmac-sha256", "host date request-line", sha);

        return Objects.requireNonNull(HttpUrl.parse("https://" + url.getHost() + url.getPath()))
                .newBuilder()
                .addQueryParameter("authorization",
                        Base64.getEncoder().encodeToString(authorization.getBytes(charset)))
                .addQueryParameter("date", date)
                .addQueryParameter("host", url.getHost())
                .build()
                .toString();
    }

    @Override
    protected boolean isConfigValid() {
        return secretId != null && secretKey != null && appId != null;
    }

    @Override
    protected void openConnection(StreamRecognitionContext ctx) {
        // 重置连接状态
        isClosed.set(false);
        webSocketRef.set(null);

        // 构建鉴权URL
        String authUrl;
        try {
            authUrl = getAuthUrl(secretId, secretKey);
        } catch (Exception e) {
            logger.error("构建鉴权URL时发生错误！", e);
            ctx.releaseLatch();
            return;
        }

        String wsUrl = authUrl.replace("http://", "ws://")
                .replace("https://", "wss://");
        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(wsUrl).build();
        AtomicInteger status = new AtomicInteger(StatusFirstFrame);
        BlockingQueue<JsonObject> frameQueue = new LinkedBlockingQueue<>();
        List<Text> resultSegments = new ArrayList<>();

        WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webSocketRef.set(webSocket);
                isClosed.set(false);
                Thread.startVirtualThread(() -> {
                    // 从基类上下文的 audioQueue 中消费原始音频，转换为讯飞帧格式
                    try {
                        while (!ctx.isCompleted() || !ctx.getAudioQueue().isEmpty()) {
                            byte[] chunk = ctx.getAudioQueue().poll(QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            if (chunk == null || isClosed.get()) continue;
                            if (chunk.length == 0) {
                                logger.debug("audioSink 数据为空，主动结束流");
                                frameQueue.offer(buildContinueFrame(chunk, chunk.length));
                                continue;
                            }
                            if (status.compareAndSet(StatusFirstFrame, StatusContinueFrame)) {
                                logger.debug("xfyun开始发送音频首帧");
                                frameQueue.offer(buildFirstFrame(chunk, chunk.length));
                            } else {
                                frameQueue.offer(buildContinueFrame(chunk, chunk.length));
                            }
                        }
                        // 音频流结束，发送最后一帧
                        if (!isClosed.get()) {
                            logger.debug("audioSink结束发送结束通知");
                            JsonObject frame = buildLastFrame();
                            webSocket.send(frame.toString());
                        }
                    } catch (InterruptedException e) {
                        logger.error("处理音频流时被中断", e);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        logger.error("处理音频流时发生错误", e);
                    }
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (isClosed.get()) return;
                IatResponse response = gson.fromJson(text, IatResponse.class);
                if (response.getCode() != 0) {
                    logger.warn("code:{}, error:{}, sid:{}",
                            response.getCode(), response.getMessage(), response.getSid());
                    return;
                }

                if (response.getData() != null && response.getData().getResult() != null) {
                    Text textObject = response.getData().getResult().getText();
                    handleResultText(textObject, resultSegments);
                }

                if (response.getData() != null && response.getData().getStatus() == 2) {
                    ctx.setResult(getFinalResult(resultSegments));
                    ctx.releaseLatch();
                    wsClose();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("流式识别失败", t);
                ctx.setResult(getFinalResult(resultSegments));
                wsClose();
                isClosed.set(true);
                webSocketRef.set(null);
                ctx.releaseLatch();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                wsClose();
                isClosed.set(true);
                webSocketRef.set(null);
                super.onClosed(webSocket, code, reason);
            }
        });

        // 发送帧线程
        Thread sendThread = new Thread(() -> {
            while (!isClosed.get()) {
                try {
                    JsonObject frame = frameQueue.poll(QUEUE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        WebSocket ws = webSocketRef.get();
                        if (ws != null) {
                            ws.send(frame.toString());
                        }
                    }
                } catch (InterruptedException e) {
                    logger.error("发送音频帧时被中断", e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    logger.error("发送音频帧失败", e);
                }
            }
        });
        sendThread.start();
    }

    @Override
    protected void cleanup(StreamRecognitionContext ctx) {
        wsClose();
    }

    /**
     * 重写模板方法以保留讯飞特有的超时后部分结果返回逻辑
     */
    @Override
    public String streamRecognition(Flux<byte[]> audioFlux) {
        // 配置检查
        if (!isConfigValid()) {
            logger.error("{}语音识别配置未设置，无法进行识别", getProviderName());
            return null;
        }

        // 构建鉴权URL（提前检查，避免进入模板流程后再失败）
        // 实际鉴权在 openConnection 中完成，这里仅做配置校验
        // 使用基类模板方法
        return super.streamRecognition(audioFlux);
    }

    private void wsClose() {
        if (isClosed.compareAndSet(false, true)) {
            WebSocket ws = webSocketRef.get();
            if (ws != null) {
                try {
                    ws.close(1000, "程序关闭");
                } catch (Exception e) {
                    logger.warn("关闭 WebSocket 时发生异常", e);
                }
            }
        }
    }

    private JsonObject buildFirstFrame(byte[] buffer, int len) {
        JsonObject common = new JsonObject();
        common.addProperty("app_id", appId);

        JsonObject business = new JsonObject();
        business.addProperty("language", "zh_cn");
        business.addProperty("domain", "iat");
        business.addProperty("accent", "mandarin");
        business.addProperty("dwa", "wpgs");

        JsonObject data = new JsonObject();
        data.addProperty("status", StatusFirstFrame);
        data.addProperty("format", "audio/L16;rate=16000");
        data.addProperty("encoding", "raw");
        data.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, len)));

        JsonObject frame = new JsonObject();
        frame.add("common", common);
        frame.add("business", business);
        frame.add("data", data);

        return frame;
    }

    private JsonObject buildContinueFrame(byte[] buffer, int len) {
        JsonObject data = new JsonObject();
        data.addProperty("status", StatusContinueFrame);
        data.addProperty("format", "audio/L16;rate=16000");
        data.addProperty("encoding", "raw");
        data.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, len)));

        JsonObject frame = new JsonObject();
        frame.add("data", data);

        return frame;
    }

    private JsonObject buildLastFrame() {
        JsonObject data = new JsonObject();
        data.addProperty("status", StatusLastFrame);
        data.addProperty("audio", "");
        data.addProperty("format", "audio/L16;rate=16000");
        data.addProperty("encoding", "raw");

        JsonObject frame = new JsonObject();
        frame.add("data", data);

        return frame;
    }
}
