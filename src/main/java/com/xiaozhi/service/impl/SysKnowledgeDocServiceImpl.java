package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.KnowledgeDocMapper;
import com.xiaozhi.entity.SysKnowledgeDoc;
import com.xiaozhi.service.SysKnowledgeDocService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 知识库文档 服务实现
 *
 * @author xiaozhi
 */
@Service
public class SysKnowledgeDocServiceImpl extends BaseServiceImpl implements SysKnowledgeDocService {

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Override
    public List<SysKnowledgeDoc> query(SysKnowledgeDoc doc, PageFilter pageFilter) {
        if (pageFilter != null) {
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return knowledgeDocMapper.query(doc);
    }

    @Override
    public SysKnowledgeDoc selectById(Long docId) {
        return knowledgeDocMapper.selectById(docId);
    }

    @Override
    @Transactional
    public int add(SysKnowledgeDoc doc) {
        return knowledgeDocMapper.add(doc);
    }

    @Override
    @Transactional
    public int update(SysKnowledgeDoc doc) {
        return knowledgeDocMapper.update(doc);
    }

    @Override
    @Transactional
    public int deleteById(Long docId) {
        return knowledgeDocMapper.deleteById(docId);
    }

    @Override
    public List<SysKnowledgeDoc> selectByKnowledgeBaseId(Long knowledgeBaseId) {
        return knowledgeDocMapper.selectByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<SysKnowledgeDoc> selectByKnowledgeBaseIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return knowledgeDocMapper.selectByKnowledgeBaseIds(ids);
    }
}
