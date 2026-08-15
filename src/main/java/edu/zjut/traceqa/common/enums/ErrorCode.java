package edu.zjut.traceqa.common.enums;

import lombok.Getter;

/**
 * 全局业务错误码字典。
 *
 * <p>所有接口统一返回 {@code code/msg/data/traceId} 结构，前端仅根据该字典中的
 * {@code code} 进行逻辑判断，严禁依赖 HTTP 状态码或异常堆栈。</p>
 *
 * <p>编码规范：{@code 2xx} 为业务成功，{@code 40xxx} 为客户端错误，{@code 42xxx} 为限流，
 * {@code 50xxx} 为服务端/外部依赖错误。</p>
 */
@Getter
public enum ErrorCode {

    /** 请求成功 */
    SUCCESS(200, "操作成功"),
    /** 参数校验失败（参数缺失、格式错误等） */
    PARAM_ERROR(40001, "参数错误"),
    /** 未登录或登录态缺失 */
    UNAUTHORIZED(40100, "请先登录"),
    /** Token 无效、过期或解析失败 */
    TOKEN_INVALID(40101, "登录状态已失效，请重新登录"),
    /** 已登录但缺少所需权限（RBAC） */
    FORBIDDEN(40300, "无权限执行该操作"),
    /** 请求的资源不存在 */
    NOT_FOUND(40400, "资源不存在"),
    /** 请求过于频繁，触发限流 */
    TOO_MANY_REQUESTS(42900, "请求过于频繁，请稍后再试"),
    /** 通用业务异常 */
    BIZ_ERROR(50000, "业务处理失败"),
    /** LLM 或检索服务不可用（优雅降级提示） */
    LLM_UNAVAILABLE(50001, "AI 服务暂时不可用，请稍后再试"),
    /** 文档上传或解析失败 */
    FILE_ERROR(50002, "文件处理失败"),
    /** 数据库或系统内部异常 */
    INTERNAL_ERROR(50003, "系统繁忙，请稍后再试");

    /** 业务错误码 */
    private final int code;
    /** 默认错误提示 */
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}