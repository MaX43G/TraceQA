package edu.zjut.traceqa.common.config;

import edu.zjut.traceqa.config.AppProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;

/**
 * LightRAG WebUI 反向代理过滤器。
 *
 * <p>对外只暴露 {@code /lightrag-webui/**}，经管理员签发的 HttpOnly Cookie
 * （{@code tq_webui}）鉴权后，将请求（含流式响应，如 {@code /query/stream}）
 * 转发至内部 LightRAG Server，从而不对外开放 LightRAG 真实端口。非管理员或无
 * 会话 Cookie 一律返回 401。</p>
 */
@Component
public class LightRagWebuiProxyFilter extends OncePerRequestFilter {

    private static final String WEBUI_PREFIX = "/lightrag-webui/";
    private static final String SESSION_COOKIE = "tq_webui";

    /** 不透传到上游的头（host/长度/连接/流式/我们的鉴权 cookie） */
    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "cookie", "upgrade");
    /** 不回写客户端的响应头（长度/连接/上游 cookie） */
    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
            "content-length", "transfer-encoding", "connection", "keep-alive", "set-cookie", "upgrade");

    @Resource
    private LightRagWebuiSessionStore webuiSessionStore;

    @Resource
    private AppProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(WEBUI_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws IOException {
        if (!hasValidSession(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"无 LightRAG WebUI 访问权限，请由管理员获取会话\"}");
            return;
        }
        proxy(request, response);
    }

    /** 校验请求携带的 tq_webui Cookie 是否有效 */
    private boolean hasValidSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (SESSION_COOKIE.equals(c.getName()) && webuiSessionStore.isValid(c.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** 将请求转发至 LightRAG 并流式回写响应 */
    private void proxy(HttpServletRequest request, HttpServletResponse response) {
        String target = properties.getLightrag().getBaseUrl() + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofMinutes(5))
                    .method(request.getMethod(), bodyPublisher(request));
            Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (SKIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                    continue;
                }
                Enumeration<String> values = request.getHeaders(name);
                while (values.hasMoreElements()) {
                    builder.header(name, values.nextElement());
                }
            }

            HttpResponse<InputStream> upstream = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            response.setStatus(upstream.statusCode());
            upstream.headers().map().forEach((name, values) -> {
                if (SKIP_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                    return;
                }
                values.forEach(v -> response.addHeader(name, v));
            });
            response.flushBuffer();

            try (InputStream in = upstream.body(); OutputStream out = response.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    out.flush();
                }
            }
        } catch (Exception e) {
            logIfNeeded(e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"code\":502,\"msg\":\"LightRAG WebUI 暂不可用\"}");
                } catch (IOException ignored) {
                    // 响应已提交，忽略
                }
            }
        }
    }

    /** 组装请求体：GET/HEAD/DELETE 无体，其余读取原始字节转发 */
    private HttpRequest.BodyPublisher bodyPublisher(HttpServletRequest request) throws IOException {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        byte[] body = request.getInputStream().readAllBytes();
        return HttpRequest.BodyPublishers.ofByteArray(body);
    }

    private void logIfNeeded(Exception e) {
        // 连接中断（客户端关闭）时静默，其余记录 debug
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (!(e instanceof java.io.EOFException)) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .debug("LightRAG WebUI 代理异常：{}", msg);
        }
    }
}