package edu.zjut.traceqa.common.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    /** 登录账号（英文数字） */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "用户名仅支持英文与数字")
    @Size(min = 3, max = 32, message = "用户名长度需在 3-32 之间")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    /** 确认密码 */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /** 昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32)
    private String nickname;
}