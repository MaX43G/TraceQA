package edu.zjut.traceqa.common.enums;

import lombok.Getter;

/**
 * 用户意图类型枚举。
 *
 * <p>由「意图识别 Agent」在检索前对用户问题进行分类，用于决定工作流走向：
 * 是否进入 RAG 检索链路、还是直接进行寒暄应答。</p>
 */
@Getter
public enum IntentType {

    /** 课程知识问答：进入「检索 -> 总结」RAG 链路 */
    COURSE_QA("课程问答"),
    /** 知识库与系统功能询问：跳转到管理/使用指引应答 */
    SYSTEM_QUESTION("系统使用咨询"),
    /** 寒暄问候：直接寒暄应答，不进入检索链路 */
    GREETING("寒暄问候"),
    /** 无法分类：按课程问答兜底处理 */
    UNKNOWN("未知");

    /** 中文展示名 */
    private final String label;

    IntentType(String label) {
        this.label = label;
    }
}