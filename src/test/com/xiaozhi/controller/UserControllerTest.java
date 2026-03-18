package com.xiaozhi.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.common.exception.UserPasswordNotMatchException;
import com.xiaozhi.common.exception.UsernameNotFoundException;
import com.xiaozhi.dto.param.LoginParam;
import com.xiaozhi.dto.param.RegisterParam;
import com.xiaozhi.dto.response.LoginResponseDTO;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.security.AuthenticationService;
import com.xiaozhi.service.*;
import com.xiaozhi.service.auth.LoginResponseBuilder;
import com.xiaozhi.service.auth.CaptchaValidator;
import com.xiaozhi.service.auth.UserExistenceChecker;
import com.xiaozhi.utils.CaptchaUtils;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.EmailUtils;
import com.xiaozhi.utils.SmsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 层测试（standalone MockMvc）
 * 验证路由映射、参数校验、响应结构
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SysUserService userService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private WxLoginService wxLoginService;

    @Mock
    private SysUserAuthService userAuthService;

    @Mock
    private LoginResponseBuilder loginResponseBuilder;

    @Mock
    private CaptchaValidator captchaValidator;

    @Mock
    private UserExistenceChecker userExistenceChecker;

    @Mock
    private SmsUtils smsUtils;

    @Mock
    private EmailUtils emailUtils;

    @Mock
    private CaptchaUtils captchaUtils;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
    }

    // ==================== login 测试 ====================

    @Test
    void login_success_returnsTokenAndUserInfo() throws Exception {
        SysUser user = buildTestUser();
        LoginResponseDTO responseDTO = LoginResponseDTO.builder()
                .token("mock-token-abc").userId(1).expiresIn(2592000).build();

        when(userService.login("admin", "123456")).thenReturn(user);
        when(userService.update(any(SysUser.class))).thenReturn(1);
        when(loginResponseBuilder.buildResponse(any(SysUser.class), eq("mock-token-abc"))).thenReturn(responseDTO);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class);
             MockedStatic<CmsUtils> cmsMock = mockStatic(CmsUtils.class)) {

            stpMock.when(() -> StpUtil.login(anyInt(), anyLong())).then(inv -> null);
            stpMock.when(StpUtil::getTokenValue).thenReturn("mock-token-abc");
            cmsMock.when(() -> CmsUtils.getClientIp(any())).thenReturn("127.0.0.1");

            LoginParam param = new LoginParam();
            param.setUsername("admin");
            param.setPassword("123456");

            mockMvc.perform(post("/api/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(param)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("mock-token-abc"))
                    .andExpect(jsonPath("$.data.userId").value(1));
        }
    }

    @Test
    void login_wrongPassword_returnsError() throws Exception {
        when(userService.login("admin", "wrong"))
                .thenThrow(new UserPasswordNotMatchException());

        LoginParam param = new LoginParam();
        param.setUsername("admin");
        param.setPassword("wrong");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(param)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("密码错误"));
    }

    @Test
    void login_userNotFound_returnsError() throws Exception {
        when(userService.login("nobody", "123456"))
                .thenThrow(new UsernameNotFoundException("nobody"));

        LoginParam param = new LoginParam();
        param.setUsername("nobody");
        param.setPassword("123456");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(param)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    // ==================== register 测试 ====================

    @Test
    void register_success_createsUser() throws Exception {
        // captchaValidator.validate() 不抛异常即为通过
        doNothing().when(captchaValidator).validate(eq("123456"), eq("new@test.com"));
        // userExistenceChecker.ensureNotExists() 不抛异常即为通过
        doNothing().when(userExistenceChecker).ensureNotExists(eq("new@test.com"), isNull(), eq("newuser"), isNull());
        when(authenticationService.encryptPassword("password1")).thenReturn("encrypted");
        when(userService.add(any(SysUser.class))).thenReturn(1);

        RegisterParam param = new RegisterParam();
        param.setUsername("newuser");
        param.setPassword("password1");
        param.setEmail("new@test.com");
        param.setCode("123456");

        mockMvc.perform(post("/api/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(param)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void register_duplicateEmail_returnsError() throws Exception {
        // captchaValidator.validate() 不抛异常即为通过
        doNothing().when(captchaValidator).validate(eq("123456"), eq("exists@test.com"));
        // userExistenceChecker 抛出异常表示邮箱已注册
        doThrow(new UserExistenceChecker.UserExistsException("邮箱已注册"))
                .when(userExistenceChecker).ensureNotExists(eq("exists@test.com"), isNull(), eq("newuser"), isNull());

        RegisterParam param = new RegisterParam();
        param.setUsername("newuser");
        param.setPassword("password1");
        param.setEmail("exists@test.com");
        param.setCode("123456");

        mockMvc.perform(post("/api/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(param)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("邮箱已注册"));
    }

    // ==================== checkToken 测试 ====================

    @Test
    void checkToken_valid_returnsUserInfo() throws Exception {
        SysUser user = buildTestUser();
        LoginResponseDTO responseDTO = LoginResponseDTO.builder()
                .token("valid-token").userId(1).expiresIn(2592000).build();

        when(userService.selectUserByUserId(1)).thenReturn(user);
        when(loginResponseBuilder.buildResponse(any(SysUser.class), eq("valid-token"))).thenReturn(responseDTO);

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginId).thenReturn(1);
            stpMock.when(StpUtil::getTokenValue).thenReturn("valid-token");
            stpMock.when(StpUtil::getTokenTimeout).thenReturn(2592000L);

            mockMvc.perform(get("/api/user/check-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.token").value("valid-token"))
                    .andExpect(jsonPath("$.data.userId").value(1));
        }
    }

    @Test
    void checkToken_invalid_returnsUnauthorized() throws Exception {
        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginId).thenThrow(new RuntimeException("Token无效"));

            mockMvc.perform(get("/api/user/check-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("Token无效或已过期"));
        }
    }

    // ==================== 辅助方法 ====================

    private SysUser buildTestUser() {
        SysUser user = new SysUser();
        user.setUserId(1);
        user.setUsername("admin");
        user.setName("管理员");
        user.setEmail("admin@test.com");
        user.setRoleId(2);
        user.setState("1");
        user.setIsAdmin("0");
        return user;
    }

}
