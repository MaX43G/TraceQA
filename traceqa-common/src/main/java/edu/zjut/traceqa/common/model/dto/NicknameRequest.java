package edu.zjut.traceqa.common.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改昵称请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NicknameRequest {

    /**
     * 新昵称
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32)
    private String nickname;
}