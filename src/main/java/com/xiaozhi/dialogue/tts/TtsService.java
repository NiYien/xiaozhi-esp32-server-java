package com.xiaozhi.dialogue.tts;

import com.xiaozhi.utils.AudioUtils;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * TTS服务接口
 * TODO 逐步重构为 spring ai 的TextToSpeechModel,Getter的参数信息+ TtsConfig逐步重构为TextToSpeechOptions
 */
public interface TtsService {

  /**
   * 获取服务提供商名称
   */
  String getProviderName();

  /**
   * 获取音色名称
   */
  String getVoiceName();

  /**
   * 获取语速
   */
  Float getSpeed();

  /**
   * 获取音调
   */
  Float getPitch();

  /**
   * 音频格式
   */
  default String audioFormat() {
    return "wav";
  }

  /**
   * 生成文件名称
   *
   * @return 文件名称
   */
  default String getAudioFileName() {
    return UUID.randomUUID().toString().replace("-", "") + "." + audioFormat();
  }


  /**
   * 将文本转换为语音（带自定义语音）
   * TODO return 的应该是Path而不是String
   *
   * @param text 要转换为语音的文本
   * @return 生成的音频文件路径
   */
  String textToSpeech(String text) throws Exception;

  /**
   * 是否支持流式语音合成。
   * 支持流式的提供商可以在合成过程中逐步返回PCM数据块，降低首次语音响应延迟。
   *
   * @return true表示支持流式，false表示仅支持文件模式
   */
  default boolean supportsStreaming() {
    return false;
  }

  /**
   * 流式语音合成，返回PCM数据块流。
   * 每个元素是一段16kHz/16bit/mono的PCM字节数组。
   * 默认实现：调用textToSpeech()生成文件后读取为PCM并包装成单元素Flux，保持向后兼容。
   *
   * @param text 要转换为语音的文本
   * @return PCM数据块的响应式流
   */
  default Flux<byte[]> textToSpeechStream(String text) {
    return Flux.create(sink -> {
      try {
        String audioPath = textToSpeech(text);
        if (audioPath != null) {
          byte[] pcmData = AudioUtils.readAsPcm(audioPath);
          sink.next(pcmData);
        }
        sink.complete();
      } catch (Exception e) {
        sink.error(e);
      }
    });
  }

}
