package edu.zjut.traceqa.controller;

import jakarta.annotation.Resource;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.auth.UserContext;
import edu.zjut.traceqa.dto.auth.LoginRequest;
import edu.zjut.traceqa.dto.auth.LoginResponse;
import edu.zjut.traceqa.dto.auth.PasswordChangeRequest;
import edu.zjut.traceqa.dto.auth.RegisterRequest;
import edu.zjut.traceqa.dto.auth.UserInfo;
import edu.zjut.traceqa.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@Tag(name = "认证", description = "注册、登录与当前用户信息")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "查询当前登录用户")
    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        return ApiResponse.ok(authService.currentUser());
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(UserContext.getUserId(), request.oldPassword(), request.newPassword());
        return ApiResponse.ok();
    }
}