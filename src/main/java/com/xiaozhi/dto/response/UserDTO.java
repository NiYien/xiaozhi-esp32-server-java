package com.xiaozhi.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 用户信息响应DTO（不包含敏感信息）
 *
 */
@Data
@Schema(description = "用户信息")
public class UserDTO {

    @Schema(description = "用户ID", example = "1")
    private Integer userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "姓名/昵称", example = "张三")
    private String name;

    @Schema(description = "邮箱", example = "user@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String tel;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "用户状态：0-禁用，1-正常", example = "1", allowableValues = {"0", "1"})
    private String state;

    @Schema(description = "用户类型：0-普通用户，1-超级管理员", example = "0", allowableValues = {"0", "1"})
    private String isAdmin;

    @Schema(description = "角色ID", example = "2")
    private Integer roleId;

    @Schema(description = "累计对话次数", example = "100")
    private Integer totalMessage;

    @Schema(description = "设备总数", example = "3")
    private Integer totalDevice;

    @Schema(description = "Token使用限制", example = "10")
    private Integer tokenLimit;

    @Schema(description = "Token通知开关", example = "1")
    private String tokenNotify;

    @Schema(description = "上次登录IP", example = "192.168.1.100")
    private String loginIp;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "上次登录时间", example = "2024-12-01 10:30:00")
    private Date loginTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2024-01-01 00:00:00")
    private Date createTime;

    // 注意：不包含password、wxOpenId、wxUnionId等敏感信息
}
