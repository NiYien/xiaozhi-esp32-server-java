package com.xiaozhi.service.auth;

import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * 用户存在性检查器
 * 统一邮箱、手机号、用户名的重复检查逻辑，消除多处重复的存在性校验代码
 *
 * @author Joey
 */
@Component
public class UserExistenceChecker {

    @Resource
    private SysUserService userService;

    /**
     * 确保邮箱、手机号、用户名未被占用，已存在则抛出异常
     *
     * @param email         邮箱（可为空，为空则跳过检查）
     * @param tel           手机号（可为空，为空则跳过检查）
     * @param excludeUserId 排除的用户ID（用于更新场景，排除自身；注册场景传null）
     * @throws UserExistsException 用户已存在时抛出
     */
    public void ensureNotExists(String email, String tel, Integer excludeUserId) {
        ensureNotExists(email, tel, null, excludeUserId);
    }

    /**
     * 确保邮箱、手机号、用户名未被占用，已存在则抛出异常
     *
     * @param email         邮箱（可为空，为空则跳过检查）
     * @param tel           手机号（可为空，为空则跳过检查）
     * @param username      用户名（可为空，为空则跳过检查）
     * @param excludeUserId 排除的用户ID（用于更新场景，排除自身；注册场景传null）
     * @throws UserExistsException 用户已存在时抛出
     */
    public void ensureNotExists(String email, String tel, String username, Integer excludeUserId) {
        // 检查邮箱
        if (StringUtils.hasText(email)) {
            SysUser existingUser = userService.selectUserByEmail(email);
            if (!ObjectUtils.isEmpty(existingUser) && !existingUser.getUserId().equals(excludeUserId)) {
                throw new UserExistsException("邮箱已注册");
            }
        }

        // 检查手机号
        if (StringUtils.hasText(tel)) {
            SysUser existingUser = userService.selectUserByTel(tel);
            if (!ObjectUtils.isEmpty(existingUser) && !existingUser.getUserId().equals(excludeUserId)) {
                throw new UserExistsException("手机号已注册");
            }
        }

        // 检查用户名
        if (StringUtils.hasText(username)) {
            SysUser existingUser = userService.selectUserByUsername(username);
            if (!ObjectUtils.isEmpty(existingUser) && !existingUser.getUserId().equals(excludeUserId)) {
                throw new UserExistsException("用户名已存在");
            }
        }
    }

    /**
     * 用户已存在异常
     */
    public static class UserExistsException extends RuntimeException {
        public UserExistsException(String message) {
            super(message);
        }
    }
}
