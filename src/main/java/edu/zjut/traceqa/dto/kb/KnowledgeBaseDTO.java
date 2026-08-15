package edu.zjut.traceqa.dto.kb;

import edu.zjut.traceqa.entity.KnowledgeBase;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 知识库请求/响应 DTO。
 */
public record KnowledgeBaseDTO(
        Long id,

        @NotBlank(message = "知识库名称不能为空")
        String name,
        String description,
        String course,
        Integer status,
        LocalDateTime createTime
) {

    /** 由实体组装 */
    public static KnowledgeBaseDTO of(KnowledgeBase kb) {
        return new KnowledgeBaseDTO(kb.getId(), kb.getName(), kb.getDescription(),
                kb.getCourse(), kb.getStatus(), kb.getCreateTime());
    }
}