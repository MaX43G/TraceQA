package edu.zjut.traceqa.model.vo;
import edu.zjut.traceqa.model.po.SystemPrompt;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统提示词请求/响应 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemPromptDTO {

    private Long id;

    @NotBlank(message = "提示词场景不能为空")
    private String scenario;

    @NotBlank(message = "提示词名称不能为空")
    private String name;

    private String content;

    private Integer enabled;

    private String remark;

    private LocalDateTime updateTime;

/** 由实体组装 */
    public static SystemPromptDTO of(SystemPrompt prompt) {
        return new SystemPromptDTO(prompt.getId(), prompt.getScenario(), prompt.getName(),
                prompt.getContent(), prompt.getEnabled(), prompt.getRemark(), prompt.getUpdateTime());
    }
}
