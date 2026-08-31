package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleDTO {

    /**
     * 角色 ID
     */
    private Long id;

    /**
     * 角色编码
     */
    private String code;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 权限码集合（逗号分隔）
     */
    private String permissions;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 实体转 DTO
     */
    public static RoleDTO of(Role role) {
        return new RoleDTO(role.getId(), role.getCode(), role.getName(),
                role.getPermissions(), role.getDescription(), role.getUpdateTime());
    }
}