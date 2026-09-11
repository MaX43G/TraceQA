package edu.zjut.traceqa.adminservice.config;

import jakarta.annotation.Resource;
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
 * Nacos WebUI 反向代理过滤器。
 *
 * <p>将 {@code /nacos/**} 经管理员 Cookie 鉴权后转发到内网 Nacos Server，
 * 避免对外暴露其管理端口；支持注入 AccessToken 实现免密登录。</p>
 */
@Component
public class NacosWebuiProxyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(NacosWebuiProxyFilter.class);

    private static final String WEBUI_PREFIX = "/nacos/";
    private static final String SESSION_COOKIE = "tq_nacos";

    private static final Set<String> SKIP_REQUEST_HEADERS = new HashSet<>(
            Arrays.asList("host", "content-length", "connection", "transfer-encoding", "cookie", "upgrade"));
    private static final Set<String> SKIP_RESPONSE_HEADERS = new HashSet<>(
            Arrays.asList("content-length", "transfer-encoding", "connection", "keep-alive", "set-cookie", "upgrade"));

    @Resource
    private NacosWebuiSessionStore sessionStore;
    @Resource
    private AdminProperties adminProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(WEBUI_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws IOException {
        if (!hasValidSession(request)) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"无 Nacos 访问权限，请由管理员获取会话\"}");
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
        String queryString = request.getQueryString();
        String serverAddr = adminProperties.getNacos().getServerAddr();
        // 移除末尾的斜杠
        if (serverAddr.endsWith("/")) {
            serverAddr = serverAddr.substring(0, serverAddr.length() - 1);
        }
        String target = serverAddr + uri;
        if (queryString != null && !queryString.isBlank()) {
            target += "?" + queryString;
        }

        try {
            URI targetUri = URI.create(target);
            // 安全校验：确保代理目标在配置的 Nacos 地址内
            URI baseUri = URI.create(serverAddr);
            if (!baseUri.getHost().equals(targetUri.getHost())) {
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

            // 复制请求头
            java.util.Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (!SKIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                    builder.header(name, request.getHeader(name));
                }
            }

            // 注入 AccessToken（如果配置了）
            String accessToken = adminProperties.getNacos().getAccessToken();
            if (accessToken != null && !accessToken.isBlank()) {
                builder.header("Authorization", "Bearer " + accessToken);
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

            // 对 HTML 内容进行 URL 重写
            if (contentType.contains("text/html")) {
                byte[] raw = upstream.body().readAllBytes();
                byte[] body = isGzip(contentEncoding) ? gunzip(raw) : raw;
                String html = new String(body, StandardCharsets.UTF_8);
                response.setContentType(contentType);
                response.setCharacterEncoding("UTF-8");
                if (isGzip(contentEncoding)) {
                    response.setHeader("Content-Encoding", "identity");
                }
                response.getWriter().write(rewriteRootRelativeUrls(html));
            } else {
                copyStream(upstream.body(), response.getOutputStream());
            }
        } catch (Exception e) {
            log.warn("Nacos WebUI 代理失败：{}", e.getMessage());
            if (!response.isCommitted()) {
                reject(response, 502, "Nacos 控制台暂不可用");
            }
        }
    }

    /**
     * 重写根相对路径，使其经过代理前缀
     */
    private String rewriteRootRelativeUrls(String html) {
        // 将 /xxx 改为 /nacos/xxx，但不重写已经是 /nacos/ 或绝对路径
        return html.replaceAll("(?i)(href|src|action|url)(\\s*(?:=|:)\\s*['\"])/(?!/)(?!nacos/)",
                "$1$2" + "/nacos/");
    }

    private HttpRequest.BodyPublisher bodyPublisher(HttpServletRequest request) throws IOException {
        if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || "DELETE".equals(request.getMethod())) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(request.getInputStream().readAllBytes());
    }

    private void copyStream(InputStream in, java.io.OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            out.flush();
        }
    }

    private boolean isGzip(String contentEncoding) {
        return contentEncoding != null && contentEncoding.toLowerCase().contains("gzip");
    }

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
