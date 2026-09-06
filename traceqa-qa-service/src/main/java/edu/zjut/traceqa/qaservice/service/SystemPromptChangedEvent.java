package edu.zjut.traceqa.qaservice.service;

/**
 * 系统提示词变更事件。
 *
 * <p>管理员更新或启用某个场景的提示词后发布，供依赖缓存的组件（如 {@code RagAgents}
 * 中缓存了系统提示词的 Agent）监听并失效缓存，使新提示词在下次问答立即生效。</p>
 *
 * @param scenario 发生变更的提示词场景（如 summary / chat / intent）
 */
public record SystemPromptChangedEvent(String scenario) {
}