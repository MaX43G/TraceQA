package edu.zjut.traceqa.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.auth.RequirePermission;
import edu.zjut.traceqa.dto.admin.AdminUserVO;
import edu.zjut.traceqa.dto.admin.RoleDTO;
import edu.zjut.traceqa.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "分页查询用户")
    @GetMapping("/users")
    @RequirePermission("user:manage")
    public ApiResponse<PageResult<AdminUserVO>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminService.pageUsers(keyword, page, size));
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/users/{id}/status")
    @RequirePermission("user:manage")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @RequestParam @Min(0) @Max(1) int status) {
        adminService.updateUserStatus(id, status);
        return ApiResponse.ok();
    }

    @Operation(summary = "变更用户角色")
    @PutMapping("/users/{id}/role")
    @RequirePermission("user:manage")
    public ApiResponse<Void> updateRole(@PathVariable Long id,
                                        @Valid @RequestBody RoleChangeRequest request) {
        adminService.updateUserRole(id, request.roleCode());
        return ApiResponse.ok();
    }

    @Operation(summary = "查询全部角色")
    @GetMapping("/roles")
    @RequirePermission("role:manage")
    public ApiResponse<List<RoleDTO>> listRoles() {
        return ApiResponse.ok(adminService.listRoles());
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/roles/{id}")
    @RequirePermission("role:manage")
    public ApiResponse<RoleDTO> updateRole(@PathVariable Long id, @RequestBody RoleDTO dto) {
        return ApiResponse.ok(adminService.updateRole(id, dto));
    }

    /** 角色变更请求体（注意：@NotBlank 在 Hibernate Validator 9 + record 下会抛 HV000030，改用 @NotNull） */
    public record RoleChangeRequest(@NotNull(message = "角色编码不能为空") String roleCode) {
    }
}