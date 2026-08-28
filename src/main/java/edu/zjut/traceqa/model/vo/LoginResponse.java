package edu.zjut.traceqa.model.vo;

import edu.zjut.traceqa.model.po.User;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import edu.zjut.traceqa.common.convert.DtoMapper;

/**
 * 登录成功响应 DTO。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    /**
     * JWT 访问令牌
     */
    private String token;

    /**
     * 当前登录用户信息
     */
    private UserInfo userInfo;

    /**
     * 由实体与登录上下文组装响应
     */
    public static LoginResponse of(User user, List<String> permissions, String token) {
        return new LoginResponse(token, DtoMapper.INSTANCE.toUserInfo(user, permissions));
    }
}
