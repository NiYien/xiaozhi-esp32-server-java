package com.xiaozhi.dao;

import com.xiaozhi.entity.SysUserMemory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户长期记忆 Mapper 接口
 */
public interface UserMemoryMapper {

    /**
     * 批量插入记忆
     */
    int batchInsert(@Param("list") List<SysUserMemory> memories);

    /**
     * 根据用户ID查询有效记忆（最多返回limit条）
     */
    List<SysUserMemory> findByUserId(@Param("userId") int userId, @Param("limit") int limit);

    /**
     * 根据用户ID和分类查询有效记忆
     */
    List<SysUserMemory> findByUserIdAndCategory(@Param("userId") int userId, @Param("category") String category);

    /**
     * 分页查询用户记忆（管理后台用）
     */
    List<SysUserMemory> query(SysUserMemory memory);

    /**
     * 逻辑删除记忆
     */
    int deleteById(@Param("memoryId") long memoryId, @Param("userId") int userId);

    /**
     * 更新记忆内容
     */
    int updateContent(@Param("memoryId") long memoryId, @Param("userId") int userId,
                      @Param("content") String content, @Param("category") String category);

    /**
     * 查询用户所有有效记忆的内容（用于去重判断）
     */
    List<String> findAllContentByUserId(@Param("userId") int userId);
}
