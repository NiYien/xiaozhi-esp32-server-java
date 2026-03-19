package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.KnowledgeBaseMapper;
import com.xiaozhi.entity.SysKnowledgeBase;
import com.xiaozhi.service.SysKnowledgeBaseService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库 服务实现
 *
 * @author xiaozhi
 */
@Service
public class SysKnowledgeBaseServiceImpl extends BaseServiceImpl implements SysKnowledgeBaseService {

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    @Transactional
    public int createKnowledgeBase(SysKnowledgeBase knowledgeBase) {
        return knowledgeBaseMapper.add(knowledgeBase);
    }

    @Override
    @Transactional
    public int deleteKnowledgeBase(Long knowledgeBaseId) {
        return knowledgeBaseMapper.deleteById(knowledgeBaseId);
    }

    @Override
    public List<SysKnowledgeBase> listKnowledgeBases(SysKnowledgeBase knowledgeBase, PageFilter pageFilter) {
        if (pageFilter != null) {
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return knowledgeBaseMapper.query(knowledgeBase);
    }

    @Override
    public SysKnowledgeBase selectById(Long knowledgeBaseId) {
        return knowledgeBaseMapper.selectById(knowledgeBaseId);
    }

    @Override
    @Transactional
    public int updateKnowledgeBase(SysKnowledgeBase knowledgeBase) {
        return knowledgeBaseMapper.update(knowledgeBase);
    }

    @Override
    public List<SysKnowledgeBase> getLinkedKnowledgeBases(String knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isBlank()) {
            return Collections.emptyList();
        }
        List<Long> ids = Arrays.stream(knowledgeBaseIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return knowledgeBaseMapper.selectByIds(ids);
    }
}
