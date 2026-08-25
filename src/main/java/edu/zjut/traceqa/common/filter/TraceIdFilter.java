package edu.zjut.traceqa.common.filter;

import edu.zjut.traceqa.common.api.TraceIdHolder;
import edu.zjut.traceqa.service.MonitorService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器。
 *
 * <p>在每个请求入口生成唯一 traceId：优先沿用上游透传的 {@code X-Trace-Id}，
 * 否则自建 UUID。写入 {@link TraceIdHolder}（线程上下文）并回填响应头，
 * 请求结束后必须清理，避免线程池复用导致 traceId 串号。
 * 同时向 {@link MonitorService} 上报请求指标。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 上游透传 traceId 的请求头名称 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    @Resource
    private MonitorService monitorService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        TraceIdHolder.set(traceId);
        response.setHeader(TRACE_HEADER, traceId);
        long start = System.currentTimeMillis();
        String path = request.getRequestURI();
        String method = request.getMethod();
        monitorService.startRequest(path, method);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = System.currentTimeMillis() - start;
            monitorService.endRequest(path, method, response.getStatus(), costMs);
            log.info("请求结束，traceId={}, method={}, path={}, status={}, cost={}ms",
                    traceId, method, path, response.getStatus(), costMs);
            TraceIdHolder.clear();
        }
    }

    /** 解析 traceId：优先沿用上游透传值，否则生成新值 */
    private String resolveTraceId(HttpServletRequest request) {
        String upstream = request.getHeader(TRACE_HEADER);
        if (upstream != null && !upstream.isBlank()) {
            return upstream;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
