package edu.zjut.traceqa.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统监控接口（仅管理员可见运行指标）。
 */
@Tag(name = "监控", description = "系统运行指标（管理员）")
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Resource
    private MonitorService monitorService;

    @Operation(summary = "查询系统运行指标")
    @SaCheckRole("ADMIN")
    @GetMapping
    public ApiResponse<Map<String, Object>> monitor() {
        return ApiResponse.ok(monitorService.snapshot());
    }
}
