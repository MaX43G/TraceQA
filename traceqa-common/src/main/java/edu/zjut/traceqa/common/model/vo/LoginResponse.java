package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.convert.DtoMapper;
import edu.zjut.traceqa.common.model.po.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {

    /**
     * 访问令牌
     */
    private String token;

    /**
     * 用户信息
     */
    private UserInfo userInfo;

    /**
     * 组装登录响应
     */
    public static LoginResponse of(User user, List<String> permissions, String token) {
        return new LoginResponse(token, DtoMapper.INSTANCE.toUserInfo(user, permissions));
    }
}