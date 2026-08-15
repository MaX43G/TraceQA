package edu.zjut.traceqa.dto.admin;

import edu.zjut.traceqa.entity.Role;

import java.time.LocalDateTime;

/**
 * 角色 DTO（RBAC 权限管理）。
 */
public record RoleDTO(
        Long id,
        String code,
        String name,
        String permissions,
        String description,
        LocalDateTime updateTime
) {

    /** 由角色实体组装 */
    public static RoleDTO of(Role role) {
        return new RoleDTO(role.getId(), role.getCode(), role.getName(),
                role.getPermissions(), role.getDescription(), role.getUpdateTime());
    }
}