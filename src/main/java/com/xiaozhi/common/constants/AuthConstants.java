package com.xiaozhi.common.constants;

/**
 * 认证相关常量
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** Token 过期时间（秒），30天 */
    public static final int TOKEN_EXPIRE_SECONDS = 2592000;

    /** 默认角色 ID */
    public static final int DEFAULT_ROLE_ID = 2;
}
