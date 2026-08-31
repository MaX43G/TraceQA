package edu.zjut.traceqa.common.model.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建会话请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionCreateRequest {

    /**
     * 会话标题（可选，空则取首条用户消息）
     */
    @Size(max = 128)
    private String title;

    /**
     * 绑定的知识库 ID（可空）
     */
    private Long knowledgeBaseId;
}