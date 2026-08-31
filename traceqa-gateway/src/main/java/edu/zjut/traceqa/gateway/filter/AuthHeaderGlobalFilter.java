package edu.zjut.traceqa.gateway.filter;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import edu.zjut.traceqa.common.auth.LoginUser;
import edu.zjut.traceqa.common.context.AuthHeaders;
import edu.zjut.traceqa.gateway.metric.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * 鉴权后用户信息透传过滤器。
 *
 * <p>从请求头中的访问令牌直接解析登录用户（经 Redis 会话），将用户信息与链路追踪 ID
 * 以请求头写入下游请求，使各微服务通过 {@code UserContextFilter} 无状态读取当前用户。
 * 令牌解析仅依赖 sa-token 的 Redis DAO，不依赖响应式请求上下文，可在网关过滤器链中安全使用。</p>
 */
@Component
public class AuthHeaderGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthHeaderGlobalFilter.class);

    /** Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayMetrics metrics;

    public AuthHeaderGlobalFilter(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        String traceId = resolveTraceId(exchange);
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        builder.header(AuthHeaders.TRACE_ID, traceId);
        if (path.startsWith("/api/")) {
            injectUser(builder, exchange.getRequest().getHeaders());
        }
        long start = System.currentTimeMillis();
        boolean internal = path.startsWith("/internal/");
        return chain.filter(exchange.mutate().request(builder.build()).build())
                .doFinally(_ -> {
                    if (internal) {
                        return;
                    }
                    int status = exchange.getResponse().getStatusCode() == null
                            ? 500 : exchange.getResponse().getStatusCode().value();
                    long cost = System.currentTimeMillis() - start;
                    metrics.record(path, method, status, cost);
                    log.info("网关访问日志：{} {} -> {}，{}ms，traceId={}", method, path, status, cost, traceId);
                });
    }

    /** 复用上游 traceId 或生成新的，并透传 */
    private String resolveTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst(AuthHeaders.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }

    /** 依据请求头中的访问令牌将登录用户信息写入下游请求头 */
    private void injectUser(ServerHttpRequest.Builder builder, HttpHeaders headers) {
        String token = resolveToken(headers);
        if (token == null) {
            return;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                return;
            }
            SaSession session = StpUtil.getSessionByLoginId(loginId);
            Object raw = session.get(LoginUser.SESSION_KEY);
            if (raw instanceof LoginUser user) {
                builder.header(AuthHeaders.USER_ID, String.valueOf(user.getUserId()));
                builder.header(AuthHeaders.USERNAME, user.getUsername() == null ? "" : user.getUsername());
                builder.header(AuthHeaders.ROLE, user.getRoleCode() == null ? "" : user.getRoleCode());
                List<String> permissions = user.getPermissions() == null ? List.of() : user.getPermissions();
                builder.header(AuthHeaders.PERMISSIONS, String.join(",", permissions));
            }
        } catch (Exception e) {
            log.debug("透传用户信息失败：{}", e.getMessage());
        }
    }

    /** 从 Authorization 请求头解析访问令牌 */
    private String resolveToken(HttpHeaders headers) {
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            return null;
        }
        if (auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        return auth.trim();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}