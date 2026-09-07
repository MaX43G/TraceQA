package edu.zjut.traceqa.common.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 思考节点视图（SSE thinking 事件载荷）。
 *
 * <p>除阶段/状态外，携带本节点耗时 {@link #costMs} 与结构化附加数据 {@link #data}
 * （如意图结果、调度策略、重写/HyDE 输出、各路径命中情况、模型与参数），
 * 供前端展示与持久化追溯。</p>
 */
@Data
@NoArgsConstructor
public class ThinkingNodeVO {

    /**
     * 节点阶段名（意图识别/检索策略调度/查询重写与 HyDE/图谱检索/向量检索/关键词检索/结果融合/总结生成/直接应答）
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

    /**
     * 本节点耗时（毫秒，finish 时计算）
     */
    private Long costMs;

    /**
     * 结构化附加数据（各步骤的具体结果，用于追溯）
     */
    private Map<String, Object> data;

    /**
     * 节点开始时间戳（内部计时用，不参与序列化/持久化）
     */
    @JsonIgnore
    private long startMillis;

    public ThinkingNodeVO(String stage, String agent, String status, String message, String detail) {
        this.stage = stage;
        this.agent = agent;
        this.status = status;
        this.message = message;
        this.detail = detail;
    }
}