package edu.zjut.traceqa.userservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.model.vo.AdminUserVO;
import edu.zjut.traceqa.common.model.vo.RoleDTO;
import edu.zjut.traceqa.common.rbac.RequirePermission;
import edu.zjut.traceqa.userservice.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台接口（RBAC 用户与角色管理）。
 */
@Tag(name = "管理后台", description = "用户与角色的 RBAC 权限管理")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private AdminService adminService;

    /**
     * 分页查询用户
     */
    @Operation(summary = "分页查询用户")
    @RequirePermission("user:manage")
    @GetMapping("/users")
    public ApiResponse<PageResult<AdminUserVO>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminService.pageUsers(keyword, page, size));
    }

    /**
     * 启用/禁用用户
     */
    @Operation(summary = "启用/禁用用户")
    @RequirePermission("user:manage")
    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @RequestParam @Min(0) @Max(1) int status) {
        adminService.updateUserStatus(id, status);
        return ApiResponse.ok();
    }

    /**
     * 变更用户角色
     */
    @Operation(summary = "变更用户角色")
    @RequirePermission("user:manage")
    @PutMapping("/users/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable Long id,
                                        @Valid @RequestBody RoleChangeRequest request) {
        adminService.updateUserRole(id, request.getRoleCode());
        return ApiResponse.ok();
    }

    /**
     * 查询全部角色
     */
    @Operation(summary = "查询全部角色")
    @RequirePermission("role:manage")
    @GetMapping("/roles")
    public ApiResponse<List<RoleDTO>> listRoles() {
        return ApiResponse.ok(adminService.listRoles());
    }

    /**
     * 更新角色权限
     */
    @Operation(summary = "更新角色权限")
    @RequirePermission("role:manage")
    @PutMapping("/roles/{id}")
    public ApiResponse<RoleDTO> updateRole(@PathVariable Long id, @RequestBody RoleDTO dto) {
        return ApiResponse.ok(adminService.updateRole(id, dto));
    }

    /**
     * 角色变更请求体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoleChangeRequest {
        @NotNull(message = "角色编码不能为空")
        private String roleCode;
    }
}