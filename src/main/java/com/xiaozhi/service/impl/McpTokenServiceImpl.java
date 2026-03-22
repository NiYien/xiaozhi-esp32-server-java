package com.xiaozhi.service.impl;

import com.xiaozhi.dao.McpTokenMapper;
import com.xiaozhi.entity.SysMcpToken;
import com.xiaozhi.service.McpTokenService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MCP Token 服务实现
 */
@Service
public class McpTokenServiceImpl implements McpTokenService {

    private static final Logger logger = LoggerFactory.getLogger(McpTokenServiceImpl.class);

    private static final String CACHE_PREFIX = "XiaoZhi:McpToken:";
    private static final long CACHE_EXPIRE_DAYS = 7;
    private static final int MAX_TOKENS_PER_USER = 10;
    private static final String DISABLED_VALUE = "DISABLED";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private McpTokenMapper mcpTokenMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private com.xiaozhi.mcp.endpoint.McpEndpointSessionManager mcpEndpointSessionManager;

    @Override
    public SysMcpToken generate(Integer userId, String tokenName) {
        // 检查数量限制
        int count = mcpTokenMapper.countByUserId(userId);
        if (count >= MAX_TOKENS_PER_USER) {
            throw new IllegalArgumentException("每个用户最多创建 " + MAX_TOKENS_PER_USER + " 个MCP Token");
        }

        // 生成token: mcp_ + 32字节随机hex
        String token = generateTokenValue();

        SysMcpToken mcpToken = new SysMcpToken();
        mcpToken.setUserId(userId);
        mcpToken.setToken(token);
        mcpToken.setTokenName(tokenName != null ? tokenName : "");
        mcpToken.setEnabled(1);

        mcpTokenMapper.add(mcpToken);

        // 写入缓存
        stringRedisTemplate.opsForValue().set(
                CACHE_PREFIX + token, String.valueOf(userId), CACHE_EXPIRE_DAYS, TimeUnit.DAYS);

        return mcpToken;
    }

    @Override
    public Integer validate(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String cacheKey = CACHE_PREFIX + token;

        // 1. Redis 缓存查询
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (DISABLED_VALUE.equals(cached)) {
                return null;
            }
            return Integer.valueOf(cached);
        }

        // 2. 缓存未命中，查 DB
        SysMcpToken mcpToken = mcpTokenMapper.selectByToken(token);
        if (mcpToken == null) {
            // 防穿透：缓存空值短时间
            stringRedisTemplate.opsForValue().set(cacheKey, DISABLED_VALUE, 5, TimeUnit.MINUTES);
            return null;
        }

        if (mcpToken.getEnabled() == null || mcpToken.getEnabled() != 1) {
            stringRedisTemplate.opsForValue().set(cacheKey, DISABLED_VALUE, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            return null;
        }

        // 3. 写入缓存
        stringRedisTemplate.opsForValue().set(
                cacheKey, String.valueOf(mcpToken.getUserId()), CACHE_EXPIRE_DAYS, TimeUnit.DAYS);

        return mcpToken.getUserId();
    }

    @Override
    public List<SysMcpToken> list(Integer userId) {
        return mcpTokenMapper.selectByUserId(userId);
    }

    @Override
    public int enable(Long id, Integer userId) {
        SysMcpToken existing = mcpTokenMapper.selectByIdAndUserId(id, userId);
        if (existing == null) {
            return 0;
        }
        int rows = mcpTokenMapper.updateEnabled(id, 1);
        if (rows > 0) {
            // 更新缓存
            stringRedisTemplate.opsForValue().set(
                    CACHE_PREFIX + existing.getToken(), String.valueOf(userId), CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        }
        return rows;
    }

    @Override
    public int disable(Long id, Integer userId) {
        SysMcpToken existing = mcpTokenMapper.selectByIdAndUserId(id, userId);
        if (existing == null) {
            return 0;
        }
        int rows = mcpTokenMapper.updateEnabled(id, 0);
        if (rows > 0) {
            // 清缓存，标记为禁用
            stringRedisTemplate.opsForValue().set(
                    CACHE_PREFIX + existing.getToken(), DISABLED_VALUE, CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            // 断开使用该Token的活跃连接
            mcpEndpointSessionManager.disconnectByToken(existing.getToken());
        }
        return rows;
    }

    @Override
    public int delete(Long id, Integer userId) {
        SysMcpToken existing = mcpTokenMapper.selectByIdAndUserId(id, userId);
        if (existing == null) {
            return 0;
        }
        int rows = mcpTokenMapper.delete(id, userId);
        if (rows > 0) {
            // 清缓存
            stringRedisTemplate.delete(CACHE_PREFIX + existing.getToken());
            // 断开使用该Token的活跃连接
            mcpEndpointSessionManager.disconnectByToken(existing.getToken());
        }
        return rows;
    }

    @Async
    @Override
    public void updateLastUsedAtAsync(String token) {
        try {
            mcpTokenMapper.updateLastUsedAt(token);
        } catch (Exception e) {
            logger.warn("更新MCP Token最后使用时间失败: {}", e.getMessage());
        }
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("mcp_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
