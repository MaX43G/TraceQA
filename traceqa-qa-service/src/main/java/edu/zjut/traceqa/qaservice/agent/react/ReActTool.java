package edu.zjut.traceqa.qaservice.agent.react;

/**
 * ReAct 工具接口。
 *
 * <p>AI Decision 模式下 LLM 可调用的检索工具，
 * 每个工具对应一种检索方式（向量/图谱/关键词）。</p>
 */
public interface ReActTool {

    /**
     * 工具名称（LLM 通过此名称调用）
     */
    String name();

    /**
     * 工具描述（供 LLM 理解工具用途）
     */
    String description();

    /**
     * 执行工具调用，返回检索结果摘要文本
     *
     * @param query 用户查询或 LLM 生成的检索查询
     * @return 检索结果的文本描述（供 LLM 阅读）
     */
    String execute(String query);
}
