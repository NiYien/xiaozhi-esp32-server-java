-- 声纹管理菜单权限
-- Phase 4: 声纹管理前端页面

-- 添加声纹管理菜单权限
INSERT IGNORE INTO `sys_permission` (`parentId`, `name`, `permissionKey`, `permissionType`, `path`, `component`, `icon`, `sort`, `visible`, `status`) VALUES
(0, '声纹管理', 'system:voiceprint', 'menu', '/voiceprint', 'page/VoiceprintView', 'AudioOutlined', 7, '1', '1');

-- 管理员角色(roleId=1)赋予声纹管理权限
INSERT IGNORE INTO `sys_role_permission` (`roleId`, `permissionId`)
SELECT 1, permissionId FROM `sys_permission` WHERE `permissionKey` = 'system:voiceprint'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    JOIN `sys_permission` p ON rp.permissionId = p.permissionId
    WHERE rp.roleId = 1 AND p.permissionKey = 'system:voiceprint'
);

-- 普通用户角色(roleId=2)赋予声纹管理权限
INSERT IGNORE INTO `sys_role_permission` (`roleId`, `permissionId`)
SELECT 2, permissionId FROM `sys_permission` WHERE `permissionKey` = 'system:voiceprint'
AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp
    JOIN `sys_permission` p ON rp.permissionId = p.permissionId
    WHERE rp.roleId = 2 AND p.permissionKey = 'system:voiceprint'
);
