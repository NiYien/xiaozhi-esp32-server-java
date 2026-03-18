package com.xiaozhi.dialogue.stt.factory;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.entity.SysConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SttServiceFactory 单元测试
 * 测试缓存命中/未命中、provider 选择、Vosk 回退、null config 处理
 */
class SttServiceFactoryTest extends BaseUnitTest {

    @Mock
    private TokenServiceFactory tokenServiceFactory;

    @InjectMocks
    private SttServiceFactory sttServiceFactory;

    private Map<String, SttService> serviceCache;

    @BeforeEach
    void setUp() {
        serviceCache = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(sttServiceFactory, "serviceCache", serviceCache);
        // 标记 Vosk 未初始化，避免测试依赖本地模型文件
        ReflectionTestUtils.setField(sttServiceFactory, "voskInitialized", false);
    }

    @Test
    void getSttService_withNullConfig_defaultsToVoskProvider() {
        // null config 应默认使用 vosk provider，缓存键为 "vosk:-1"
        // 由于 Vosk 模型不存在会失败，但验证逻辑流转正确
        // 设置 fallbackProvider 避免 RuntimeException
        SttService mockService = mock(SttService.class);
        serviceCache.put("tencent:1", mockService);
        ReflectionTestUtils.setField(sttServiceFactory, "fallbackProvider", "tencent:1");

        SttService result = sttServiceFactory.getSttService(null);
        // 应该返回 fallback 服务
        assertNotNull(result);
        assertSame(mockService, result);
    }

    @Test
    void getSttService_cacheHit_returnsCachedService() {
        SttService mockService = mock(SttService.class);
        serviceCache.put("tencent:1", mockService);

        SysConfig config = new SysConfig().setProvider("tencent").setConfigId(1);
        SttService result = sttServiceFactory.getSttService(config);

        assertSame(mockService, result, "应返回缓存中的服务实例");
    }

    @Test
    void getSttService_cacheMiss_createsNewTencentService() {
        SysConfig config = new SysConfig().setProvider("tencent").setConfigId(100);
        config.setApiKey("test-key").setAppId("test-app");

        SttService result = sttServiceFactory.getSttService(config);

        assertNotNull(result, "应创建新的 TencentSttService");
        assertTrue(serviceCache.containsKey("tencent:100"), "新服务应被缓存");
    }

    @Test
    void getSttService_aliyunProvider_createsAliyunService() {
        SysConfig config = new SysConfig().setProvider("aliyun").setConfigId(200);
        config.setApiKey("test-key").setApiUrl("https://test.aliyun.com");

        SttService result = sttServiceFactory.getSttService(config);

        assertNotNull(result);
        assertTrue(serviceCache.containsKey("aliyun:200"));
    }

    @Test
    void getSttService_aliyunNlsProvider_usesTokenServiceFactory() {
        SysConfig config = new SysConfig().setProvider("aliyun-nls").setConfigId(300);
        config.setApiKey("test-key").setApiUrl("https://test.nls.aliyun.com");

        when(tokenServiceFactory.getTokenService(config)).thenReturn(mock(com.xiaozhi.dialogue.token.TokenService.class));

        SttService result = sttServiceFactory.getSttService(config);

        assertNotNull(result);
        verify(tokenServiceFactory).getTokenService(config);
    }

    @Test
    void getSttService_unknownProvider_fallsBackWhenVoskFails() {
        // 先缓存一个 fallback 服务
        SttService fallbackService = mock(SttService.class);
        serviceCache.put("tencent:1", fallbackService);
        ReflectionTestUtils.setField(sttServiceFactory, "fallbackProvider", "tencent:1");

        SysConfig config = new SysConfig().setProvider("unknown-provider").setConfigId(999);

        SttService result = sttServiceFactory.getSttService(config);
        // Vosk 初始化失败时应返回 fallback
        assertSame(fallbackService, result);
    }

    @Test
    void getSttService_unknownProvider_noFallback_throwsException() {
        ReflectionTestUtils.setField(sttServiceFactory, "fallbackProvider", null);

        SysConfig config = new SysConfig().setProvider("unknown-provider").setConfigId(999);

        assertThrows(RuntimeException.class, () -> sttServiceFactory.getSttService(config),
                "无 fallback 时应抛出 RuntimeException");
    }

    @Test
    void removeCache_removesCorrectEntry() {
        SttService mockService = mock(SttService.class);
        serviceCache.put("tencent:1", mockService);
        serviceCache.put("aliyun:2", mock(SttService.class));

        SysConfig config = new SysConfig().setProvider("tencent").setConfigId(1);
        sttServiceFactory.removeCache(config);

        assertFalse(serviceCache.containsKey("tencent:1"), "指定缓存应被移除");
        assertTrue(serviceCache.containsKey("aliyun:2"), "其他缓存应保留");
    }

    @Test
    void getSttService_firstApiService_setsFallbackProvider() {
        ReflectionTestUtils.setField(sttServiceFactory, "fallbackProvider", null);

        SysConfig config = new SysConfig().setProvider("tencent").setConfigId(1);
        config.setApiKey("test");

        sttServiceFactory.getSttService(config);

        String fallbackProvider = (String) ReflectionTestUtils.getField(sttServiceFactory, "fallbackProvider");
        assertEquals("tencent:1", fallbackProvider, "首个 API 服务应设为 fallback");
    }
}
