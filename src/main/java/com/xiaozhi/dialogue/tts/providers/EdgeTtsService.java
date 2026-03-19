package com.xiaozhi.dialogue.tts.providers;

import io.github.whitemagic2014.tts.TTS;
import io.github.whitemagic2014.tts.TTSVoice;
import io.github.whitemagic2014.tts.bean.Voice;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.utils.AudioUtils;

public class EdgeTtsService implements TtsService {
    private static final Logger logger = LoggerFactory.getLogger(EdgeTtsService.class);

    private static final String PROVIDER_NAME = "edge";

    private final String voiceName;
    private final String outputPath;
    private final Float pitch;
    private final Float speed;

    // 缓存 Voice 对象，避免每次调用 TTSVoice.provides() 遍历
    private final Voice cachedVoice;

    public EdgeTtsService(String voiceName, Float pitch, Float speed, String outputPath) {
        this.voiceName = voiceName;
        this.pitch = pitch;
        this.speed = speed;
        this.outputPath = outputPath;

        Voice voice = null;
        try {
            voice = TTSVoice.provides().stream()
                    .filter(v -> v.getShortName().equals(voiceName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Edge TTS Voice 列表获取失败，将在首次调用时重试: {}", e.getMessage());
        }
        this.cachedVoice = voice;
        logger.info("Edge TTS 初始化完成, voice: {}, cached: {}", voiceName, voice != null);
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
    public String audioFormat() {
        return "mp3";
    }

    @Override
    public String textToSpeech(String text) throws Exception {
        long start = System.currentTimeMillis();

        int ratePercent = (int) ((speed - 1.0f) * 100);
        int pitchHz = (int) ((pitch - 1.0f) * 50);

        Voice voice = cachedVoice;
        if (voice == null) {
            voice = TTSVoice.provides().stream()
                    .filter(v -> v.getShortName().equals(voiceName))
                    .findFirst()
                    .orElseThrow(() -> new Exception("Edge TTS voice not found: " + voiceName));
        }

        // Edge TTS API 调用 → 写 MP3 文件
        TTS ttsEngine = new TTS(voice, text);
        String audioFilePath = ttsEngine.findHeadHook()
                .storage(outputPath)
                .fileName(getAudioFileName().split("\\.")[0])
                .isRateLimited(true)
                .overwrite(false)
                .voicePitch(pitchHz + "Hz")
                .voiceRate(ratePercent + "%")
                .formatMp3()
                .trans();

        long apiElapsed = System.currentTimeMillis() - start;

        String fullPath = outputPath + audioFilePath;

        // JLayer 纯 Java 解码 MP3→PCM（无 FFmpeg 依赖）
        byte[] mp3Data = Files.readAllBytes(Paths.get(fullPath));
        byte[] pcmData = AudioUtils.mp3BytesToPcm(mp3Data);

        // 保存为 WAV
        String wavFilePath = AudioUtils.saveAsWav(pcmData);

        // 删除 MP3 临时文件
        Files.deleteIfExists(Paths.get(fullPath));

        long totalElapsed = System.currentTimeMillis() - start;
        logger.info("Edge TTS 耗时: {}ms (API: {}ms, 解码: {}ms), 文本长度: {}",
                totalElapsed, apiElapsed, totalElapsed - apiElapsed, text.length());

        return wavFilePath;
    }

}
