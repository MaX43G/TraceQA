package edu.zjut.traceqa.dto.chat;

/**
 * Agent 思考链路节点 DTO。
 *
 * <p>用于前端「动态折叠面板」逐节点展示 Agent 工作流状态。</p>
 *
 * @param stage   节点阶段名（如：意图识别、查询重写、双路检索）
 * @param agent   所属 Agent 名
 * @param status  节点状态（running/done/failed/skipped）
 * @param message 过程描述
 * @param detail  过程结果（如改写后的查询、检索到的片段数）
 */
public record ThinkingNodeVO(
        String stage,
        String agent,
        String status,
        String message,
        String detail
) {
}