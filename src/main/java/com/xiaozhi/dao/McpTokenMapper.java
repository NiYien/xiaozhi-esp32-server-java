package com.xiaozhi.dao;

import com.xiaozhi.entity.SysMcpToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MCP Token 数据层
 */
@Mapper
public interface McpTokenMapper {

    /**
     * 按 token 值查询
     */
    SysMcpToken selectByToken(@Param("token") String token);

    /**
     * 按用户ID查询列表
     */
    List<SysMcpToken> selectByUserId(@Param("userId") Integer userId);

    /**
     * 统计用户Token数量
     */
    int countByUserId(@Param("userId") Integer userId);

    /**
     * 新增
     */
    int add(SysMcpToken mcpToken);

    /**
     * 更新启用状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);

    /**
     * 更新最后使用时间
     */
    int updateLastUsedAt(@Param("token") String token);

    /**
     * 删除
     */
    int delete(@Param("id") Long id, @Param("userId") Integer userId);

    /**
     * 按ID和用户ID查询
     */
    SysMcpToken selectByIdAndUserId(@Param("id") Long id, @Param("userId") Integer userId);
}
