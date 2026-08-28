package edu.zjut.traceqa.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求 DTO。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    /**
     * 登录账号（仅英文数字，注册后不可修改）
     */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "账号只能由英文字母和数字组成，且注册后不可修改")
    @Size(min = 3, max = 32, message = "账号长度需在 3-32 之间")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;

    /**
     * 确认密码（需与密码一致）
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /**
     * 昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称长度不能超过 32")
    private String nickname;

}
