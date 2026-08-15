package edu.zjut.traceqa.dto.admin;

import edu.zjut.traceqa.entity.User;

import java.time.LocalDateTime;

/**
 * 管理员视角的用户信息 DTO。
 */
public record AdminUserVO(
        Long id,
        String username,
        String nickname,
        String roleCode,
        Integer status,
        LocalDateTime createTime
) {

    /** 由用户实体组装 */
    public static AdminUserVO of(User user) {
        return new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getRoleCode(), user.getStatus(), user.getCreateTime());
    }
}