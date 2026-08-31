package edu.zjut.traceqa.common.context;

import edu.zjut.traceqa.common.api.TraceIdHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 用户上下文、链路追踪与访问日志过滤器。
 *
 * <p>网关完成鉴权后以请求头透传用户信息与 traceId；本过滤器在请求入口
 * 将其解析为 {@link UserContext} 与 {@link TraceIdHolder}，请求结束后输出统一访问日志并清理。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    /** 慢请求阈值（毫秒），超过则单独 WARN 提示 */
    private static final long SLOW_REQUEST_MS = 2000L;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        long start = System.currentTimeMillis();
        resolveTraceId(request, response);
        resolveUser(request);
        try {
            chain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - start;
            log.info("访问日志：{} {} -> {}，{}ms，traceId={}",
                    method, path, response.getStatus(), cost, TraceIdHolder.get());
            if (cost >= SLOW_REQUEST_MS) {
                log.warn("慢请求：{} {}，耗时 {}ms，traceId={}", method, path, cost, TraceIdHolder.get());
            }
            org.slf4j.MDC.remove("traceId");
            UserContext.clear();
            TraceIdHolder.clear();
        }
    }

    /**
     * 复用上游 traceId 或重新生成，并回写响应头
     */
    private void resolveTraceId(HttpServletRequest request, HttpServletResponse response) {
        String traceId = request.getHeader(AuthHeaders.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        TraceIdHolder.set(traceId);
        // 写入 MDC，供 JSON 结构化日志携带 traceId
        org.slf4j.MDC.put("traceId", traceId);
        response.setHeader(AuthHeaders.TRACE_ID, traceId);
    }

    /**
     * 从请求头解析用户上下文（未透传则为 null）
     */
    private void resolveUser(HttpServletRequest request) {
        String userId = request.getHeader(AuthHeaders.USER_ID);
        if (userId == null || userId.isBlank()) {
            return;
        }
        String username = request.getHeader(AuthHeaders.USERNAME);
        String role = request.getHeader(AuthHeaders.ROLE);
        String perms = request.getHeader(AuthHeaders.PERMISSIONS);
        List<String> permissions = perms == null || perms.isBlank()
                ? List.of()
                : Arrays.stream(perms.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();
        UserContext.set(new CurrentUser(Long.valueOf(userId), username, role, permissions));
    }
}