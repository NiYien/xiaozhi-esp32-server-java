package com.xiaozhi.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dto.param.OtaRequestDto;
import com.xiaozhi.dto.response.OtaResponseDto;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysFirmware;
import com.xiaozhi.service.OtaService;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysFirmwareService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.JsonUtil;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OTA服务实现
 *
 * @author Joey
 */
@Service
public class OtaServiceImpl implements OtaService {

    private static final Logger logger = LoggerFactory.getLogger(OtaServiceImpl.class);

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private SysFirmwareService firmwareService;

    @Resource
    private CmsUtils cmsUtils;

    @Override
    public OtaRequestDto parseOtaRequest(String requestBody, String deviceIdHeader) {
        OtaRequestDto dto = new OtaRequestDto();
        dto.setDeviceId(deviceIdHeader);

        if (requestBody != null && !requestBody.isEmpty()) {
            try {
                Map<String, Object> jsonData = JsonUtil.OBJECT_MAPPER.readValue(requestBody, new TypeReference<>() {});

                // 获取设备ID (MAC地址)
                if (dto.getDeviceId() == null) {
                    if (jsonData.containsKey("mac_address")) {
                        dto.setDeviceId((String) jsonData.get("mac_address"));
                    } else if (jsonData.containsKey("mac")) {
                        dto.setDeviceId((String) jsonData.get("mac"));
                    }
                }

                // 提取芯片型号
                if (jsonData.containsKey("chip_model_name")) {
                    dto.setChipModelName((String) jsonData.get("chip_model_name"));
                }

                // 提取应用版本
                if (jsonData.containsKey("application") && jsonData.get("application") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> application = (Map<String, Object>) jsonData.get("application");
                    if (application.containsKey("version")) {
                        dto.setVersion((String) application.get("version"));
                    }
                }

                // 提取WiFi名称和设备类型
                if (jsonData.containsKey("board") && jsonData.get("board") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> board = (Map<String, Object>) jsonData.get("board");
                    if (board.containsKey("ssid")) {
                        dto.setWifiName((String) board.get("ssid"));
                    }
                    if (board.containsKey("type")) {
                        dto.setType((String) board.get("type"));
                    }
                }
            } catch (Exception e) {
                logger.debug("JSON解析失败: {}", e.getMessage());
            }
        }

        return dto;
    }

    @Override
    public OtaResponseDto processOtaRequest(OtaRequestDto otaRequest, HttpServletRequest request) {
        OtaResponseDto response = new OtaResponseDto();

        // 校验设备ID
        if (otaRequest.getDeviceId() == null || !cmsUtils.isMacAddressValid(otaRequest.getDeviceId())) {
            response.setError("设备ID不正确");
            return response;
        }

        String deviceId = otaRequest.getDeviceId();

        // 构建设备对象用于查询和更新
        SysDevice device = new SysDevice();
        device.setDeviceId(deviceId);
        device.setChipModelName(otaRequest.getChipModelName());
        device.setVersion(otaRequest.getVersion());
        device.setWifiName(otaRequest.getWifiName());
        device.setType(otaRequest.getType());

        // 设置设备IP地址
        device.setIp(CmsUtils.getClientIp(request));
        var ipInfo = CmsUtils.getIPInfoByAddress(device.getIp());
        if (ipInfo != null && ipInfo.getLocation() != null && !ipInfo.getLocation().isEmpty()) {
            device.setLocation(ipInfo.getLocation());
        }

        // 设置服务器时间
        Map<String, Object> serverTimeData = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        serverTimeData.put("timestamp", timestamp);
        serverTimeData.put("timezone_offset", 480); // 东八区
        response.setServerTime(serverTimeData);

        // 固件匹配（D2策略）
        Map<String, Object> firmwareData = new HashMap<>();
        SysFirmware matchedFirmware = firmwareService.matchFirmware(
                otaRequest.getChipModelName(), otaRequest.getType());

        if (matchedFirmware != null) {
            firmwareData.put("version", matchedFirmware.getVersion());
            firmwareData.put("url", matchedFirmware.getUrl());
        } else {
            // 回退到硬编码默认值（向后兼容）
            firmwareData.put("version", "1.0.0");
            firmwareData.put("url", cmsUtils.getOtaAddress());
        }
        response.setFirmware(firmwareData);

        // 查询设备是否已绑定
        List<SysDevice> queryDevice = deviceService.query(device, new PageFilter());

        if (ObjectUtils.isEmpty(queryDevice)) {
            // 设备未绑定，生成验证码
            try {
                SysDevice codeResult = deviceService.generateCode(device);
                Map<String, Object> activationData = new HashMap<>();
                activationData.put("code", codeResult.getCode());
                activationData.put("message", codeResult.getCode());
                activationData.put("challenge", deviceId);
                response.setActivation(activationData);
            } catch (Exception e) {
                logger.error("生成验证码失败", e);
                response.setError("生成验证码失败");
                return response;
            }
        } else {
            // 设备已绑定，设置WebSocket连接信息
            String websocketToken = "";
            Map<String, Object> websocketData = new HashMap<>();
            websocketData.put("url", cmsUtils.getWebsocketAddress());
            websocketData.put("token", websocketToken);
            response.setWebsocket(websocketData);

            // 更新设备信息
            SysDevice boundDevice = queryDevice.get(0);
            device.setDeviceName(boundDevice.getDeviceName());
            deviceService.update(device);
        }

        return response;
    }
}
