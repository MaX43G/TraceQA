package edu.zjut.traceqa.common.api;

import edu.zjut.traceqa.common.enums.ErrorCode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结构。
 *
 * <p>所有 REST 接口（SSE 事件除外）均返回本结构，保证前后端契约一致：</p>
 * <ul>
 *   <li>{@code code}   —— 业务错误码，前端仅据此判断成功/失败</li>
 *   <li>{@code msg}    —— 人类可读的提示信息（面向用户/运维）</li>
 *   <li>{@code detail} —— 诊断详情：根因信息（异常类型 + 关键信息），供排障定位，
 *                          不含完整堆栈（完整堆栈见服务端日志）；无则 null</li>
 *   <li>{@code data}   —— 业务数据载荷</li>
 *   <li>{@code traceId}—— 链路追踪 ID，配合日志定位完整堆栈</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private int code;
    /** 提示信息 */
    private String msg;
    /** 业务数据 */
    private T data;
    /** 链路追踪 ID */
    private String traceId;
    /** 诊断详情（排障用，可空） */
    private String detail;

    public ApiResponse(int code, String msg, T data, String traceId, String detail) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.traceId = traceId;
        this.detail = detail;
    }

    /** 构造成功响应 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), data, TraceIdHolder.get(), null);
    }

    /** 构造无数据成功响应 */
    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    /** 构造失败响应 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMsg(), null, TraceIdHolder.get(), null);
    }

    /** 构造失败响应（自定义提示） */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String msg) {
        return new ApiResponse<>(errorCode.getCode(), msg, null, TraceIdHolder.get(), null);
    }

    /** 构造失败响应（自定义提示 + 诊断详情） */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String msg, String detail) {
        return new ApiResponse<>(errorCode.getCode(), msg, null, TraceIdHolder.get(), detail);
    }
}