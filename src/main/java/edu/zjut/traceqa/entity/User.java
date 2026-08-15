package edu.zjut.traceqa.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体。
 *
 * <p>承载账号登录信息。角色以编码形式外键关联 {@link Role}，实现 RBAC 最小闭环：
 * 用户 -> 角色 -> 权限码集合。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    /** 登录账号（唯一） */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 昵称（展示名） */
    private String nickname;

    /** 角色编码（关联 t_role.code） */
    private String roleCode;

    /** 用户状态：1 启用，0 禁用 */
    private Integer status;

    /** 头像（可选） */
    @TableField(exist = false)
    private String avatar;
}