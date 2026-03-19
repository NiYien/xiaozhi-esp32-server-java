package com.xiaozhi.dao;

import com.xiaozhi.entity.SysDeviceGroup;
import com.xiaozhi.entity.SysDeviceGroupMember;

import java.util.List;

/**
 * 设备分组 数据层
 */
public interface DeviceGroupMapper {

    /**
     * 查询分组列表
     */
    List<SysDeviceGroup> query(SysDeviceGroup group);

    /**
     * 根据分组ID查询
     */
    SysDeviceGroup selectById(Integer groupId);

    /**
     * 新增分组
     */
    int insert(SysDeviceGroup group);

    /**
     * 更新分组
     */
    int update(SysDeviceGroup group);

    /**
     * 删除分组
     */
    int delete(SysDeviceGroup group);

    /**
     * 添加分组成员
     */
    int addMember(SysDeviceGroupMember member);

    /**
     * 移除分组成员
     */
    int removeMember(SysDeviceGroupMember member);

    /**
     * 查询分组内的设备ID列表
     */
    List<String> selectDeviceIdsByGroupId(Integer groupId);

    /**
     * 查询设备所属的分组列表
     */
    List<SysDeviceGroup> selectGroupsByDeviceId(String deviceId);

    /**
     * 删除分组的所有成员
     */
    int removeAllMembers(Integer groupId);

    /**
     * 查询用户的分组列表（含设备数量）
     */
    List<SysDeviceGroup> queryWithDeviceCount(SysDeviceGroup group);
}
