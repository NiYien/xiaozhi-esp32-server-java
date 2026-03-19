package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.DeviceGroupMapper;
import com.xiaozhi.entity.SysDeviceGroup;
import com.xiaozhi.entity.SysDeviceGroupMember;
import com.xiaozhi.service.SysDeviceGroupService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备分组服务实现
 */
@Service
public class SysDeviceGroupServiceImpl extends BaseServiceImpl implements SysDeviceGroupService {
    private static final Logger logger = LoggerFactory.getLogger(SysDeviceGroupServiceImpl.class);

    @Resource
    private DeviceGroupMapper deviceGroupMapper;

    @Override
    public List<SysDeviceGroup> query(SysDeviceGroup group, PageFilter pageFilter) {
        if (pageFilter != null) {
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return deviceGroupMapper.query(group);
    }

    @Override
    public SysDeviceGroup selectById(Integer groupId) {
        return deviceGroupMapper.selectById(groupId);
    }

    @Override
    public int create(SysDeviceGroup group) {
        if (group.getState() == null) {
            group.setState("1");
        }
        return deviceGroupMapper.insert(group);
    }

    @Override
    public int update(SysDeviceGroup group) {
        return deviceGroupMapper.update(group);
    }

    @Override
    @Transactional
    public int delete(SysDeviceGroup group) {
        // 先删除分组成员关系
        deviceGroupMapper.removeAllMembers(group.getGroupId());
        // 再删除分组
        return deviceGroupMapper.delete(group);
    }

    @Override
    public int addMember(SysDeviceGroupMember member) {
        return deviceGroupMapper.addMember(member);
    }

    @Override
    public int removeMember(SysDeviceGroupMember member) {
        return deviceGroupMapper.removeMember(member);
    }

    @Override
    public List<String> getDeviceIds(Integer groupId) {
        return deviceGroupMapper.selectDeviceIdsByGroupId(groupId);
    }

    @Override
    public List<SysDeviceGroup> getGroupsByDeviceId(String deviceId) {
        return deviceGroupMapper.selectGroupsByDeviceId(deviceId);
    }

    @Override
    public List<SysDeviceGroup> queryByUserId(Integer userId) {
        SysDeviceGroup query = new SysDeviceGroup();
        query.setUserId(userId);
        query.setState("1");
        return deviceGroupMapper.queryWithDeviceCount(query);
    }
}
