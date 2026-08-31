package edu.zjut.traceqa.adminservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 可观测性工具反向代理过滤器。
 *
 * <p>将 {@code /grafana/**} 与 {@code /prometheus/**} 经管理员 Cookie 鉴权后转发到
 * 内网 Grafana / Prometheus，避免对外暴露其真实端口。HTML 中的根相对路径资源引用会被改写。</p>
 */
@Component
public class ObservabilityProxyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityProxyFilter.class);

    private static final String GRAFANA_PREFIX = "/grafana";
    private static final String PROMETHEUS_PREFIX = "/prometheus";
    private static final String SESSION_COOKIE = "tq_obs";

    private static final Set<String> SKIP_REQUEST_HEADERS = new HashSet<>(
            Arrays.asList("host", "content-length", "connection", "transfer-encoding", "cookie", "upgrade"));
    private static final Set<String> SKIP_RESPONSE_HEADERS = new HashSet<>(
            Arrays.asList("content-length", "transfer-encoding", "connection", "keep-alive", "set-cookie", "upgrade"));

    private final ObservabilitySessionStore sessionStore;
    private final AdminProperties properties;
    private final HttpClient httpClient;

    public ObservabilityProxyFilter(ObservabilitySessionStore sessionStore, AdminProperties properties) {
        this.sessionStore = sessionStore;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !matchesPrefix(uri, GRAFANA_PREFIX) && !matchesPrefix(uri, PROMETHEUS_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws IOException {
        if (!hasValidSession(request)) {
            log.warn("可观测性代理无有效会话，返回 401：{}", request.getRequestURI());
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"无可观测性工具访问权限，请由管理员获取会话\"}");
            return;
        }
        proxy(request, response);
    }

    private boolean hasValidSession(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (var cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName()) && sessionStore.isValid(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    private void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        String base = uri.startsWith(PROMETHEUS_PREFIX)
                ? properties.getObservability().getPrometheusBaseUrl()
                : properties.getObservability().getGrafanaBaseUrl();
        String prefix = uri.startsWith(PROMETHEUS_PREFIX) ? PROMETHEUS_PREFIX : GRAFANA_PREFIX;
        String target = base + uri + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        try {
            URI targetUri = URI.create(target);
            if (!URI.create(base).getHost().equals(targetUri.getHost())) {
                reject(response, 400, "非法代理目标");
                return;
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri).timeout(Duration.ofMinutes(5));
            switch (request.getMethod()) {
                case "GET" -> builder.GET();
                case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                case "DELETE" -> builder.method("DELETE", HttpRequest.BodyPublishers.noBody());
                default -> builder.method(request.getMethod(), bodyPublisher(request));
            }
            java.util.Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                    builder.header(name, request.getHeader(name));
                }
            }
            HttpResponse<InputStream> upstream = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            response.setStatus(upstream.statusCode());
            upstream.headers().map().forEach((name, values) -> {
                if (!SKIP_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                    response.setHeader(name, String.join(",", values));
                }
            });
            String contentType = upstream.headers().firstValue("content-type").orElse("");
            String contentEncoding = upstream.headers().firstValue("content-encoding").orElse("");
            if (contentType.contains("text/html")) {
                byte[] raw = upstream.body().readAllBytes();
                byte[] body = isGzip(contentEncoding) ? gunzip(raw) : raw;
                String html = new String(body, StandardCharsets.UTF_8);
                response.setContentType(contentType);
                response.setCharacterEncoding("UTF-8");
                if (isGzip(contentEncoding)) {
                    response.setHeader("Content-Encoding", "identity");
                }
                response.getWriter().write(rewriteRootRelativeUrls(html, prefix));
            } else {
                copyStream(upstream.body(), response.getOutputStream());
            }
        } catch (Exception e) {
            log.error("代理转发异常：{} -> {}, err={}", uri, target, e.getMessage(), e);
            if (!response.isCommitted()) {
                reject(response, 502, "可观测性工具暂不可用");
            }
        }
    }

    /**
     * 将根相对路径（/foo）资源引用改写为带前缀（/grafana/foo）
     */
    private String rewriteRootRelativeUrls(String html, String prefix) {
        String pf = prefix.substring(1);
        return html.replaceAll("(?i)(href|src|action|url)(\\s*(?:=|:)\\s*['\"])/(?!/)(?!" + pf + "/)",
                "$1$2" + prefix + "/");
    }

    private HttpRequest.BodyPublisher bodyPublisher(HttpServletRequest request) throws IOException {
        if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || "DELETE".equals(request.getMethod())) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(request.getInputStream().readAllBytes());
    }

    private boolean matchesPrefix(String uri, String prefix) {
        return uri.equals(prefix) || uri.startsWith(prefix + "/");
    }

    private void copyStream(InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            out.flush();
        }
    }

    /** 是否为 gzip 压缩 */
    private boolean isGzip(String contentEncoding) {
        return contentEncoding != null && contentEncoding.toLowerCase().contains("gzip");
    }

    /** 解压 gzip 字节 */
    private byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private void reject(HttpServletResponse response, int status, String msg) throws IOException {
        if (!response.isCommitted()) {
            response.resetBuffer();
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\"}");
        }
    }
}