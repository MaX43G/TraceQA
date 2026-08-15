package edu.zjut.traceqa.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求 DTO。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 */
public record PasswordChangeRequest(
        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 32, message = "新密码长度需在 6-32 之间")
        String newPassword
) {
}