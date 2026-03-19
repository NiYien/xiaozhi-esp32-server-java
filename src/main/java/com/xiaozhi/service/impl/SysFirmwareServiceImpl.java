package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.FirmwareMapper;
import com.xiaozhi.entity.SysFirmware;
import com.xiaozhi.service.SysFirmwareService;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 固件管理服务实现
 *
 */
@Service
public class SysFirmwareServiceImpl extends BaseServiceImpl implements SysFirmwareService {

    private static final Logger logger = LoggerFactory.getLogger(SysFirmwareServiceImpl.class);

    @Resource
    private FirmwareMapper firmwareMapper;

    @Override
    public List<SysFirmware> query(SysFirmware firmware, PageFilter pageFilter) {
        if (pageFilter != null) {
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        return firmwareMapper.query(firmware);
    }

    @Override
    public SysFirmware selectById(Integer firmwareId) {
        return firmwareMapper.selectById(firmwareId);
    }

    @Override
    @Transactional
    public int add(SysFirmware firmware) {
        // 如果设置为默认，先取消同组的默认标记
        if ("1".equals(firmware.getIsDefault())) {
            firmwareMapper.clearDefaultByChipAndType(firmware.getChipModelName(), firmware.getDeviceType());
        }
        return firmwareMapper.insert(firmware);
    }

    @Override
    @Transactional
    public int update(SysFirmware firmware) {
        // 如果设置为默认，先取消同组的默认标记
        if ("1".equals(firmware.getIsDefault())) {
            SysFirmware existing = firmwareMapper.selectById(firmware.getFirmwareId());
            if (existing != null) {
                firmwareMapper.clearDefaultByChipAndType(existing.getChipModelName(), existing.getDeviceType());
            }
        }
        return firmwareMapper.update(firmware);
    }

    @Override
    public int delete(Integer firmwareId) {
        return firmwareMapper.deleteById(firmwareId);
    }

    @Override
    @Transactional
    public int setDefault(Integer firmwareId) {
        SysFirmware firmware = firmwareMapper.selectById(firmwareId);
        if (firmware == null) {
            return 0;
        }
        // 先取消同组的默认标记
        firmwareMapper.clearDefaultByChipAndType(firmware.getChipModelName(), firmware.getDeviceType());
        // 设置当前固件为默认
        SysFirmware updateFirmware = new SysFirmware();
        updateFirmware.setFirmwareId(firmwareId);
        updateFirmware.setIsDefault("1");
        return firmwareMapper.update(updateFirmware);
    }

    @Override
    public SysFirmware matchFirmware(String chipModelName, String deviceType) {
        // 优先级1：精确匹配 chipModelName + deviceType
        if (chipModelName != null && deviceType != null) {
            SysFirmware firmware = firmwareMapper.findDefaultByChipAndType(chipModelName, deviceType);
            if (firmware != null) {
                return firmware;
            }
        }

        // 优先级2：仅匹配 chipModelName（deviceType 为 NULL）
        if (chipModelName != null) {
            SysFirmware firmware = firmwareMapper.findDefaultByChip(chipModelName);
            if (firmware != null) {
                return firmware;
            }
        }

        // 优先级3：全局默认固件
        SysFirmware firmware = firmwareMapper.findGlobalDefault();
        if (firmware != null) {
            return firmware;
        }

        // 优先级4：无匹配，返回null（调用方回退到硬编码 1.0.0）
        return null;
    }
}
