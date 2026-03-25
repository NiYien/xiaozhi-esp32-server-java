package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.dto.param.ConfigAddParam;
import com.xiaozhi.dto.param.ConfigUpdateParam;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;
import com.xiaozhi.utils.HttpUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.BeanUtils;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * 配置管理
 * 
 * 
 */

@RestController
@RequestMapping("/api/config")
@Tag(name = "配置管理", description = "配置相关操作")
public class ConfigController extends BaseController {

    @Value("${aec.enabled:true}")
    private boolean aecEnabled;

    @Value("${aec.stream.delay.ms:120}")
    private int aecStreamDelayMs;

    @Value("${aec.noise.suppression.level:MODERATE}")
    private String aecNoiseSuppressionLevel;

    @Resource
    private SysConfigService configService;

    @Resource
    private TtsServiceFactory ttsServiceFactory;

    @Resource
    private SttServiceFactory sttServiceFactory;

    /**
     * 配置查询
     *
     * @param config
     * @return configList
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询配置", description = "返回配置信息列表")
    public ResultMessage list(SysConfig config, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            List<SysConfig> configList = configService.query(config, pageFilter);

            ResultMessage result = ResultMessage.success();
            result.put("data", DtoConverter.toPageInfo(configList, DtoConverter::toConfigDTOList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 配置信息更新
     *
     * @param configId 配置ID
     * @param param 更新参数
     * @return
     */
    @PutMapping("/{configId}")
    @ResponseBody
    @Operation(summary = "更新配置信息", description = "更新LLM/STT/TTS配置")
    public ResultMessage update(@PathVariable Integer configId, @Valid @RequestBody ConfigUpdateParam param) {
        try {
            SysConfig config = new SysConfig();
            BeanUtils.copyProperties(param, config);
            config.setConfigId(configId);
            config.setUserId(CmsUtils.getUserId());

            SysConfig oldSysConfig = configService.selectConfigById(config.getConfigId());
            int rows = configService.update(config);
            if (rows > 0) {
                if (oldSysConfig != null) {
                    if ("stt".equals(oldSysConfig.getConfigType())
                            && !Objects.equals(oldSysConfig.getApiKey(), config.getApiKey())) {
                        sttServiceFactory.removeCache(oldSysConfig);
                    } else if ("tts".equals(oldSysConfig.getConfigType())
                            && !Objects.equals(oldSysConfig.getApiKey(), config.getApiKey())) {
                        ttsServiceFactory.removeCache(oldSysConfig);
                    }
                }

                // 返回更新后的配置信息
                SysConfig updatedConfig = configService.selectConfigById(configId);
                return ResultMessage.success(DtoConverter.toConfigDTO(updatedConfig));
            }
            return ResultMessage.error("更新失败");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 添加配置
     *
     * @param param 添加参数
     */
    @PostMapping("")
    @ResponseBody
    @Operation(summary = "添加配置信息", description = "添加新的LLM/STT/TTS配置")
    public ResultMessage create(@Valid @RequestBody ConfigAddParam param) {
        try {
            SysConfig config = new SysConfig();
            BeanUtils.copyProperties(param, config);
            config.setUserId(CmsUtils.getUserId());

            configService.add(config);

            // 返回新增的配置信息(不包含敏感字段)
            return ResultMessage.success(DtoConverter.toConfigDTO(config));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 动态获取模型列表
     *
     * @param configType 配置类型（stt/tts）
     * @param provider   提供商标识
     * @param apiKey     API Key
     * @return 模型列表
     */
    @GetMapping("/models")
    @ResponseBody
    @Operation(summary = "动态获取模型列表", description = "根据提供商和API Key动态获取可用模型列表")
    public ResultMessage getModels(
            @RequestParam String configType,
            @RequestParam String provider,
            @RequestParam(required = false) String apiKey) {
        try {
            List<Map<String, String>> models = new ArrayList<>();

            // 目前仅阿里云 DashScope 支持动态获取
            if ("aliyun".equals(provider) && apiKey != null && !apiKey.isEmpty()) {
                models = fetchDashScopeModels(configType, apiKey);
            }

            return ResultMessage.success(models);
        } catch (Exception e) {
            logger.error("动态获取模型列表失败: {}", e.getMessage(), e);
            // 异常时返回空列表，前端回退到静态列表
            return ResultMessage.success(new ArrayList<>());
        }
    }

    /**
     * 调用阿里云 DashScope /v1/models 接口获取模型列表
     */
    private List<Map<String, String>> fetchDashScopeModels(String configType, String apiKey) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/models";
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();

            try (okhttp3.Response response = HttpUtil.client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    logger.warn("DashScope /v1/models 请求失败: {}", response.code());
                    return result;
                }

                String responseBody = response.body().string();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode dataArray = rootNode.get("data");

                if (dataArray == null || !dataArray.isArray()) {
                    return result;
                }

                for (JsonNode modelNode : dataArray) {
                    String modelId = modelNode.has("id") ? modelNode.get("id").asText() : "";
                    if (modelId.isEmpty()) {
                        continue;
                    }

                    // 根据 configType 过滤模型
                    boolean match = false;
                    if ("stt".equals(configType)) {
                        // STT 模型关键词过滤
                        match = modelId.contains("paraformer") || modelId.contains("gummy")
                                || (modelId.contains("qwen") && modelId.contains("realtime")
                                        && !modelId.contains("tts"));
                    } else if ("tts".equals(configType)) {
                        // TTS 模型关键词过滤
                        match = modelId.contains("cosyvoice") || modelId.contains("sambert")
                                || (modelId.contains("tts") && !modelId.contains("speech2text"));
                    }

                    if (match) {
                        Map<String, String> model = new HashMap<>();
                        model.put("model_name", modelId);
                        // 尝试获取描述信息
                        String ownedBy = modelNode.has("owned_by") ? modelNode.get("owned_by").asText() : "";
                        model.put("desc", ownedBy.isEmpty() ? modelId : ownedBy + " - " + modelId);
                        result.add(model);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("调用 DashScope /v1/models 获取模型列表失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 查询 AEC 回声消除状态
     */
    @GetMapping("/aec-status")
    @ResponseBody
    @Operation(summary = "查询AEC回声消除状态", description = "返回AEC当前配置参数及说明")
    public ResultMessage aecStatus() {
        try {
            Map<String, Object> aecStatus = new LinkedHashMap<>();
            aecStatus.put("enabled", aecEnabled);
            aecStatus.put("enabledDesc", "是否启用AEC回声消除");
            aecStatus.put("streamDelayMs", aecStreamDelayMs);
            aecStatus.put("streamDelayMsDesc", "初始延迟提示（毫秒），帮助AEC加速收敛");
            aecStatus.put("noiseSuppressionLevel", aecNoiseSuppressionLevel);
            aecStatus.put("noiseSuppressionLevelDesc", "降噪级别，可选值: LOW, MODERATE, HIGH, VERY_HIGH");

            ResultMessage result = ResultMessage.success();
            result.put("data", aecStatus);
            return result;
        } catch (Exception e) {
            logger.error("查询AEC状态失败", e);
            return ResultMessage.error("查询AEC状态失败");
        }
    }

}