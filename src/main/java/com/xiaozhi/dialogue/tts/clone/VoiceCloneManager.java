package com.xiaozhi.dialogue.tts.clone;

import com.xiaozhi.dao.VoiceCloneMapper;
import com.xiaozhi.dao.RoleMapper;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysVoiceClone;
import com.xiaozhi.service.SysConfigService;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 音色克隆管理器
 * 负责音频校验、文件存储、提交训练、状态轮询、克隆音色的增删查
 */
@Service
public class VoiceCloneManager {

    private static final Logger logger = LoggerFactory.getLogger(VoiceCloneManager.class);

    /** 每用户最大克隆音色数量 */
    private static final int MAX_CLONES_PER_USER = 10;

    /** 音频最小时长（秒） */
    private static final int MIN_DURATION_SECONDS = 10;

    /** 音频最大时长（秒） */
    private static final int MAX_DURATION_SECONDS = 600;

    /** 最低采样率（Hz） */
    private static final int MIN_SAMPLE_RATE = 16000;

    /** 试听预设文本 */
    private static final String PREVIEW_TEXT = "你好，这是一段音色克隆效果的试听文本，请确认音色是否满足您的需求。";

    @Value("${xiaozhi.upload-path:uploads}")
    private String uploadPath;

    @Resource
    private VoiceCloneMapper voiceCloneMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private SysConfigService configService;

    @Resource
    private TtsServiceFactory ttsServiceFactory;

    @Resource
    private List<VoiceCloneService> voiceCloneServices;

    /**
     * 提交音色克隆任务
     *
     * @param userId    用户ID
     * @param cloneName 用户自定义名称
     * @param provider  提供商名称
     * @param configId  TTS配置ID
     * @param file      音频文件
     * @return 创建的克隆记录
     */
    public SysVoiceClone submitClone(Integer userId, String cloneName, String provider, Integer configId, MultipartFile file) {
        // 检查数量限制
        int count = voiceCloneMapper.countByUserId(userId);
        if (count >= MAX_CLONES_PER_USER) {
            throw new RuntimeException("克隆音色数量已达上限（最多" + MAX_CLONES_PER_USER + "个）");
        }

        // 校验音频文件
        validateAudioFile(file);

        // 保存音频文件到本地
        String samplePath = saveAudioFile(file);

        // 创建数据库记录
        SysVoiceClone voiceClone = new SysVoiceClone();
        voiceClone.setUserId(userId);
        voiceClone.setCloneName(cloneName);
        voiceClone.setProvider(provider);
        voiceClone.setConfigId(configId);
        voiceClone.setSamplePath(samplePath);
        voiceClone.setStatus("uploading");
        voiceCloneMapper.insert(voiceClone);

        // 提交到云API训练
        try {
            VoiceCloneService cloneService = getCloneService(provider);
            String taskId = cloneService.submitCloneTask(samplePath, cloneName, configId);
            voiceClone.setTaskId(taskId);
            voiceClone.setStatus("training");
            voiceCloneMapper.update(voiceClone);
        } catch (Exception e) {
            logger.error("提交音色克隆任务失败: {}", e.getMessage(), e);
            voiceClone.setStatus("failed");
            voiceClone.setErrorMessage("提交训练失败: " + e.getMessage());
            voiceCloneMapper.update(voiceClone);
        }

        return voiceClone;
    }

    /**
     * 查询用户所有克隆音色
     */
    public List<SysVoiceClone> listClones(Integer userId) {
        return voiceCloneMapper.selectByUserId(userId);
    }

    /**
     * 删除克隆音色
     */
    public void deleteClone(Integer cloneId, Integer userId) {
        SysVoiceClone voiceClone = voiceCloneMapper.selectById(cloneId);
        if (voiceClone == null) {
            throw new RuntimeException("克隆音色不存在");
        }
        if (!voiceClone.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该克隆音色");
        }

        // 如果有 voiceId，调用云API删除
        if (voiceClone.getVoiceId() != null && !voiceClone.getVoiceId().isEmpty()) {
            try {
                VoiceCloneService cloneService = getCloneService(voiceClone.getProvider());
                cloneService.deleteVoice(voiceClone.getVoiceId(), voiceClone.getConfigId());
            } catch (Exception e) {
                logger.warn("调用云API删除音色失败: {}", e.getMessage());
            }
        }

        // 清理角色引用：将引用此克隆音色的角色 voiceName 置为 NULL
        // 注意：voiceName 为 NULL 时，TtsServiceFactory 会自动使用对应 Provider 的默认音色
        String cloneVoiceName = "clone:" + cloneId;
        clearRoleVoiceReference(cloneVoiceName);

        // 删除本地音频文件
        if (voiceClone.getSamplePath() != null) {
            try {
                Files.deleteIfExists(Path.of(voiceClone.getSamplePath()));
            } catch (Exception e) {
                logger.warn("删除音频样本文件失败: {}", e.getMessage());
            }
        }

        // 删除数据库记录
        voiceCloneMapper.deleteById(cloneId);
    }

    /**
     * 试听克隆音色
     *
     * @param cloneId 克隆音色ID
     * @return 合成的音频文件路径
     */
    public String previewClone(Integer cloneId) {
        SysVoiceClone voiceClone = voiceCloneMapper.selectById(cloneId);
        if (voiceClone == null) {
            throw new RuntimeException("克隆音色不存在");
        }
        if (!"ready".equals(voiceClone.getStatus())) {
            throw new RuntimeException("音色尚未训练完成，无法试听");
        }

        try {
            SysConfig config = configService.selectConfigById(voiceClone.getConfigId());
            if (config == null) {
                throw new RuntimeException("TTS配置不存在");
            }

            // 使用克隆音色的 voiceId 进行合成
            TtsService ttsService = ttsServiceFactory.getTtsService(config, voiceClone.getVoiceId(), 1.0f, 1.0f);
            return ttsService.textToSpeech(PREVIEW_TEXT);
        } catch (Exception e) {
            logger.error("试听克隆音色失败: {}", e.getMessage(), e);
            throw new RuntimeException("试听失败: " + e.getMessage());
        }
    }

    /**
     * 根据 cloneId 查询克隆音色信息
     */
    public SysVoiceClone getById(Integer cloneId) {
        return voiceCloneMapper.selectById(cloneId);
    }

    // ===================== T-3.3.6 训练状态轮询 =====================

    /**
     * 定时轮询训练中的克隆任务状态
     * 每30秒执行一次，查询所有 status=training 的记录
     */
    @Scheduled(fixedRate = 30000)
    public void pollTrainingStatus() {
        List<SysVoiceClone> trainingClones = voiceCloneMapper.selectByStatus("training");
        if (trainingClones.isEmpty()) {
            return;
        }

        logger.info("开始轮询音色克隆训练状态，共 {} 个任务", trainingClones.size());

        // 按 provider 分组以优化查询
        Map<String, List<SysVoiceClone>> groupByProvider = trainingClones.stream()
                .collect(Collectors.groupingBy(SysVoiceClone::getProvider));

        for (Map.Entry<String, List<SysVoiceClone>> entry : groupByProvider.entrySet()) {
            String provider = entry.getKey();
            VoiceCloneService cloneService;
            try {
                cloneService = getCloneService(provider);
            } catch (Exception e) {
                logger.warn("未找到 {} 的克隆服务实现，跳过", provider);
                continue;
            }

            for (SysVoiceClone clone : entry.getValue()) {
                try {
                    VoiceCloneStatus status = cloneService.queryStatus(clone.getTaskId(), clone.getConfigId());
                    switch (status) {
                        case READY -> {
                            String voiceId = cloneService.getVoiceId(clone.getTaskId(), clone.getConfigId());
                            clone.setStatus("ready");
                            clone.setVoiceId(voiceId);
                            voiceCloneMapper.update(clone);
                            logger.info("音色克隆训练完成: cloneId={}, voiceId={}", clone.getCloneId(), voiceId);
                        }
                        case FAILED -> {
                            clone.setStatus("failed");
                            clone.setErrorMessage("云端训练失败");
                            voiceCloneMapper.update(clone);
                            logger.warn("音色克隆训练失败: cloneId={}", clone.getCloneId());
                        }
                        case TRAINING -> {
                            // 仍在训练中，不做处理
                        }
                    }
                } catch (Exception e) {
                    logger.error("轮询克隆任务状态异常: cloneId={}, error={}", clone.getCloneId(), e.getMessage());
                }
            }
        }
    }

    // ===================== 私有方法 =====================

    /**
     * 校验音频文件格式、时长、采样率
     */
    private void validateAudioFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new RuntimeException("文件名不能为空");
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".wav") && !lowerName.endsWith(".mp3")) {
            throw new RuntimeException("仅支持 WAV 和 MP3 格式");
        }

        // 校验音频属性
        try (InputStream is = file.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(is);
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis)) {

            AudioFormat format = audioInputStream.getFormat();

            // 校验单声道
            if (format.getChannels() != 1) {
                throw new RuntimeException("仅支持单声道音频");
            }

            // 校验采样率
            if (format.getSampleRate() < MIN_SAMPLE_RATE) {
                throw new RuntimeException("采样率不足，至少需要 16kHz");
            }

            // 计算时长
            long frames = audioInputStream.getFrameLength();
            if (frames > 0) {
                double durationSeconds = frames / (double) format.getFrameRate();
                if (durationSeconds < MIN_DURATION_SECONDS) {
                    throw new RuntimeException("音频时长不足，至少需要 " + MIN_DURATION_SECONDS + " 秒");
                }
                if (durationSeconds > MAX_DURATION_SECONDS) {
                    throw new RuntimeException("音频时长过长，最大 " + MAX_DURATION_SECONDS + " 秒");
                }
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            // MP3 降级路径：AudioSystem 不原生支持 MP3 格式，无法校验采样率和声道数，
            // 此处仅通过文件大小粗略判断时长是否合理。如需精确校验可使用 FFmpeg。
            logger.warn("音频格式不受 AudioSystem 支持（可能为 MP3），无法校验采样率和声道数，仅校验文件大小");
            long fileSize = file.getSize();
            if (fileSize < 100 * 1024) { // 小于100KB视为时长不足
                throw new RuntimeException("音频时长不足，至少需要 " + MIN_DURATION_SECONDS + " 秒");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("音频文件校验异常，将跳过详细校验: {}", e.getMessage());
        }
    }

    /**
     * 保存音频文件到本地
     */
    private String saveAudioFile(MultipartFile file) {
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String relativePath = "voice-clone/" + datePath;
            String extension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
            String fileName = UUID.randomUUID().toString().replaceAll("-", "") + extension;

            String fullPath = uploadPath + "/" + relativePath;
            File dir = new File(fullPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String filePath = fullPath + "/" + fileName;
            file.transferTo(new File(filePath));
            return filePath;
        } catch (Exception e) {
            throw new RuntimeException("保存音频文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取对应 provider 的克隆服务实现
     */
    private VoiceCloneService getCloneService(String provider) {
        return voiceCloneServices.stream()
                .filter(s -> s.getProviderName().equals(provider))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的音色克隆提供商: " + provider));
    }

    /**
     * 清理角色对克隆音色的引用
     * 将引用被删除克隆音色的角色 voiceName 置空
     */
    private void clearRoleVoiceReference(String cloneVoiceName) {
        try {
            int affected = roleMapper.clearVoiceName(cloneVoiceName);
            if (affected > 0) {
                logger.info("已清理 {} 个角色的克隆音色引用: voiceName={}", affected, cloneVoiceName);
            }
        } catch (Exception e) {
            logger.warn("清理角色引用失败: {}", e.getMessage());
        }
    }
}
