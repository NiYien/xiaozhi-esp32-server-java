package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送验证码请求参数
 *
 */
@Data
@Schema(description = "发送验证码请求参数")
public class SendCaptchaParam {

    @Schema(description = "邮箱地址（发送邮箱验证码时使用）", example = "user@example.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "手机号（发送短信验证码时使用）", example = "13800138000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String tel;

    @Schema(
        description = "验证码用途类型：register-注册，forget-找回密码",
        example = "register",
        allowableValues = {"register", "forget"}
    )
    @NotBlank(message = "类型不能为空")
    private String type;
}
