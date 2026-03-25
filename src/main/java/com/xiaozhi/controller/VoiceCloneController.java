package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dialogue.tts.clone.VoiceCloneManager;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysVoiceClone;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 音色克隆管理
 */
@RestController
@RequestMapping("/api/voice-clone")
@Tag(name = "音色克隆管理", description = "音色克隆相关操作")
public class VoiceCloneController extends BaseController {

    @Resource
    private VoiceCloneManager voiceCloneManager;

    @Resource
    private SysMessageService sysMessageService;

    /**
     * 上传音频并提交训练
     */
    @PostMapping("/upload")
    @ResponseBody
    @Operation(summary = "上传音频并提交训练", description = "上传音频样本文件，校验后提交到云API进行音色克隆训练")
    public ResultMessage upload(
            @Parameter(description = "音频文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "音色名称") @RequestParam("cloneName") String cloneName,
            @Parameter(description = "提供商(volcengine/aliyun-nls)") @RequestParam("provider") String provider,
            @Parameter(description = "TTS配置ID") @RequestParam("configId") Integer configId,
            @Parameter(description = "声音ID(火山引擎从控制台获取)") @RequestParam(value = "speakerId", required = false) String speakerId) {
        try {
            Integer userId = CmsUtils.getUserId();
            SysVoiceClone voiceClone = voiceCloneManager.submitClone(userId, cloneName, provider, configId, file, speakerId);
            return ResultMessage.success(voiceClone);
        } catch (RuntimeException e) {
            logger.error("音色克隆上传失败: {}", e.getMessage());
            return ResultMessage.error(e.getMessage());
        } catch (Exception e) {
            logger.error("音色克隆上传失败", e);
            return ResultMessage.error("上传失败");
        }
    }

    /**
     * 查询音色克隆列表
     */
    @GetMapping("/list")
    @ResponseBody
    @Operation(summary = "查询音色克隆列表", description = "查询当前用户的所有克隆音色")
    public ResultMessage list() {
        try {
            Integer userId = CmsUtils.getUserId();
            List<SysVoiceClone> cloneList = voiceCloneManager.listClones(userId);
            ResultMessage result = ResultMessage.success();
            result.put("data", cloneList);
            return result;
        } catch (Exception e) {
            logger.error("查询音色克隆列表失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除音色克隆
     */
    @DeleteMapping("/{cloneId}")
    @ResponseBody
    @Operation(summary = "删除音色克隆", description = "删除指定的克隆音色，同时清理云端数据和角色引用")
    public ResultMessage delete(@PathVariable Integer cloneId) {
        try {
            Integer userId = CmsUtils.getUserId();
            voiceCloneManager.deleteClone(cloneId, userId);
            return ResultMessage.success("删除成功");
        } catch (RuntimeException e) {
            logger.error("删除音色克隆失败: {}", e.getMessage());
            return ResultMessage.error(e.getMessage());
        } catch (Exception e) {
            logger.error("删除音色克隆失败", e);
            return ResultMessage.error("删除失败");
        }
    }

    /**
     * 从对话记录中选择音频提交克隆
     */
    @PostMapping("/submitFromMessages")
    @ResponseBody
    @Operation(summary = "从对话记录提交克隆", description = "选择多条用户音频消息，拼接后提交音色克隆训练")
    public ResultMessage submitFromMessages(@RequestBody Map<String, Object> params) {
        try {
            String cloneName = (String) params.get("cloneName");
            String provider = (String) params.get("provider");
            Integer configId = params.get("configId") != null ? ((Number) params.get("configId")).intValue() : null;
            String speakerId = (String) params.get("speakerId");
            @SuppressWarnings("unchecked")
            List<Integer> messageIds = (List<Integer>) params.get("messageIds");

            if (cloneName == null || provider == null || configId == null || messageIds == null || messageIds.isEmpty()) {
                return ResultMessage.error("参数不完整");
            }

            Integer userId = CmsUtils.getUserId();

            // 查询消息并提取音频路径
            List<SysMessage> messages = sysMessageService.findByIds(messageIds);
            List<String> audioPaths = messages.stream()
                    .filter(m -> m.getAudioPath() != null && !m.getAudioPath().isBlank())
                    .map(SysMessage::getAudioPath)
                    .toList();

            if (audioPaths.isEmpty()) {
                return ResultMessage.error("所选消息中没有有效的音频文件");
            }

            // 拼接音频为 WAV
            byte[] wavData = AudioUtils.mergeOpusToWav(audioPaths);

            // 将 WAV 数据包装为 MultipartFile
            MultipartFile multipartFile = new MultipartFile() {
                @Override public String getName() { return "file"; }
                @Override public String getOriginalFilename() { return "merged_audio.wav"; }
                @Override public String getContentType() { return "audio/wav"; }
                @Override public boolean isEmpty() { return wavData.length == 0; }
                @Override public long getSize() { return wavData.length; }
                @Override public byte[] getBytes() { return wavData; }
                @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(wavData); }
                @Override public void transferTo(java.io.File dest) throws java.io.IOException {
                    java.nio.file.Files.write(dest.toPath(), wavData);
                }
            };

            SysVoiceClone voiceClone = voiceCloneManager.submitClone(userId, cloneName, provider, configId, multipartFile, speakerId);
            return ResultMessage.success(voiceClone);
        } catch (RuntimeException e) {
            logger.error("从对话记录提交克隆失败: {}", e.getMessage());
            return ResultMessage.error(e.getMessage());
        } catch (Exception e) {
            logger.error("从对话记录提交克隆失败", e);
            return ResultMessage.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 试听克隆音色
     */
    @GetMapping("/preview/{cloneId}")
    @ResponseBody
    @Operation(summary = "试听克隆音色", description = "使用克隆音色合成预设文本的音频并返回文件路径")
    public ResultMessage preview(@PathVariable Integer cloneId) {
        try {
            String audioFilePath = voiceCloneManager.previewClone(cloneId);
            ResultMessage result = ResultMessage.success();
            result.put("data", audioFilePath);
            return result;
        } catch (RuntimeException e) {
            logger.error("试听克隆音色失败: {}", e.getMessage());
            return ResultMessage.error(e.getMessage());
        } catch (Exception e) {
            logger.error("试听克隆音色失败", e);
            return ResultMessage.error("试听失败");
        }
    }
}
