package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.SystemPrompt;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统提示词 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemPromptDTO {

    /**
     * 提示词 ID
     */
    private Long id;

    /**
     * 场景编码（chat/rewrite/hyde/summary...）
     */
    @NotBlank(message = "场景编码不能为空")
    private String scenario;

    /**
     * 提示词名称
     */
    @NotBlank(message = "提示词名称不能为空")
    private String name;

    /**
     * 提示词内容
     */
    private String content;

    /**
     * 是否启用
     */
    private Integer enabled;

    /**
     * 备注
     */
    private String remark;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 实体转 DTO
     */
    public static SystemPromptDTO of(SystemPrompt prompt) {
        return new SystemPromptDTO(prompt.getId(), prompt.getScenario(), prompt.getName(),
                prompt.getContent(), prompt.getEnabled(), prompt.getRemark(), prompt.getUpdateTime());
    }
}