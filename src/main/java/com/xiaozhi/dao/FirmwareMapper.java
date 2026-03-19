package com.xiaozhi.dao;

import com.xiaozhi.entity.SysFirmware;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 固件信息 数据层
 *
 */
public interface FirmwareMapper {

    List<SysFirmware> query(SysFirmware firmware);

    SysFirmware selectById(@Param("firmwareId") Integer firmwareId);

    int insert(SysFirmware firmware);

    int update(SysFirmware firmware);

    int deleteById(@Param("firmwareId") Integer firmwareId);

    /**
     * 精确匹配：chipModelName + deviceType 的默认固件
     */
    SysFirmware findDefaultByChipAndType(@Param("chipModelName") String chipModelName,
                                          @Param("deviceType") String deviceType);

    /**
     * 仅匹配 chipModelName 的默认固件（deviceType 为 NULL）
     */
    SysFirmware findDefaultByChip(@Param("chipModelName") String chipModelName);

    /**
     * 全局默认固件（chipModelName 和 deviceType 均为 NULL）
     */
    SysFirmware findGlobalDefault();

    /**
     * 取消同组固件的默认标记
     */
    int clearDefaultByChipAndType(@Param("chipModelName") String chipModelName,
                                   @Param("deviceType") String deviceType);
}
