package edu.zjut.traceqa.common.enums;

import lombok.Getter;

/**
 * 手动检索策略枚举。
 *
 * <p>用户可在对话输入框中勾选本次问答使用的检索方式，
 * 系统根据勾选结果调度对应的检索路径。</p>
 */
@Getter
public enum RetrievalStrategy {

    /**
     * 自动模式（默认）：Agent 工作流自主控制检索策略
     */
    AUTO("auto", "自动模式"),

    /**
     * 假设性文档（HyDE）：先生成假设性回答，再用其向量检索
     */
    HYDE("hyde", "假设性文档"),

    /**
     * 向量检索：基于 LightRAG 的向量相似度检索
     */
    VECTOR("vector", "向量检索"),

    /**
     * 关键词检索：jieba TF-IDF + ES BM25
     */
    KEYWORD("keyword", "关键词检索"),

    /**
     * 图谱检索：LightRAG 知识图谱检索（local + global）
     */
    GRAPH("graph", "图谱检索");

    private final String code;
    private final String label;

    RetrievalStrategy(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据 code 查找枚举，找不到返回 AUTO
     */
    public static RetrievalStrategy fromCode(String code) {
        if (code == null || code.isBlank()) {
            return AUTO;
        }
        for (RetrievalStrategy s : values()) {
            if (s.code.equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        return AUTO;
    }
}
