package edu.zjut.traceqa.common.enums;

import lombok.Getter;

/**
 * 文档解析状态枚举。
 *
 * <p>文档上传后立即返回 202，后台异步解析，状态由 {@code PENDING} 逐步流转至
 * {@code DONE} 或 {@code FAILED}，前端通过进度追踪面板轮询/SSE 观察。</p>
 */
@Getter
public enum DocumentStatus {

    /** 已上传，等待解析队列处理 */
    PENDING("等待解析"),
    /** 已提交至 LightRAG，正在抽取（分块/实体/关系） */
    PROCESSING("解析中"),
    /** 解析完成，可参与检索 */
    DONE("已完成"),
    /** 解析失败（含降级提示） */
    FAILED("失败");

    /** 中文展示名 */
    private final String label;

    DocumentStatus(String label) {
        this.label = label;
    }
}