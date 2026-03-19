package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysKnowledgeDoc;

import java.util.List;

/**
 * 知识库文档 服务接口
 *
 * @author xiaozhi
 */
public interface SysKnowledgeDocService {

    /**
     * 查询文档列表
     */
    List<SysKnowledgeDoc> query(SysKnowledgeDoc doc, PageFilter pageFilter);

    /**
     * 根据ID查询文档
     */
    SysKnowledgeDoc selectById(Long docId);

    /**
     * 新增文档
     */
    int add(SysKnowledgeDoc doc);

    /**
     * 更新文档
     */
    int update(SysKnowledgeDoc doc);

    /**
     * 删除文档
     */
    int deleteById(Long docId);

    /**
     * 根据知识库ID查询已就绪的文档列表
     */
    List<SysKnowledgeDoc> selectByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 根据知识库ID列表查询已就绪的文档列表
     */
    List<SysKnowledgeDoc> selectByKnowledgeBaseIds(List<Long> ids);
}
