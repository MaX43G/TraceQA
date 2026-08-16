package edu.zjut.traceqa.model.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 思考链路节点 DTO。
 *
 * <p>用于前端「动态折叠面板」逐节点展示 Agent 工作流状态。</p>
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThinkingNodeVO {

    /** 节点阶段名（如：意图识别、查询重写、双路检索） */
    private String stage;

    /** 所属 Agent 名 */
    private String agent;

    /** 节点状态（running/done/failed/skipped） */
    private String status;

    /** 过程描述 */
    private String message;

    /** 过程结果（如改写后的查询、检索到的片段数） */
    private String detail;

}
