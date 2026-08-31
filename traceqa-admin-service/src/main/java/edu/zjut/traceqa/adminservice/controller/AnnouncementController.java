package edu.zjut.traceqa.adminservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.rbac.RequirePermission;
import edu.zjut.traceqa.adminservice.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统公告接口。
 */
@Tag(name = "系统公告", description = "公开查看公告、管理员维护公告")
@RestController
@RequestMapping("/api/announcement")
public class AnnouncementController {

    @Resource
    private AnnouncementService announcementService;

    /**
     * 获取所有启用的公告（公开）
     */
    @Operation(summary = "获取所有启用的公告（公开）")
    @GetMapping("/active")
    public ApiResponse<List<Map<String, Object>>> active() {
        return ApiResponse.ok(announcementService.active());
    }

    /**
     * 获取全部公告（管理员）
     */
    @Operation(summary = "获取全部公告（管理员）")
    @RequirePermission("user:manage")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll() {
        return ApiResponse.ok(announcementService.listAll());
    }

    /**
     * 新增/修改公告（管理员）
     */
    @Operation(summary = "新增/修改公告（管理员）")
    @RequirePermission("user:manage")
    @PostMapping
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(announcementService.save(
                body.get("id") == null ? null : Long.parseLong(String.valueOf(body.get("id"))),
                (String) body.get("title"),
                (String) body.get("content"),
                body.get("enabled") == null ? null : Integer.parseInt(String.valueOf(body.get("enabled")))));
    }

    /**
     * 删除公告（管理员）
     */
    @Operation(summary = "删除公告（管理员）")
    @RequirePermission("user:manage")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResponse.ok();
    }
}