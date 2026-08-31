package edu.zjut.traceqa.common.model.vo;

import edu.zjut.traceqa.common.model.po.KnowledgeBase;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBaseDTO {

    /**
     * 知识库 ID
     */
    private Long id;

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空")
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 所属课程
     */
    private String course;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 实体转 DTO
     */
    public static KnowledgeBaseDTO of(KnowledgeBase kb) {
        return new KnowledgeBaseDTO(kb.getId(), kb.getName(), kb.getDescription(), kb.getCourse(), kb.getCreateTime());
    }
}