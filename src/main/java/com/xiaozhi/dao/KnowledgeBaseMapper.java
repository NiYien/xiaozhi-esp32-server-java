package com.xiaozhi.dao;

import com.xiaozhi.entity.SysKnowledgeBase;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库 数据层
 *
 * @author xiaozhi
 */
public interface KnowledgeBaseMapper {

    /**
     * 查询知识库列表
     */
    List<SysKnowledgeBase> query(SysKnowledgeBase knowledgeBase);

    /**
     * 根据ID查询知识库
     */
    SysKnowledgeBase selectById(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 新增知识库
     */
    int add(SysKnowledgeBase knowledgeBase);

    /**
     * 更新知识库
     */
    int update(SysKnowledgeBase knowledgeBase);

    /**
     * 删除知识库（逻辑删除）
     */
    int deleteById(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 根据ID列表查询知识库
     */
    List<SysKnowledgeBase> selectByIds(@Param("ids") List<Long> ids);
}
