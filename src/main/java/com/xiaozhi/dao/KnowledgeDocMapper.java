package com.xiaozhi.dao;

import com.xiaozhi.entity.SysKnowledgeDoc;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档 数据层
 *
 * @author xiaozhi
 */
public interface KnowledgeDocMapper {

    /**
     * 查询文档列表
     */
    List<SysKnowledgeDoc> query(SysKnowledgeDoc doc);

    /**
     * 根据ID查询文档
     */
    SysKnowledgeDoc selectById(@Param("docId") Long docId);

    /**
     * 新增文档
     */
    int add(SysKnowledgeDoc doc);

    /**
     * 更新文档
     */
    int update(SysKnowledgeDoc doc);

    /**
     * 删除文档（逻辑删除）
     */
    int deleteById(@Param("docId") Long docId);

    /**
     * 根据知识库ID查询已就绪的文档列表
     */
    List<SysKnowledgeDoc> selectByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);

    /**
     * 根据知识库ID列表查询已就绪的文档列表
     */
    List<SysKnowledgeDoc> selectByKnowledgeBaseIds(@Param("ids") List<Long> ids);
}
