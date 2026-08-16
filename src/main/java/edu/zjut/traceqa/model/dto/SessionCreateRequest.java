package edu.zjut.traceqa.model.dto;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求 DTO。
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionCreateRequest {

    @Size(max = 128, message = "会话标题长度不能超过 128")
    /** 会话标题（为空则取首条消息摘要） */
    private String title;

    /** 绑定的知识库 ID（可空） */
    private Long knowledgeBaseId;

}
