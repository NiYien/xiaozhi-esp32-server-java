package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dialogue.tts.clone.VoiceCloneManager;
import com.xiaozhi.entity.SysVoiceClone;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 音色克隆管理
 */
@RestController
@RequestMapping("/api/voice-clone")
@Tag(name = "音色克隆管理", description = "音色克隆相关操作")
public class VoiceCloneController extends BaseController {

    @Resource
    private VoiceCloneManager voiceCloneManager;

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
            @Parameter(description = "TTS配置ID") @RequestParam("configId") Integer configId) {
        try {
            Integer userId = CmsUtils.getUserId();
            SysVoiceClone voiceClone = voiceCloneManager.submitClone(userId, cloneName, provider, configId, file);
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
