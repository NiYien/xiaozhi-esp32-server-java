package com.xiaozhi.controller;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.entity.SysDeviceGroup;
import com.xiaozhi.entity.SysDeviceGroupMember;
import com.xiaozhi.service.SysDeviceGroupService;
import com.xiaozhi.utils.CmsUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备分组管理
 */
@RestController
@RequestMapping("/api/deviceGroup")
@Tag(name = "设备分组管理", description = "设备分组相关操作")
public class DeviceGroupController extends BaseController {

    @Resource
    private SysDeviceGroupService deviceGroupService;

    /**
     * 查询分组列表
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "查询设备分组列表", description = "返回当前用户的设备分组信息")
    public ResultMessage query(SysDeviceGroup group, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            group.setUserId(CmsUtils.getUserId());
            List<SysDeviceGroup> list = deviceGroupService.query(group, pageFilter);
            ResultMessage result = ResultMessage.success();
            result.put("data", list);
            return result;
        } catch (Exception e) {
            logger.error("查询设备分组失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 获取分组详情（含设备列表）
     */
    @GetMapping("/{groupId}")
    @ResponseBody
    @Operation(summary = "获取分组详情", description = "返回分组信息及包含的设备列表")
    public ResultMessage detail(@PathVariable Integer groupId) {
        try {
            SysDeviceGroup group = deviceGroupService.selectById(groupId);
            if (group == null) {
                return ResultMessage.error("分组不存在");
            }
            // 验证权限
            if (!group.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权访问此分组");
            }
            // 填充设备ID列表
            List<String> deviceIds = deviceGroupService.getDeviceIds(groupId);
            group.setDeviceCount(deviceIds.size());
            return ResultMessage.success(group);
        } catch (Exception e) {
            logger.error("获取分组详情失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 创建分组
     */
    @PostMapping("")
    @ResponseBody
    @Operation(summary = "创建设备分组", description = "创建一个新的设备分组")
    public ResultMessage create(@RequestBody SysDeviceGroup group) {
        try {
            group.setUserId(CmsUtils.getUserId());
            int row = deviceGroupService.create(group);
            if (row > 0) {
                return ResultMessage.success(group);
            }
            return ResultMessage.error("创建失败");
        } catch (Exception e) {
            logger.error("创建设备分组失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 更新分组
     */
    @PutMapping("/{groupId}")
    @ResponseBody
    @Operation(summary = "更新设备分组", description = "更新分组名称、描述等信息")
    public ResultMessage update(@PathVariable Integer groupId, @RequestBody SysDeviceGroup group) {
        try {
            group.setGroupId(groupId);
            group.setUserId(CmsUtils.getUserId());
            int row = deviceGroupService.update(group);
            if (row > 0) {
                return ResultMessage.success("更新成功");
            }
            return ResultMessage.error("更新失败");
        } catch (Exception e) {
            logger.error("更新设备分组失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除分组
     */
    @DeleteMapping("/{groupId}")
    @ResponseBody
    @Operation(summary = "删除设备分组", description = "删除分组及其成员关系")
    public ResultMessage delete(@PathVariable Integer groupId) {
        try {
            SysDeviceGroup group = new SysDeviceGroup();
            group.setGroupId(groupId);
            group.setUserId(CmsUtils.getUserId());
            int row = deviceGroupService.delete(group);
            if (row > 0) {
                return ResultMessage.success("删除成功");
            }
            return ResultMessage.error("删除失败");
        } catch (Exception e) {
            logger.error("删除设备分组失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 添加设备到分组
     */
    @PostMapping("/{groupId}/member")
    @ResponseBody
    @Operation(summary = "添加设备到分组", description = "将设备添加到指定分组")
    public ResultMessage addMember(@PathVariable Integer groupId, @RequestBody SysDeviceGroupMember member) {
        try {
            // 验证分组归属
            SysDeviceGroup group = deviceGroupService.selectById(groupId);
            if (group == null || !group.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权操作此分组");
            }
            member.setGroupId(groupId);
            int row = deviceGroupService.addMember(member);
            if (row > 0) {
                return ResultMessage.success("添加成功");
            }
            return ResultMessage.error("添加失败，设备可能已在分组中");
        } catch (Exception e) {
            logger.error("添加设备到分组失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 从分组移除设备
     */
    @DeleteMapping("/{groupId}/member/{deviceId}")
    @ResponseBody
    @Operation(summary = "从分组移除设备", description = "将设备从指定分组中移除")
    public ResultMessage removeMember(@PathVariable Integer groupId, @PathVariable String deviceId) {
        try {
            // 验证分组归属
            SysDeviceGroup group = deviceGroupService.selectById(groupId);
            if (group == null || !group.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权操作此分组");
            }
            SysDeviceGroupMember member = new SysDeviceGroupMember();
            member.setGroupId(groupId);
            member.setDeviceId(deviceId);
            int row = deviceGroupService.removeMember(member);
            if (row > 0) {
                return ResultMessage.success("移除成功");
            }
            return ResultMessage.error("移除失败");
        } catch (Exception e) {
            logger.error("从分组移除设备失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 获取分组内的设备列表
     */
    @GetMapping("/{groupId}/devices")
    @ResponseBody
    @Operation(summary = "获取分组内设备列表", description = "返回分组内的所有设备ID")
    public ResultMessage getDevices(@PathVariable Integer groupId) {
        try {
            // 验证分组归属
            SysDeviceGroup group = deviceGroupService.selectById(groupId);
            if (group == null || !group.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权访问此分组");
            }
            List<String> deviceIds = deviceGroupService.getDeviceIds(groupId);
            ResultMessage result = ResultMessage.success();
            result.put("data", deviceIds);
            return result;
        } catch (Exception e) {
            logger.error("获取分组设备列表失败", e);
            return ResultMessage.error();
        }
    }
}
