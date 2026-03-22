package com.xiaozhi.service;

import com.xiaozhi.entity.SysMcpToken;

import java.util.List;

/**
 * MCP Token 服务接口
 */
public interface McpTokenService {

    /**
     * 生成新Token
     *
     * @param userId    用户ID
     * @param tokenName Token名称
     * @return 包含完整token值的实体
     */
    SysMcpToken generate(Integer userId, String tokenName);

    /**
     * 验证Token，返回userId，验证失败返回null
     */
    Integer validate(String token);

    /**
     * 查询用户的Token列表
     */
    List<SysMcpToken> list(Integer userId);

    /**
     * 启用Token
     */
    int enable(Long id, Integer userId);

    /**
     * 禁用Token
     */
    int disable(Long id, Integer userId);

    /**
     * 删除Token
     */
    int delete(Long id, Integer userId);

    /**
     * 异步更新最后使用时间
     */
    void updateLastUsedAtAsync(String token);
}
