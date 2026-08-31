package edu.zjut.traceqa.adminservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.rbac.RequireRole;
import edu.zjut.traceqa.adminservice.config.LightRagWebuiSessionStore;
import edu.zjut.traceqa.adminservice.config.ObservabilitySessionStore;
import edu.zjut.traceqa.adminservice.service.LightRagMonitorService;
import edu.zjut.traceqa.adminservice.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统监控接口（仅管理员可见运行指标）。
 */
@Tag(name = "监控", description = "系统运行指标与 LightRAG 引擎面板（管理员）")
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Resource
    private MonitorService monitorService;
    @Resource
    private LightRagMonitorService lightRagMonitorService;
    @Resource
    private LightRagWebuiSessionStore webuiSessionStore;
    @Resource
    private ObservabilitySessionStore observabilitySessionStore;
    @Resource
    private edu.zjut.traceqa.adminservice.config.AdminProperties adminProperties;

    /**
     * 会话 Cookie 是否标记 Secure
     */
    private boolean cookieSecure() {
        return adminProperties.isCookieSecure();
    }

    /**
     * 查询系统运行指标
     */
    @Operation(summary = "查询系统运行指标")
    @RequireRole("ADMIN")
    @GetMapping
    public ApiResponse<Map<String, Object>> monitor() {
        return ApiResponse.ok(monitorService.snapshot());
    }

    /**
     * 查询 LightRAG 引擎只读信息面板
     */
    @Operation(summary = "查询 LightRAG 引擎只读信息面板")
    @RequireRole("ADMIN")
    @GetMapping("/lightrag")
    public ApiResponse<Map<String, Object>> lightragPanel() {
        return ApiResponse.ok(lightRagMonitorService.snapshot());
    }

    /**
     * 重试 LightRAG 中解析失败的文档
     */
    @Operation(summary = "重试 LightRAG 中解析失败的文档")
    @RequireRole("ADMIN")
    @PostMapping("/lightrag/reprocess-failed")
    public ApiResponse<Map<String, Object>> reprocessFailed() {
        return ApiResponse.ok(lightRagMonitorService.reprocessFailed());
    }

    /**
     * 清空 LightRAG 缓存
     */
    @Operation(summary = "清空 LightRAG 缓存")
    @RequireRole("ADMIN")
    @PostMapping("/lightrag/clear-cache")
    public ApiResponse<Map<String, Object>> clearCache() {
        return ApiResponse.ok(lightRagMonitorService.clearCache());
    }

    /**
     * 取消 LightRAG 当前运行的索引流水线
     */
    @Operation(summary = "取消 LightRAG 当前运行的索引流水线")
    @RequireRole("ADMIN")
    @PostMapping("/lightrag/cancel-pipeline")
    public ApiResponse<Map<String, Object>> cancelPipeline() {
        return ApiResponse.ok(lightRagMonitorService.cancelPipeline());
    }

    /**
     * 触发 LightRAG 目录扫描
     */
    @Operation(summary = "触发 LightRAG 目录扫描")
    @RequireRole("ADMIN")
    @PostMapping("/lightrag/scan")
    public ApiResponse<Map<String, Object>> scan() {
        return ApiResponse.ok(lightRagMonitorService.scanDocuments());
    }

    /**
     * 获取 LightRAG WebUI 访问会话（签发短期 HttpOnly Cookie，供反向代理鉴权）
     */
    @Operation(summary = "获取 LightRAG WebUI 访问会话")
    @RequireRole("ADMIN")
    @PostMapping("/lightrag/webui-session")
    public ApiResponse<Void> webuiSession(HttpServletResponse response) {
        String token = webuiSessionStore.create();
        Cookie cookie = new Cookie("tq_webui", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ApiResponse.ok();
    }

    /**
     * 获取可观测性工具访问会话（签发短期 HttpOnly Cookie，供反向代理鉴权）
     */
    @Operation(summary = "获取可观测性工具（Grafana/Prometheus）访问会话")
    @RequireRole("ADMIN")
    @PostMapping("/observability/session")
    public ApiResponse<Void> observabilitySession(HttpServletResponse response) {
        String token = observabilitySessionStore.create();
        Cookie cookie = new Cookie("tq_obs", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ApiResponse.ok();
    }
}