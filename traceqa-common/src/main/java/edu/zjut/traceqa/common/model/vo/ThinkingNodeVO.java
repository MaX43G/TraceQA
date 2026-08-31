package edu.zjut.traceqa.common.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 思考节点视图（SSE thinking 事件载荷）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThinkingNodeVO {

    /**
     * 节点阶段名（意图识别/检索策略调度/查询重写与 HyDE/图谱检索/向量检索/关键词检索/融合与补全/总结生成/直接应答）
     */
    private String stage;

    /**
     * 所属 Agent 名（intent-agent/router-agent/...）
     */
    private String agent;

    /**
     * 状态：running/done/failed/skipped
     */
    private String status;

    /**
     * 过程描述
     */
    private String message;

    /**
     * 过程结果（改写后的查询、检索片段数等）
     */
    private String detail;
}