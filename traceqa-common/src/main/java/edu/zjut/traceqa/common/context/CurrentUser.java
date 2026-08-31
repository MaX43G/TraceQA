package edu.zjut.traceqa.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户的轻量快照（由网关解析后透传，下游各服务读取）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CurrentUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 角色编码（如 ADMIN/USER） */
    private String roleCode;

    /** 权限码集合 */
    private List<String> permissions;

    /** 是否具备指定权限 */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /** 是否具备指定角色 */
    public boolean hasRole(String role) {
        return roleCode != null && roleCode.equalsIgnoreCase(role);
    }
}