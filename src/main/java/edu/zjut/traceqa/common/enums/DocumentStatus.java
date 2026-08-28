package edu.zjut.traceqa.common.enums;

/**
 * 文档解析状态枚举。
 */
public enum DocumentStatus {
    /**
     * 已上传，等待解析队列处理
     */
    PENDING,
    /**
     * 已提交至 LightRAG，正在抽取
     */
    PROCESSING,
    /**
     * 解析完成，可参与检索
     */
    DONE,
    /**
     * 解析失败
     */
    FAILED
}