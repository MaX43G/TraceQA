package edu.zjut.traceqa.controller;

import jakarta.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckPermission;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.model.vo.AdminUserVO;
import edu.zjut.traceqa.model.vo.RoleDTO;
import edu.zjut.traceqa.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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


    @Operation(summary = "分页查询用户")
    @GetMapping("/users")
    @SaCheckPermission("user:manage")
    public ApiResponse<PageResult<AdminUserVO>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(adminService.pageUsers(keyword, page, size));
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/users/{id}/status")
    @SaCheckPermission("user:manage")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @RequestParam @Min(0) @Max(1) int status) {
        adminService.updateUserStatus(id, status);
        return ApiResponse.ok();
    }

    @Operation(summary = "变更用户角色")
    @PutMapping("/users/{id}/role")
    @SaCheckPermission("user:manage")
    public ApiResponse<Void> updateRole(@PathVariable Long id,
                                        @Valid @RequestBody RoleChangeRequest request) {
        adminService.updateUserRole(id, request.getRoleCode());
        return ApiResponse.ok();
    }

    @Operation(summary = "查询全部角色")
    @GetMapping("/roles")
    @SaCheckPermission("role:manage")
    public ApiResponse<List<RoleDTO>> listRoles() {
        return ApiResponse.ok(adminService.listRoles());
    }

    @Operation(summary = "更新角色权限")
    @PutMapping("/roles/{id}")
    @SaCheckPermission("role:manage")
    public ApiResponse<RoleDTO> updateRole(@PathVariable Long id, @RequestBody RoleDTO dto) {
        return ApiResponse.ok(adminService.updateRole(id, dto));
    }

    /**
     * 角色变更请求体（@NotNull 校验）
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoleChangeRequest {
        @NotNull(message = "角色编码不能为空")
        private String roleCode;
    }
}