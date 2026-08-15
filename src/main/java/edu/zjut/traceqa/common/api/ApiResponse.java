package edu.zjut.traceqa.common.api;

import edu.zjut.traceqa.common.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结构。
 *
 * <p>所有 REST 接口（SSE 事件除外）均返回本结构，保证前后端契约一致：</p>
 * <ul>
 *   <li>{@code code}   —— 业务错误码，前端仅据此判断成功/失败</li>
 *   <li>{@code msg}    —— 人类可读的提示信息</li>
 *   <li>{@code data}   —— 业务数据载荷</li>
 *   <li>{@code traceId}—— 链路追踪 ID，便于问题定位</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    /** 构造成功响应 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMsg(), data, TraceIdHolder.get());
    }

    /** 构造无数据成功响应 */
    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    /** 构造失败响应 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMsg(), null, TraceIdHolder.get());
    }

    /** 构造失败响应（自定义提示） */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String msg) {
        return new ApiResponse<>(errorCode.getCode(), msg, null, TraceIdHolder.get());
    }
}