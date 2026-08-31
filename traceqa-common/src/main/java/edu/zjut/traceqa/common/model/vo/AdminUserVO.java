package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员视角的用户信息。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserVO {

    /**
     * 用户 ID
     */
    private Long id;

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
     * 用户状态：1 启用，0 禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 实体转 VO
     */
    public static AdminUserVO of(User user) {
        return new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getRoleCode(), user.getStatus(), user.getCreateTime());
    }
}