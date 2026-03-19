package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysFirmware;

import java.util.List;

/**
 * 固件管理服务
 *
 * @author Joey
 */
public interface SysFirmwareService {

    /**
     * 分页查询固件列表
     */
    List<SysFirmware> query(SysFirmware firmware, PageFilter pageFilter);

    /**
     * 根据ID查询固件
     */
    SysFirmware selectById(Integer firmwareId);

    /**
     * 新增固件
     */
    int add(SysFirmware firmware);

    /**
     * 更新固件信息
     */
    int update(SysFirmware firmware);

    /**
     * 删除固件
     */
    int delete(Integer firmwareId);

    /**
     * 设置固件为默认
     */
    int setDefault(Integer firmwareId);

    /**
     * 按芯片型号和设备类型匹配固件（D2优先级策略）
     */
    SysFirmware matchFirmware(String chipModelName, String deviceType);
}
