package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户信息更新请求参数
 *
 */
@Data
@Schema(description = "用户信息更新请求参数")
public class UserUpdateParam {

    @Schema(description = "新邮箱", example = "newemail@example.com")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "新手机号", example = "13900139000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String tel;

    @Schema(description = "新密码", example = "newpassword123")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20个字符之间")
    private String password;

    @Schema(description = "新姓名/昵称", example = "李四")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String name;

    @Schema(description = "新头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "Token使用限制", example = "10")
    private Integer tokenLimit;

    @Schema(description = "Token通知开关（1-开启，0-关闭）", example = "1")
    @Pattern(regexp = "^[01]$", message = "tokenNotify只能是0或1")
    private String tokenNotify;
}
