package edu.zjut.traceqa.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改昵称请求 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NicknameRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称长度不能超过 32")
    private String nickname;

}
