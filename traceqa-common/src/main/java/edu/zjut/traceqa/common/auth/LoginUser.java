package edu.zjut.traceqa.common.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录用户快照。
 *
 * <p>用户服务在登录成功后将本对象写入 sa-token 会话（Redis 持久化），
 * 网关在鉴权时读取并解析出用户 ID/角色/权限，透传给下游微服务。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话中存储登录用户信息的 key */
    public static final String SESSION_KEY = "loginUser";

    /** 用户 ID */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色编码 */
    private String roleCode;

    /** 权限码集合 */
    private List<String> permissions;

    /** 头像地址 */
    private String avatar;
}