package edu.zjut.traceqa.common.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体（RBAC）。
 *
 * <p>权限以逗号分隔的权限码字符串存储，简化多对多关系的同时保持扩展性，
 * 配合 {@code @RequirePermission} 注解实现方法级权限校验。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_role")
public class Role extends BaseEntity {

    /**
     * 角色编码（唯一，如 ADMIN / USER）
     */
    private String code;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 权限码集合，逗号分隔（如 "user:manage,kb:manage,doc:manage"）
     */
    private String permissions;

    /**
     * 角色描述
     */
    private String description;
}