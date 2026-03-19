package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysKnowledgeBase;

import java.util.List;

/**
 * 知识库 服务接口
 *
 * @author xiaozhi
 */
public interface SysKnowledgeBaseService {

    /**
     * 创建知识库
     */
    int createKnowledgeBase(SysKnowledgeBase knowledgeBase);

    /**
     * 删除知识库（逻辑删除）
     */
    int deleteKnowledgeBase(Long knowledgeBaseId);

    /**
     * 查询知识库列表
     */
    List<SysKnowledgeBase> listKnowledgeBases(SysKnowledgeBase knowledgeBase, PageFilter pageFilter);

    /**
     * 根据ID查询知识库
     */
    SysKnowledgeBase selectById(Long knowledgeBaseId);

    /**
     * 更新知识库
     */
    int updateKnowledgeBase(SysKnowledgeBase knowledgeBase);

    /**
     * 获取角色关联的知识库列表
     *
     * @param knowledgeBaseIds 逗号分隔的知识库ID字符串
     * @return 知识库列表
     */
    List<SysKnowledgeBase> getLinkedKnowledgeBases(String knowledgeBaseIds);
}
