package com.xiaozhi.service.auth;

import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 验证码校验器
 * 统一验证码校验逻辑和错误信息，消除多处重复的验证码检查代码
 *
 */
@Component
public class CaptchaValidator {

    /** 验证码校验失败的统一错误信息 */
    public static final String CAPTCHA_INVALID_MESSAGE = "验证码错误或已过期";

    @Resource
    private SysUserService userService;

    /**
     * 验证验证码是否有效，无效则抛出异常
     *
     * @param code         验证码
     * @param emailOrTel   邮箱或手机号（用于查询验证码记录）
     * @throws CaptchaInvalidException 验证码无效时抛出
     */
    public void validate(String code, String emailOrTel) {
        SysUser codeUser = new SysUser();
        codeUser.setEmail(emailOrTel);
        codeUser.setCode(code);
        int row = userService.queryCaptcha(codeUser);
        if (row < 1) {
            throw new CaptchaInvalidException(CAPTCHA_INVALID_MESSAGE);
        }
    }

    /**
     * 验证码无效异常
     */
    public static class CaptchaInvalidException extends RuntimeException {
        public CaptchaInvalidException(String message) {
            super(message);
        }
    }
}
