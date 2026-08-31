package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前用户信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 登录账号
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 用户状态
     */
    private Integer status;

    /**
     * 权限码集合
     */
    private List<String> permissions;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 实体 + 权限集合转 VO
     */
    public static UserInfo of(User user, List<String> permissions) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname(),
                user.getRoleCode(), user.getStatus(), permissions, user.getAvatar());
    }
}