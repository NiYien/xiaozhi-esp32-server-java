package com.xiaozhi.service.auth;

import com.xiaozhi.dto.response.LoginResponseDTO;
import com.xiaozhi.entity.SysAuthRole;
import com.xiaozhi.entity.SysPermission;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.service.SysAuthRoleService;
import com.xiaozhi.service.SysPermissionService;
import com.xiaozhi.utils.DtoConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 登录响应构建器
 * 封装角色查询 + 权限树构建 + DTO 转换逻辑，消除多处重复的登录响应构建代码
 *
 */
@Component
public class LoginResponseBuilder {

    /** 登录Token有效期（秒）：30天 */
    public static final int TOKEN_EXPIRE_SECONDS = 2592000;

    @Resource
    private SysAuthRoleService authRoleService;

    @Resource
    private SysPermissionService permissionService;

    /**
     * 构建登录响应DTO
     *
     * @param user  当前登录用户
     * @param token 登录Token
     * @return 登录响应DTO
     */
    public LoginResponseDTO buildResponse(SysUser user, String token) {
        // 获取角色和权限
        SysAuthRole role = authRoleService.selectById(user.getRoleId());
        List<SysPermission> permissions = permissionService.selectByUserId(user.getUserId());
        List<SysPermission> permissionTree = permissionService.buildPermissionTree(permissions);

        return LoginResponseDTO.builder()
            .token(token)
            .refreshToken(token)
            .expiresIn(TOKEN_EXPIRE_SECONDS)
            .userId(user.getUserId())
            .user(DtoConverter.toUserDTO(user))
            .role(DtoConverter.toRoleDTO(role))
            .permissions(DtoConverter.toPermissionDTOList(permissionTree))
            .build();
    }
}
