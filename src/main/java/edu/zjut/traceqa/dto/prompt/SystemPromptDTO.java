package edu.zjut.traceqa.dto.prompt;

import edu.zjut.traceqa.entity.SystemPrompt;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 系统提示词请求/响应 DTO。
 */
public record SystemPromptDTO(
        Long id,

        @NotBlank(message = "提示词场景不能为空")
        String scenario,

        @NotBlank(message = "提示词名称不能为空")
        String name,
        String content,
        Integer enabled,
        String remark,
        LocalDateTime updateTime
) {

    /** 由实体组装 */
    public static SystemPromptDTO of(SystemPrompt prompt) {
        return new SystemPromptDTO(prompt.getId(), prompt.getScenario(), prompt.getName(),
                prompt.getContent(), prompt.getEnabled(), prompt.getRemark(), prompt.getUpdateTime());
    }
}