package com.xiaozhi.dao;

import java.util.List;

import com.xiaozhi.entity.SysRole;
import org.apache.ibatis.annotations.Param;

/**
 * 角色管理 数据层
 *
 *
 */
public interface RoleMapper {
  List<SysRole> query(SysRole role);

  int update(SysRole role);

  int resetDefault(SysRole role);

  int add(SysRole role);

  SysRole selectRoleById(Integer roleId);

  int deleteById(@Param("roleId") Integer roleId);

  /**
   * 清除指定voiceName的角色引用（音色克隆删除时调用）
   */
  int clearVoiceName(@Param("voiceName") String voiceName);
}