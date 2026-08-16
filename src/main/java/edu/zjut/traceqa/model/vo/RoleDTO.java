package edu.zjut.traceqa.model.vo;
import edu.zjut.traceqa.model.po.Role;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色 DTO（RBAC 权限管理）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleDTO {

    private Long id;

    private String code;

    private String name;

    private String permissions;

    private String description;

    private LocalDateTime updateTime;

/** 由角色实体组装 */
    public static RoleDTO of(Role role) {
        return new RoleDTO(role.getId(), role.getCode(), role.getName(),
                role.getPermissions(), role.getDescription(), role.getUpdateTime());
    }
}
