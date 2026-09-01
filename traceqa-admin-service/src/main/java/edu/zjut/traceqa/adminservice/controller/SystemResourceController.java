package edu.zjut.traceqa.adminservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.rbac.RequireRole;
import edu.zjut.traceqa.adminservice.service.SystemResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统资源检测与清理接口（管理员）。
 */
@Tag(name = "系统资源", description = "CPU/内存/磁盘占用、设备基础信息与无用资源清理（管理员）")
@RestController
@RequestMapping("/api/monitor/system")
public class SystemResourceController {

    @Resource
    private SystemResourceService systemResourceService;

    /**
     * 系统资源检测快照
     */
    @Operation(summary = "系统资源检测（CPU/内存/磁盘/基础信息）")
    @RequireRole("ADMIN")
    @GetMapping
    public ApiResponse<Map<String, Object>> system() {
        return ApiResponse.ok(systemResourceService.snapshot());
    }

    /**
     * Docker 可回收空间（计算较慢，带 TTL 缓存，按需调用）
     */
    @Operation(summary = "Docker 可回收空间检测")
    @RequireRole("ADMIN")
    @GetMapping("/reclaimable")
    public ApiResponse<Map<String, Object>> reclaimable() {
        return ApiResponse.ok(systemResourceService.reclaimableInfo());
    }

    /**
     * 清理系统无用资源
     *
     * @param req 请求体，{mode: "docker"}，当前支持 Docker 无用资源清理
     */
    @Operation(summary = "清理系统无用资源（Docker 镜像/容器/卷/构建缓存）")
    @RequireRole("ADMIN")
    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Object>> cleanup(@RequestBody(required = false) Map<String, String> req) {
        String mode = req == null ? null : req.get("mode");
        return ApiResponse.ok(systemResourceService.cleanup(mode));
    }
}
