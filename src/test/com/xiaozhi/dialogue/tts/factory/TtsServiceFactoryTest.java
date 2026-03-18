package com.xiaozhi.dialogue.tts.factory;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.dialogue.token.TokenService;
import com.xiaozhi.dialogue.token.factory.TokenServiceFactory;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.entity.SysConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * TtsServiceFactory 单元测试
 * 测试 5 参数缓存键生成、不同 voice/pitch/speed 创建不同实例、removeCache 特殊清理逻辑
 */
class TtsServiceFactoryTest extends BaseUnitTest {

    @Mock
    private TokenServiceFactory tokenServiceFactory;

    @InjectMocks
    private TtsServiceFactory ttsServiceFactory;

    private Map<String, TtsService> serviceCache;

    @BeforeEach
    void setUp() {
        serviceCache = new ConcurrentHashMap<>();
        ReflectionTestUtils.setField(ttsServiceFactory, "serviceCache", serviceCache);
    }

    @Test
    void getTtsService_defaultService_usesEdgeProvider() {
        TtsService result = ttsServiceFactory.getDefaultTtsService();

        assertNotNull(result);
        // 默认应使用 Edge TTS
        assertEquals(1, serviceCache.size());
        // 验证缓存键包含默认参数
        String expectedKey = "edge:-1:zh-CN-XiaoyiNeural:1.0:1.0";
        assertTrue(serviceCache.containsKey(expectedKey),
                "缓存键应为: " + expectedKey + ", 实际: " + serviceCache.keySet());
    }

    @Test
    void getTtsService_cacheHit_returnsSameInstance() {
        SysConfig config = new SysConfig().setProvider("edge").setConfigId(1);

        TtsService first = ttsServiceFactory.getTtsService(config, "voice1", 1.0f, 1.0f);
        TtsService second = ttsServiceFactory.getTtsService(config, "voice1", 1.0f, 1.0f);

        assertSame(first, second, "相同参数应返回相同缓存实例");
    }

    @Test
    void getTtsService_differentVoice_createsDifferentInstance() {
        SysConfig config = new SysConfig().setProvider("edge").setConfigId(1);

        TtsService service1 = ttsServiceFactory.getTtsService(config, "voice1", 1.0f, 1.0f);
        TtsService service2 = ttsServiceFactory.getTtsService(config, "voice2", 1.0f, 1.0f);

        assertNotSame(service1, service2, "不同 voice 应创建不同实例");
        assertEquals(2, serviceCache.size());
    }

    @Test
    void getTtsService_differentPitch_createsDifferentInstance() {
        SysConfig config = new SysConfig().setProvider("edge").setConfigId(1);

        TtsService service1 = ttsServiceFactory.getTtsService(config, "voice1", 1.0f, 1.0f);
        TtsService service2 = ttsServiceFactory.getTtsService(config, "voice1", 1.5f, 1.0f);

        assertNotSame(service1, service2, "不同 pitch 应创建不同实例");
    }

    @Test
    void getTtsService_differentSpeed_createsDifferentInstance() {
        SysConfig config = new SysConfig().setProvider("edge").setConfigId(1);

        TtsService service1 = ttsServiceFactory.getTtsService(config, "voice1", 1.0f, 1.0f);
        TtsService service2 = ttsServiceFactory.getTtsService(config, "voice1", 1.0f, 2.0f);

        assertNotSame(service1, service2, "不同 speed 应创建不同实例");
    }

    @Test
    void getTtsService_nullConfig_defaultsToEdge() {
        TtsService result = ttsServiceFactory.getTtsService(null, "testVoice", 1.0f, 1.0f);

        assertNotNull(result);
        // 应使用 edge 默认 provider
        assertTrue(serviceCache.keySet().stream().anyMatch(k -> k.startsWith("edge:")));
    }

    @Test
    void getTtsService_aliyunProvider_createsAliyunService() {
        SysConfig config = new SysConfig().setProvider("aliyun").setConfigId(10);
        config.setApiKey("test-key").setApiUrl("https://test.aliyun.com");

        TtsService result = ttsServiceFactory.getTtsService(config, "voice", 1.0f, 1.0f);

        assertNotNull(result);
        assertTrue(serviceCache.containsKey("aliyun:10:voice:1.0:1.0"));
    }

    @Test
    void getTtsService_aliyunNlsProvider_usesTokenServiceFactory() {
        SysConfig config = new SysConfig().setProvider("aliyun-nls").setConfigId(20);
        config.setApiKey("test-key").setApiUrl("https://test.nls.aliyun.com");

        when(tokenServiceFactory.getTokenService(config)).thenReturn(mock(TokenService.class));

        TtsService result = ttsServiceFactory.getTtsService(config, "voice", 1.0f, 1.0f);

        assertNotNull(result);
        verify(tokenServiceFactory).getTokenService(config);
    }

    @Test
    void removeCache_removesAllEntriesForProvider() {
        // 为同一 provider 添加多个不同 voice 的缓存
        serviceCache.put("tencent:1:voice1:1.0:1.0", mock(TtsService.class));
        serviceCache.put("tencent:1:voice2:1.5:1.0", mock(TtsService.class));
        serviceCache.put("aliyun:2:voice1:1.0:1.0", mock(TtsService.class));

        SysConfig config = new SysConfig().setProvider("tencent").setConfigId(1);
        ttsServiceFactory.removeCache(config);

        assertFalse(serviceCache.containsKey("tencent:1:voice1:1.0:1.0"));
        assertFalse(serviceCache.containsKey("tencent:1:voice2:1.5:1.0"));
        assertTrue(serviceCache.containsKey("aliyun:2:voice1:1.0:1.0"), "其他 provider 的缓存应保留");
    }

    @Test
    void removeCache_withNullConfig_doesNothing() {
        serviceCache.put("edge:-1:voice:1.0:1.0", mock(TtsService.class));

        ttsServiceFactory.removeCache(null);

        assertEquals(1, serviceCache.size(), "null config 不应移除任何缓存");
    }
}
