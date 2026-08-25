package edu.zjut.traceqa.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.config.LightRagWebuiSessionStore;
import edu.zjut.traceqa.common.config.ObservabilitySessionStore;
import edu.zjut.traceqa.service.LightRagMonitorService;
import edu.zjut.traceqa.service.MonitorService;
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

    @Operation(summary = "查询系统运行指标")
    @SaCheckRole("ADMIN")
    @GetMapping
    public ApiResponse<Map<String, Object>> monitor() {
        return ApiResponse.ok(monitorService.snapshot());
    }

    @Operation(summary = "查询 LightRAG 引擎只读信息面板（流水线/文档状态/模型/图谱标签）")
    @SaCheckRole("ADMIN")
    @GetMapping("/lightrag")
    public ApiResponse<Map<String, Object>> lightragPanel() {
        return ApiResponse.ok(lightRagMonitorService.snapshot());
    }

    @Operation(summary = "重试 LightRAG 中解析失败的文档")
    @SaCheckRole("ADMIN")
    @PostMapping("/lightrag/reprocess-failed")
    public ApiResponse<Map<String, Object>> reprocessFailed() {
        return ApiResponse.ok(lightRagMonitorService.reprocessFailed());
    }

    @Operation(summary = "清空 LightRAG 缓存")
    @SaCheckRole("ADMIN")
    @PostMapping("/lightrag/clear-cache")
    public ApiResponse<Map<String, Object>> clearCache() {
        return ApiResponse.ok(lightRagMonitorService.clearCache());
    }

    @Operation(summary = "取消 LightRAG 当前运行的索引流水线")
    @SaCheckRole("ADMIN")
    @PostMapping("/lightrag/cancel-pipeline")
    public ApiResponse<Map<String, Object>> cancelPipeline() {
        return ApiResponse.ok(lightRagMonitorService.cancelPipeline());
    }

    @Operation(summary = "触发 LightRAG 目录扫描")
    @SaCheckRole("ADMIN")
    @PostMapping("/lightrag/scan")
    public ApiResponse<Map<String, Object>> scan() {
        return ApiResponse.ok(lightRagMonitorService.scanDocuments());
    }

@Operation(summary = "获取 LightRAG WebUI 访问会话（签发短期 HttpOnly Cookie，供反向代理鉴权）")
    @SaCheckRole("ADMIN")
    @PostMapping("/lightrag/webui-session")
    public ApiResponse<Void> webuiSession(HttpServletResponse response) {
        String token = webuiSessionStore.create();
        Cookie cookie = new Cookie("tq_webui", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        // 路径设为 /：LightRAG WebUI 内部的 API 文档链接可能使用根路径（如 /docs），
        // 需保证该 Cookie 能随根路径请求一起发送，从而通过反向代理鉴权
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取可观测性工具（Grafana/Prometheus）访问会话（签发短期 HttpOnly Cookie，供反向代理鉴权）")
    @SaCheckRole("ADMIN")
    @PostMapping("/observability/session")
    public ApiResponse<Void> observabilitySession(HttpServletResponse response) {
        String token = observabilitySessionStore.create();
        Cookie cookie = new Cookie("tq_obs", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return ApiResponse.ok();
    }
}
