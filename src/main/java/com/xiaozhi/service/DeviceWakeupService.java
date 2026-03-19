package com.xiaozhi.service;

import com.xiaozhi.dao.DeviceWakeupMapper;
import com.xiaozhi.entity.SysDeviceWakeup;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 设备唤醒词-角色映射服务
 * 管理唤醒词与角色的映射关系，提供缓存加速查询
 */
@Service
public class DeviceWakeupService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceWakeupService.class);

    @Resource
    private DeviceWakeupMapper deviceWakeupMapper;

    /**
     * 缓存：deviceId → {wakeupWord → roleId}
     */
    private final ConcurrentHashMap<String, Map<String, Integer>> cache = new ConcurrentHashMap<>();

    /**
     * 新增唤醒词映射
     */
    public int addMapping(String deviceId, String wakeupWord, Integer roleId, Integer priority) {
        SysDeviceWakeup wakeup = new SysDeviceWakeup();
        wakeup.setDeviceId(deviceId);
        wakeup.setWakeupWord(wakeupWord);
        wakeup.setRoleId(roleId);
        wakeup.setPriority(priority != null ? priority : 1);
        int result = deviceWakeupMapper.insert(wakeup);
        // 刷新缓存
        invalidateCache(deviceId);
        return result;
    }

    /**
     * 更新唤醒词映射
     */
    public int updateMapping(Integer id, Integer roleId, Integer priority) {
        SysDeviceWakeup wakeup = new SysDeviceWakeup();
        wakeup.setId(id);
        wakeup.setRoleId(roleId);
        wakeup.setPriority(priority);
        int result = deviceWakeupMapper.update(wakeup);
        // 清除所有缓存（因为不知道 deviceId，简单处理）
        cache.clear();
        return result;
    }

    /**
     * 删除唤醒词映射
     */
    public int deleteMapping(Integer id) {
        int result = deviceWakeupMapper.delete(id);
        // 清除所有缓存
        cache.clear();
        return result;
    }

    /**
     * 查询设备所有唤醒词映射
     */
    public List<SysDeviceWakeup> getMappings(String deviceId) {
        return deviceWakeupMapper.queryByDevice(deviceId);
    }

    /**
     * 核心方法：根据唤醒词查找对应的 roleId
     * 优先从缓存查询，未命中则从数据库加载并缓存
     *
     * @param deviceId   设备ID
     * @param wakeupWord 唤醒词文本
     * @return 匹配的 roleId，未匹配返回 null（由调用方回退到 sys_device.roleId）
     */
    public Integer getRoleIdByWakeupWord(String deviceId, String wakeupWord) {
        Map<String, Integer> wordMap = cache.computeIfAbsent(deviceId, this::loadMappings);
        return wordMap.get(wakeupWord);
    }

    /**
     * 从数据库加载设备的唤醒词映射
     */
    private Map<String, Integer> loadMappings(String deviceId) {
        List<SysDeviceWakeup> mappings = deviceWakeupMapper.queryByDevice(deviceId);
        Map<String, Integer> wordMap = mappings.stream()
                .collect(Collectors.toConcurrentMap(
                        SysDeviceWakeup::getWakeupWord,
                        SysDeviceWakeup::getRoleId,
                        (existing, replacement) -> existing
                ));
        logger.debug("加载设备 {} 的唤醒词映射，共 {} 条", deviceId, wordMap.size());
        return wordMap;
    }

    /**
     * 清除指定设备的缓存
     */
    public void invalidateCache(String deviceId) {
        cache.remove(deviceId);
    }
}
