package edu.zjut.traceqa.model.vo;

import edu.zjut.traceqa.model.po.KnowledgeBase;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库请求/响应 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBaseDTO {

    private Long id;

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    private String course;

    private LocalDateTime createTime;

    /**
     * 由实体组装
     */
    public static KnowledgeBaseDTO of(KnowledgeBase kb) {
        return new KnowledgeBaseDTO(kb.getId(), kb.getName(), kb.getDescription(),
                kb.getCourse(), kb.getCreateTime());
    }
}
