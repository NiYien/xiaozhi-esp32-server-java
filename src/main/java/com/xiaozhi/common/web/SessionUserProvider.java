package com.xiaozhi.common.web;

import com.xiaozhi.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 用户会话管理工具类
 * 提供当前请求用户的获取和设置功能
 */
public class SessionUserProvider {

    public static final String USER_ATTRIBUTE_KEY = "user";

    /**
     * 获取当前请求的用户
     */
    public static SysUser getUser() {
        Object userObj = RequestContextHolder.currentRequestAttributes().getAttribute(USER_ATTRIBUTE_KEY, RequestAttributes.SCOPE_REQUEST);
        if (userObj instanceof SysUser) {
            return (SysUser) userObj;
        } else {
            return null;
        }
    }

    /**
     * 设置当前请求的用户
     */
    public static void setUser(HttpServletRequest request, SysUser user) {
        request.setAttribute(USER_ATTRIBUTE_KEY, user);
    }

    /**
     * 获取当前用户ID
     */
    public static Integer getUserId() {
        SysUser user = getUser();
        if (user != null) {
            return user.getUserId();
        } else {
            return null;
        }
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        SysUser user = getUser();
        if (user != null) {
            return user.getUsername();
        } else {
            return null;
        }
    }

    /**
     * 获取当前用户姓名
     */
    public static String getName() {
        SysUser user = getUser();
        if (user != null) {
            return user.getName();
        } else {
            return null;
        }
    }
}
