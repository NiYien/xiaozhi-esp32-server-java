package com.xiaozhi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 权限信息DTO
 *
 */
@Data
@Schema(description = "权限信息")
public class PermissionDTO {

    @Schema(description = "权限ID", example = "1")
    private Integer permissionId;

    @Schema(description = "权限名称", example = "用户管理")
    private String permissionName;

    @Schema(description = "权限编码", example = "user:manage")
    private String permissionCode;

    @Schema(description = "权限类型", example = "menu")
    private String type;

    @Schema(description = "父级权限ID", example = "0")
    private Integer parentId;

    @Schema(description = "路由路径", example = "/user/list")
    private String path;

    @Schema(description = "图标", example = "user")
    private String icon;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "子权限列表")
    private List<PermissionDTO> children;
}
