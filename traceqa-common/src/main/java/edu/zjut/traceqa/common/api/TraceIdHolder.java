package edu.zjut.traceqa.common.api;

import lombok.NoArgsConstructor;

/**
 * 链路追踪 ID 的线程上下文持有器。
 *
 * <p>由各服务的 {@code TraceIdFilter} 在请求入口生成并写入 {@link ThreadLocal}，
 * 供 {@link ApiResponse} 与全局异常处理器读取，贯穿单次请求全链路。
 * 网关生成/透传的 traceId 会通过 {@code X-Trace-Id} 头在各微服务间传播。</p>
 */
@NoArgsConstructor
public final class TraceIdHolder {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();


    /**
     * 获取当前线程的 traceId，为空时返回占位符
     */
    public static String get() {
        String traceId = TRACE_ID.get();
        return traceId == null ? "-" : traceId;
    }

    /**
     * 设置当前线程的 traceId
     */
    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    /**
     * 清理当前线程的 traceId（请求结束时必须调用，防止线程复用串号）
     */
    public static void clear() {
        TRACE_ID.remove();
    }
}