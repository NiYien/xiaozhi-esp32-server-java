package com.xiaozhi.service;

import com.xiaozhi.dto.param.OtaRequestDto;
import com.xiaozhi.dto.response.OtaResponseDto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * OTA服务
 *
 */
public interface OtaService {

    /**
     * 解析OTA请求体，返回OTA请求DTO
     *
     * @param requestBody   请求体JSON字符串
     * @param deviceIdHeader Device-Id请求头
     * @return OtaRequestDto
     */
    OtaRequestDto parseOtaRequest(String requestBody, String deviceIdHeader);

    /**
     * 处理OTA完整流程（设备校验、绑定判断、固件匹配）
     *
     * @param otaRequest OTA请求DTO
     * @param request    HTTP请求
     * @return OtaResponseDto
     */
    OtaResponseDto processOtaRequest(OtaRequestDto otaRequest, HttpServletRequest request);
}
