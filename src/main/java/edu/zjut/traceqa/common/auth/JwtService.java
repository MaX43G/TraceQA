package edu.zjut.traceqa.common.auth;

import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 令牌服务。
 *
 * <p>负责令牌的签发与解析。用户 ID、账号、角色、权限码随令牌签发，
 * 拦截器每次请求解析后写入 {@link UserContext}，实现无状态鉴权。</p>
 */
@Slf4j
@Component
public class JwtService {

    /** 令牌签发密钥（生产环境务必通过环境变量覆盖） */
    @Value("${app.jwt.secret:traceqa-secret-key-please-change-in-prod-2026}")
    private String secret;
    private SecretKey secretKey;
    /** 令牌有效期（毫秒） */
    @Value("${app.jwt.expiration:7200000}")
    private long expiration;

    @PostConstruct
    private void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 为用户签发令牌 */
    public String generateToken(UserContext.LoginUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("role", user.getRoleCode())
                .claim("permissions", user.getPermissions())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** 解析令牌，非法或过期时抛出 {@link BizException}（禁止暴露堆栈） */
    public UserContext.LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            List<String> permissions = claims.get("permissions", List.class);
            return new UserContext.LoginUser(
                    userId,
                    claims.get("username", String.class),
                    claims.get("nickname", String.class),
                    claims.get("role", String.class),
                    permissions == null ? List.of() : permissions);
        } catch (Exception e) {
            log.warn("令牌解析失败：{}", e.getMessage());
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }
}