package com.xiaozhi.dialogue.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 多 Persona 缓存注册表
 * 按 roleId 缓存已创建的 Persona 实例，支持 LRU 淘汰。
 * 同一时刻只有一个 Persona 处于活跃状态。
 */
public class PersonaRegistry {

    private static final Logger logger = LoggerFactory.getLogger(PersonaRegistry.class);

    /**
     * 每个设备最多缓存的 Persona 数量
     */
    private final int maxSize;

    /**
     * 使用 LinkedHashMap(accessOrder=true) 实现 LRU 淘汰
     * 最近访问的条目放在末尾，最久未使用的在头部
     */
    private final LinkedHashMap<Integer, Persona> personas;

    /**
     * 当前活跃的 Persona
     */
    private volatile Persona activePersona;

    public PersonaRegistry() {
        this(5);
    }

    public PersonaRegistry(int maxSize) {
        this.maxSize = maxSize;
        // accessOrder=true 使 get/put 操作将条目移到末尾（最近使用）
        this.personas = new LinkedHashMap<>(maxSize + 1, 0.75f, true);
    }

    /**
     * 获取或创建指定角色的 Persona
     * 如果缓存中已有则直接返回，否则通过工厂创建并缓存
     *
     * @param roleId  角色ID
     * @param factory Persona 工厂方法
     * @return Persona 实例
     */
    public synchronized Persona getOrCreate(int roleId, Supplier<Persona> factory) {
        Persona persona = personas.get(roleId);
        if (persona != null) {
            logger.debug("从缓存获取 Persona，roleId: {}", roleId);
            return persona;
        }

        // 创建新的 Persona
        persona = factory.get();
        if (persona != null) {
            // 超出容量限制，淘汰最久未使用的非活跃 Persona
            evictIfNeeded();
            personas.put(roleId, persona);
            logger.info("创建新的 Persona 并缓存，roleId: {}，当前缓存数: {}", roleId, personas.size());
        }
        return persona;
    }

    /**
     * 激活指定角色的 Persona
     *
     * @param roleId 角色ID
     * @throws IllegalArgumentException 如果缓存中不存在该角色
     */
    public synchronized void activate(int roleId) {
        Persona persona = personas.get(roleId);
        if (persona == null) {
            throw new IllegalArgumentException("PersonaRegistry 中不存在 roleId: " + roleId);
        }
        this.activePersona = persona;
        logger.info("激活 Persona，roleId: {}", roleId);
    }

    /**
     * 获取当前活跃的 Persona
     *
     * @return 当前活跃的 Persona，可能为 null
     */
    public synchronized Persona getActive() {
        return activePersona;
    }

    /**
     * 设置活跃的 Persona（直接设置，用于向后兼容 setPersona 场景）
     *
     * @param persona Persona 实例，可为 null（清除活跃状态）
     */
    public synchronized void setActive(Persona persona) {
        this.activePersona = persona;
    }

    /**
     * 将 Persona 放入缓存并激活
     *
     * @param roleId  角色ID
     * @param persona Persona 实例
     */
    public synchronized void putAndActivate(int roleId, Persona persona) {
        evictIfNeeded();
        personas.put(roleId, persona);
        this.activePersona = persona;
        logger.info("放入并激活 Persona，roleId: {}，当前缓存数: {}", roleId, personas.size());
    }

    /**
     * 释放所有缓存的 Persona
     */
    public synchronized void releaseAll() {
        for (Map.Entry<Integer, Persona> entry : personas.entrySet()) {
            try {
                Persona persona = entry.getValue();
                if (persona.getSynthesizer() != null) {
                    persona.getSynthesizer().cancel();
                }
                if (persona.getPlayer() != null) {
                    persona.getPlayer().stop();
                }
                if (persona.getConversation() != null) {
                    persona.getConversation().clear();
                }
            } catch (Exception e) {
                logger.warn("释放 Persona 资源异常，roleId: {}", entry.getKey(), e);
            }
        }
        personas.clear();
        activePersona = null;
        logger.info("已释放所有缓存的 Persona");
    }

    /**
     * 获取当前缓存数量
     */
    public synchronized int size() {
        return personas.size();
    }

    /**
     * 淘汰最久未使用的非活跃 Persona（如果已达到容量上限）
     */
    private void evictIfNeeded() {
        while (personas.size() >= maxSize) {
            // LinkedHashMap(accessOrder=true) 的迭代器顺序：最久未访问在前
            Integer evictKey = null;
            Persona evictPersona = null;
            for (Map.Entry<Integer, Persona> entry : personas.entrySet()) {
                // 不淘汰当前活跃的 Persona
                if (entry.getValue() != activePersona) {
                    evictKey = entry.getKey();
                    evictPersona = entry.getValue();
                    break;
                }
            }
            if (evictKey == null) {
                // 所有都是活跃的（不太可能），强制跳出
                logger.warn("PersonaRegistry 已满且无法淘汰非活跃 Persona");
                break;
            }
            // 释放被淘汰的 Persona 资源
            try {
                if (evictPersona.getSynthesizer() != null) {
                    evictPersona.getSynthesizer().cancel();
                }
                if (evictPersona.getConversation() != null) {
                    evictPersona.getConversation().clear();
                }
            } catch (Exception e) {
                logger.warn("淘汰 Persona 时释放资源异常，roleId: {}", evictKey, e);
            }
            personas.remove(evictKey);
            logger.info("LRU 淘汰 Persona，roleId: {}，剩余缓存数: {}", evictKey, personas.size());
        }
    }
}
