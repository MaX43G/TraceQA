package edu.zjut.traceqa.dto.auth;

import edu.zjut.traceqa.entity.User;

import java.util.List;

/**
 * 当前用户信息 DTO。
 */
public record UserInfo(
        Long userId,
        String username,
        String nickname,
        String roleCode,
        Integer status,
        List<String> permissions
) {

    /** 由用户实体与权限码集合组装 */
    public static UserInfo of(User user, List<String> permissions) {
        return new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRoleCode(),
                user.getStatus(),
                permissions
        );
    }
}