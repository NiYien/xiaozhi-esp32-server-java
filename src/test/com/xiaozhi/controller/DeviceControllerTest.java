package com.xiaozhi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.service.Persona;
import com.xiaozhi.dto.param.DeviceAddParam;
import com.xiaozhi.dto.param.DeviceUpdateParam;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.utils.CmsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DeviceController 层测试（standalone MockMvc）
 * 验证路由映射、参数校验、响应结构、Session 清理行为
 */
@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SysDeviceService deviceService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private Environment environment;

    @Mock
    private CmsUtils cmsUtils;

    @InjectMocks
    private DeviceController deviceController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(deviceController).build();
    }

    // ==================== listDevices 测试 ====================

    @Test
    void query_withPagination_returnsDeviceList() throws Exception {
        SysDevice device = new SysDevice();
        device.setDeviceId("aa:bb:cc:dd:ee:ff");
        device.setDeviceName("小智");

        when(deviceService.query(any(SysDevice.class), any())).thenReturn(List.of(device));

        try (MockedStatic<CmsUtils> cmsMock = mockStatic(CmsUtils.class)) {
            cmsMock.when(CmsUtils::getUserId).thenReturn(1);

            mockMvc.perform(get("/api/device")
                            .param("start", "0")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").exists());
        }
    }

    // ==================== addDevice 测试 ====================

    @Test
    void create_validCode_addsDevice() throws Exception {
        SysDevice verifiedDevice = new SysDevice();
        verifiedDevice.setDeviceId("aa:bb:cc:dd:ee:ff");
        verifiedDevice.setType("ESP32-S3");

        SysDevice addedDevice = new SysDevice();
        addedDevice.setDeviceId("aa:bb:cc:dd:ee:ff");
        addedDevice.setDeviceName("ESP32-S3");

        when(deviceService.queryVerifyCode(any(SysDevice.class))).thenReturn(verifiedDevice);
        when(deviceService.add(any(SysDevice.class))).thenReturn(1);
        when(sessionManager.getSessionByDeviceId("aa:bb:cc:dd:ee:ff")).thenReturn(null);
        when(deviceService.selectDeviceById("aa:bb:cc:dd:ee:ff")).thenReturn(addedDevice);

        try (MockedStatic<CmsUtils> cmsMock = mockStatic(CmsUtils.class)) {
            cmsMock.when(CmsUtils::getUserId).thenReturn(1);

            DeviceAddParam param = new DeviceAddParam();
            param.setCode("ABCD1234");

            mockMvc.perform(post("/api/device")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(param)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.deviceId").value("aa:bb:cc:dd:ee:ff"));
        }
    }

    // ==================== updateDevice 测试 ====================

    @Test
    void update_roleChange_clearsSession() throws Exception {
        SysDevice oldDevice = new SysDevice();
        oldDevice.setDeviceId("aa:bb:cc:dd:ee:ff");
        oldDevice.setRoleId(1);

        SysDevice updatedDevice = new SysDevice();
        updatedDevice.setDeviceId("aa:bb:cc:dd:ee:ff");
        updatedDevice.setRoleId(2);
        updatedDevice.setDeviceName("小智");

        ChatSession mockSession = mock(ChatSession.class);
        Persona mockPersona = mock(Persona.class);
        Conversation mockConversation = mock(Conversation.class);

        when(deviceService.selectDeviceById("aa:bb:cc:dd:ee:ff"))
                .thenReturn(oldDevice)
                .thenReturn(updatedDevice);
        when(deviceService.update(any(SysDevice.class))).thenReturn(1);
        when(sessionManager.getSessionByDeviceId("aa:bb:cc:dd:ee:ff")).thenReturn(mockSession);
        when(mockSession.getPersona()).thenReturn(mockPersona);
        when(mockPersona.getConversation()).thenReturn(mockConversation);

        try (MockedStatic<CmsUtils> cmsMock = mockStatic(CmsUtils.class)) {
            cmsMock.when(CmsUtils::getUserId).thenReturn(1);

            DeviceUpdateParam param = new DeviceUpdateParam();
            param.setRoleId(2);

            mockMvc.perform(put("/api/device/aa:bb:cc:dd:ee:ff")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(param)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(mockConversation).clear();
            verify(mockSession).setPersona(null);
        }
    }

    // ==================== OTA 测试 ====================

    @Test
    void ota_validRequest_returnsFirmwareInfo() throws Exception {
        SysDevice boundDevice = new SysDevice();
        boundDevice.setDeviceId("aa:bb:cc:dd:ee:ff");
        boundDevice.setDeviceName("小智");

        when(cmsUtils.isMacAddressValid("aa:bb:cc:dd:ee:ff")).thenReturn(true);
        when(deviceService.query(any(SysDevice.class), any())).thenReturn(List.of(boundDevice));
        when(cmsUtils.getOtaAddress()).thenReturn("https://ota.example.com/firmware.bin");
        when(cmsUtils.getWebsocketAddress()).thenReturn("wss://ws.example.com/ws");
        when(deviceService.update(any(SysDevice.class))).thenReturn(1);

        try (MockedStatic<CmsUtils> cmsMock = mockStatic(CmsUtils.class)) {
            cmsMock.when(() -> CmsUtils.getClientIp(any())).thenReturn("192.168.1.1");
            cmsMock.when(() -> CmsUtils.getIPInfoByAddress(anyString())).thenReturn(null);

            String requestBody = """
                    {
                        "chip_model_name": "ESP32-S3",
                        "application": {"version": "1.0.0"},
                        "board": {"ssid": "MyWifi", "type": "ESP32-S3"}
                    }
                    """;

            mockMvc.perform(post("/api/device/ota")
                            .header("Device-Id", "aa:bb:cc:dd:ee:ff")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firmware.url").value("https://ota.example.com/firmware.bin"))
                    .andExpect(jsonPath("$.server_time.timestamp").exists())
                    .andExpect(jsonPath("$.websocket.url").value("wss://ws.example.com/ws"));
        }
    }

    @Test
    void ota_invalidMac_returnsBadRequest() throws Exception {
        when(cmsUtils.isMacAddressValid("invalid-mac")).thenReturn(false);

        mockMvc.perform(post("/api/device/ota")
                        .header("Device-Id", "invalid-mac")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("设备ID不正确"));
    }
}
