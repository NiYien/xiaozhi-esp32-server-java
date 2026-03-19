package com.xiaozhi.dao;

import com.xiaozhi.entity.SysVoiceClone;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 音色克隆 数据层
 */
public interface VoiceCloneMapper {

    /**
     * 新增音色克隆记录
     */
    int insert(SysVoiceClone voiceClone);

    /**
     * 更新音色克隆记录
     */
    int update(SysVoiceClone voiceClone);

    /**
     * 根据ID查询
     */
    SysVoiceClone selectById(@Param("cloneId") Integer cloneId);

    /**
     * 根据用户ID查询列表
     */
    List<SysVoiceClone> selectByUserId(@Param("userId") Integer userId);

    /**
     * 查询指定状态的记录
     */
    List<SysVoiceClone> selectByStatus(@Param("status") String status);

    /**
     * 统计用户克隆数量
     */
    int countByUserId(@Param("userId") Integer userId);

    /**
     * 删除记录
     */
    int deleteById(@Param("cloneId") Integer cloneId);
}
