package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dialogue.voiceprint.SpeakerEmbeddingService;
import com.xiaozhi.dialogue.voiceprint.VoiceprintRecognitionService;
import com.xiaozhi.entity.SysVoiceprint;
import com.xiaozhi.service.SysVoiceprintService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 声纹管理
 */
@RestController
@RequestMapping("/api/voiceprint")
@Tag(name = "声纹管理", description = "声纹注册、查询、删除等操作")
public class VoiceprintController extends BaseController {

    @Resource
    private SysVoiceprintService voiceprintService;

    @Resource
    private SpeakerEmbeddingService speakerEmbeddingService;

    @Resource
    private VoiceprintRecognitionService voiceprintRecognitionService;

    /**
     * 查询当前用户的声纹列表
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "查询声纹列表", description = "查询当前登录用户的所有有效声纹")
    public ResultMessage list() {
        try {
            Integer userId = CmsUtils.getUserId();
            List<SysVoiceprint> voiceprints = voiceprintService.listByUserId(userId);

            // 构建返回数据（不含embedding二进制）
            List<Map<String, Object>> result = voiceprints.stream().map(vp -> {
                Map<String, Object> map = new HashMap<>();
                map.put("voiceprintId", vp.getVoiceprintId());
                map.put("voiceprintName", vp.getVoiceprintName());
                map.put("deviceId", vp.getDeviceId());
                map.put("sampleCount", vp.getSampleCount());
                map.put("createTime", vp.getCreateTime());
                return map;
            }).toList();

            ResultMessage msg = ResultMessage.success();
            msg.put("data", result);
            return msg;
        } catch (Exception e) {
            logger.error("查询声纹列表失败: {}", e.getMessage(), e);
            return ResultMessage.error("查询声纹列表失败");
        }
    }

    /**
     * 上传音频注册声纹
     *
     * @param deviceId 设备ID
     * @param audioFile 音频文件（16kHz 16bit单声道 PCM/WAV）
     */
    @PostMapping("/register")
    @ResponseBody
    @Operation(summary = "注册声纹", description = "上传音频文件注册声纹，音频需为16kHz 16bit单声道格式，时长不低于1.5秒")
    public ResultMessage register(
            @RequestParam("deviceId") String deviceId,
            @RequestParam(value = "name", defaultValue = "默认声纹") String name,
            @RequestParam("audio") MultipartFile audioFile) {
        try {
            if (!speakerEmbeddingService.isEnabled()) {
                return ResultMessage.error("声纹识别功能未启用，请检查模型文件是否存在");
            }

            if (audioFile == null || audioFile.isEmpty()) {
                return ResultMessage.error("音频文件不能为空");
            }

            Integer userId = CmsUtils.getUserId();

            // 读取音频数据
            byte[] audioData = audioFile.getBytes();

            // 如果是WAV文件，跳过44字节文件头
            if (audioData.length > 44 && isWavHeader(audioData)) {
                byte[] pcmData = new byte[audioData.length - 44];
                System.arraycopy(audioData, 44, pcmData, 0, pcmData.length);
                audioData = pcmData;
            }

            // 提取嵌入向量
            float[] embedding = speakerEmbeddingService.extractEmbedding(audioData);
            if (embedding == null) {
                return ResultMessage.error("声纹提取失败，请确保音频时长不低于1.5秒且格式为16kHz 16bit单声道");
            }

            // 注册声纹
            SysVoiceprint voiceprint = voiceprintService.register(userId, deviceId, name, embedding);
            if (voiceprint == null) {
                return ResultMessage.error("声纹注册失败，可能已达到数量上限");
            }

            // 刷新设备声纹缓存
            voiceprintRecognitionService.refreshDeviceVoiceprints(deviceId);

            Map<String, Object> data = new HashMap<>();
            data.put("voiceprintId", voiceprint.getVoiceprintId());
            data.put("deviceId", deviceId);
            return ResultMessage.success("声纹注册成功", data);
        } catch (Exception e) {
            logger.error("声纹注册失败: {}", e.getMessage(), e);
            return ResultMessage.error("声纹注册失败: " + e.getMessage());
        }
    }

    /**
     * 删除声纹
     */
    @DeleteMapping("/{voiceprintId}")
    @ResponseBody
    @Operation(summary = "删除声纹", description = "删除指定的声纹记录")
    public ResultMessage delete(@PathVariable Long voiceprintId) {
        try {
            Integer userId = CmsUtils.getUserId();

            // 删除前先查询声纹的设备ID，用于后续刷新缓存
            List<SysVoiceprint> userVoiceprints = voiceprintService.listByUserId(userId);
            String deviceId = userVoiceprints.stream()
                    .filter(vp -> vp.getVoiceprintId().equals(voiceprintId))
                    .map(SysVoiceprint::getDeviceId)
                    .findFirst()
                    .orElse(null);

            int rows = voiceprintService.delete(voiceprintId, userId);
            if (rows > 0) {
                // W3: 删除声纹后刷新设备缓存，确保识别服务使用最新数据
                if (deviceId != null) {
                    voiceprintRecognitionService.refreshDeviceVoiceprints(deviceId);
                }
                return ResultMessage.success("声纹删除成功");
            } else {
                return ResultMessage.error("声纹删除失败，记录不存在或无权限");
            }
        } catch (Exception e) {
            logger.error("声纹删除失败: {}", e.getMessage(), e);
            return ResultMessage.error("声纹删除失败");
        }
    }

    /**
     * 查询声纹功能状态
     */
    @GetMapping("/status")
    @ResponseBody
    @Operation(summary = "声纹功能状态", description = "查询声纹识别功能是否可用")
    public ResultMessage status() {
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", speakerEmbeddingService.isEnabled());
        data.put("embeddingDim", SpeakerEmbeddingService.EMBEDDING_DIM);
        data.put("minSpeechDuration", SpeakerEmbeddingService.MIN_SPEECH_DURATION_SECONDS);

        Integer userId = CmsUtils.getUserId();
        data.put("voiceprintCount", voiceprintService.countByUserId(userId));

        return ResultMessage.success("查询成功", data);
    }

    /**
     * 判断是否为WAV文件头
     * S3: 局限性说明 - 此方法仅检查RIFF标识，假定标准44字节WAV头。
     * 对于包含扩展元数据（如LIST/INFO块）的WAV文件，实际数据偏移可能大于44字节。
     * 如需精确解析，应读取fmt子块和data子块的偏移量来定位PCM数据起始位置。
     */
    private boolean isWavHeader(byte[] data) {
        return data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F';
    }
}
