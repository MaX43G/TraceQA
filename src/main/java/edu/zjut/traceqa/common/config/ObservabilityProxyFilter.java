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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可观测性工具（Grafana / Prometheus）反向代理过滤器。
 *
 * <p>对外只暴露 {@code /grafana/**} 与 {@code /prometheus/**}，经管理员签发的 HttpOnly Cookie
 * （{@code tq_obs}）鉴权后转发至内网 Grafana / Prometheus，从而不将工具真实地址直接对外
 * （或仅在防火墙内放行）。非管理员或无可观测性会话一律返回 401。</p>
 */
@Component
public class ObservabilityProxyFilter extends OncePerRequestFilter {

    private static final String GRAFANA_PREFIX = "/grafana";
    private static final String PROMETHEUS_PREFIX = "/prometheus";
    private static final String SESSION_COOKIE = "tq_obs";

    /** 不透传到上游的头 */
    private static final Set<String> SKIP_REQUEST_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "cookie", "upgrade");
    /** 不回写客户端的响应头 */
    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of(
            "content-length", "transfer-encoding", "connection", "keep-alive", "set-cookie", "upgrade");

    /** 匹配 HTML 中的根相对资源引用（href/src/action/url） */
    private static final Pattern ROOT_RESOURCE_REF = Pattern.compile(
            "(?i)(href|src|action|url)\\s*(=|:)\\s*[\"']");

    @Resource
    private ObservabilitySessionStore sessionStore;

    @Resource
    private AppProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null
                || (!matchesPrefix(path, GRAFANA_PREFIX) && !matchesPrefix(path, PROMETHEUS_PREFIX));
    }

    /** 匹配前缀（兼容有/无尾部斜杠，如 /grafana 与 /grafana/） */
    private boolean matchesPrefix(String path, String prefix) {
        return path.startsWith(prefix + "/") || path.equals(prefix);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws IOException {
        if (!hasValidSession(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"无可观测性工具访问权限，请由管理员获取会话\"}");
            return;
        }
        proxy(request, response);
    }

    private boolean hasValidSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (SESSION_COOKIE.equals(c.getName()) && sessionStore.isValid(c.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** 将请求转发至内网 Grafana / Prometheus 并流式回写响应 */
    private void proxy(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getRequestURI();
        boolean isPrometheus = matchesPrefix(path, PROMETHEUS_PREFIX);
        String baseUrl = isPrometheus
                ? properties.getObservability().getPrometheusBaseUrl()
                : properties.getObservability().getGrafanaBaseUrl();
        String target = baseUrl + path + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
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

            String contentType = upstream.headers().firstValue("content-type").orElse("");
            if (contentType.toLowerCase().contains("text/html")) {
                // 双保险：把 HTML 里根相对资源引用加上对应前缀（Grafana/Prometheus 已按子路径配置，一般用不到）
                byte[] body = upstream.body().readAllBytes();
                String html = new String(body, StandardCharsets.ISO_8859_1);
                String prefix = isPrometheus ? PROMETHEUS_PREFIX : GRAFANA_PREFIX;
                response.getOutputStream().write(rewriteRootRelativeUrls(html, prefix).getBytes(StandardCharsets.ISO_8859_1));
                response.getOutputStream().flush();
            } else {
                try (InputStream in = upstream.body(); OutputStream out = response.getOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                }
            }
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"code\":502,\"msg\":\"可观测性工具暂不可用\"}");
                } catch (IOException ignored) {
                    // 响应已提交，忽略
                }
            }
        }
    }

    /** 改写 HTML 中根相对资源引用，为其加上子路径前缀；已带前缀或协议相对（//）的引用保持不变 */
    private String rewriteRootRelativeUrls(String html, String prefix) {
        Matcher matcher = ROOT_RESOURCE_REF.matcher(html);
        StringBuffer sb = new StringBuffer(html.length() + 64);
        while (matcher.find()) {
            int pathStart = matcher.end();
            boolean rootRelative = pathStart < html.length()
                    && html.charAt(pathStart) == '/'
                    && (pathStart + 1 >= html.length() || html.charAt(pathStart + 1) != '/');
            boolean alreadyPrefixed = html.regionMatches(pathStart, prefix + "/", 0, prefix.length() + 1);
            if (rootRelative && !alreadyPrefixed) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group() + prefix));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
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
}