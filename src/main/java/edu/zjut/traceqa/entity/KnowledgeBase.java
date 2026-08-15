package edu.zjut.traceqa.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库实体。
 *
 * <p>《数据挖掘》课程的知识单元容器。一个知识库对应一组教材/PPT 文档，
 * 聊天时可按知识库限定检索范围。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_knowledge_base")
public class KnowledgeBase extends BaseEntity {

    /** 知识库名称 */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 所属课程 */
    private String course;

    /** 状态：1 启用，0 停用 */
    private Integer status;
}