package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysDeviceGroup;
import com.xiaozhi.entity.SysDeviceGroupMember;

import java.util.List;

/**
 * 设备分组服务
 */
public interface SysDeviceGroupService {

    /**
     * 查询分组列表
     */
    List<SysDeviceGroup> query(SysDeviceGroup group, PageFilter pageFilter);

    /**
     * 根据ID查询分组
     */
    SysDeviceGroup selectById(Integer groupId);

    /**
     * 创建分组
     */
    int create(SysDeviceGroup group);

    /**
     * 更新分组
     */
    int update(SysDeviceGroup group);

    /**
     * 删除分组（同时删除成员关系）
     */
    int delete(SysDeviceGroup group);

    /**
     * 添加设备到分组
     */
    int addMember(SysDeviceGroupMember member);

    /**
     * 从分组中移除设备
     */
    int removeMember(SysDeviceGroupMember member);

    /**
     * 获取分组内的设备ID列表
     */
    List<String> getDeviceIds(Integer groupId);

    /**
     * 查询设备所属的分组列表
     */
    List<SysDeviceGroup> getGroupsByDeviceId(String deviceId);

    /**
     * 查询用户的分组列表（含设备数量，用于Function Call）
     */
    List<SysDeviceGroup> queryByUserId(Integer userId);
}
