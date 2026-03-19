package com.xiaozhi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应DTO
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应结果")
public class LoginResponseDTO {

    @Schema(description = "访问令牌", example = "eyJ0eXAiOiJKV1QiLCJhbGc...")
    private String token;

    @Schema(description = "刷新令牌", example = "eyJ0eXAiOiJKV1QiLCJhbGc...")
    private String refreshToken;

    @Schema(description = "token过期时间（秒）", example = "2592000")
    private Integer expiresIn;

    @Schema(description = "用户ID", example = "1")
    private Integer userId;

    @Schema(description = "用户信息")
    private UserDTO user;

    @Schema(description = "用户角色")
    private RoleDTO role;

    @Schema(description = "用户权限树")
    private List<PermissionDTO> permissions;
}
