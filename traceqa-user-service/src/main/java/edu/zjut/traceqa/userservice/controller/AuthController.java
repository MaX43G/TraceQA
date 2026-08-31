package edu.zjut.traceqa.userservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.context.UserContext;
import edu.zjut.traceqa.common.model.dto.LoginRequest;
import edu.zjut.traceqa.common.model.dto.NicknameRequest;
import edu.zjut.traceqa.common.model.dto.PasswordChangeRequest;
import edu.zjut.traceqa.common.model.dto.RegisterRequest;
import edu.zjut.traceqa.common.model.vo.LoginResponse;
import edu.zjut.traceqa.common.model.vo.UserInfo;
import edu.zjut.traceqa.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证接口。
 */
@Tag(name = "认证", description = "注册、登录与当前用户信息")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 用户注册
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /**
     * 用户登录
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(httpRequest, request));
    }

    /**
     * 用户登出
     */
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    /**
     * 查询当前登录用户
     */
    @Operation(summary = "查询当前登录用户")
    @GetMapping("/me")
    public ApiResponse<UserInfo> me() {
        return ApiResponse.ok(authService.currentUser());
    }

    /**
     * 修改当前用户昵称
     */
    @Operation(summary = "修改当前用户昵称")
    @PutMapping("/nickname")
    public ApiResponse<Void> updateNickname(@Valid @RequestBody NicknameRequest request) {
        authService.updateNickname(request.getNickname());
        return ApiResponse.ok();
    }

    /**
     * 上传头像（经文件服务写入 MinIO，返回头像 URL）
     */
    @Operation(summary = "上传头像（前端裁剪后提交，返回头像 URL）")
    @PostMapping("/avatar")
    public ApiResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(authService.updateAvatar(file));
    }

    /**
     * 修改当前用户密码
     */
    @Operation(summary = "修改当前用户密码")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        authService.changePassword(UserContext.getUserId(), request.getOldPassword(), request.getNewPassword());
        return ApiResponse.ok();
    }
}