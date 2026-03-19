package com.xiaozhi.service;

import com.xiaozhi.entity.SysUserMemory;

import java.util.List;

/**
 * 用户长期记忆服务接口
 */
public interface SysUserMemoryService {

    /**
     * 从对话内容中异步提取并保存用户记忆
     *
     * @param userId       用户ID
     * @param roleId       角色ID（用于获取ChatModel）
     * @param conversation 对话内容文本
     */
    void extractAndSaveAsync(int userId, int roleId, String conversation);

    /**
     * 查询用户的有效记忆（最多20条，用于注入对话上下文）
     *
     * @param userId 用户ID
     * @return 记忆列表
     */
    List<SysUserMemory> findForContext(int userId);

    /**
     * 将记忆列表格式化为可注入到系统提示词的文本
     *
     * @param memories 记忆列表
     * @return 格式化后的文本
     */
    String formatMemoriesForPrompt(List<SysUserMemory> memories);

    /**
     * 分页查询用户记忆（管理后台用）
     *
     * @param memory 查询条件
     * @return 记忆列表
     */
    List<SysUserMemory> query(SysUserMemory memory);

    /**
     * 逻辑删除记忆
     *
     * @param memoryId 记忆ID
     * @param userId   用户ID
     * @return 影响行数
     */
    int delete(long memoryId, int userId);

    /**
     * 更新记忆内容
     *
     * @param memoryId 记忆ID
     * @param userId   用户ID
     * @param content  新内容
     * @param category 新分类
     * @return 影响行数
     */
    int update(long memoryId, int userId, String content, String category);
}
