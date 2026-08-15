package edu.zjut.traceqa.dto.auth;

import edu.zjut.traceqa.common.auth.UserContext;
import edu.zjut.traceqa.entity.User;

import java.util.List;

/**
 * 登录成功响应 DTO。
 *
 * @param token   JWT 访问令牌
 * @param userInfo 当前登录用户信息
 */
public record LoginResponse(
        String token,
        UserInfo userInfo
) {

    /** 由实体与登录上下文组装响应 */
    public static LoginResponse of(User user, List<String> permissions, String token) {
        return new LoginResponse(token, UserInfo.of(user, permissions));
    }
}