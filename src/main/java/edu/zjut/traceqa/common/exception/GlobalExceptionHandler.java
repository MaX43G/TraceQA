package edu.zjut.traceqa.common.exception;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.api.TraceIdHolder;
import edu.zjut.traceqa.common.enums.ErrorCode;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import edu.zjut.traceqa.service.MonitorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>捕获所有未处理异常并转化为标准 {@link ApiResponse}，严禁将底层异常堆栈
 * 直接暴露给前端。所有异常均记录完整日志（含 traceId）以便排查。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Resource
    private MonitorService monitorService;

    /**
     * 业务异常：按自身错误码返回，附带根因详情
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        log.warn("业务异常，traceId={}, code={}, msg={}",
                TraceIdHolder.get(), e.getErrorCode().getCode(), e.getMessage());
        // 业务异常的底层根因（如 zip 解压失败的具体原因）作为 detail 下发展示
        String detail = e.getCause() == null ? null : extractDetail(e.getCause());
        return ApiResponse.fail(e.getErrorCode(), e.getMessage(), detail);
    }

    /**
     * sa-token 未登录/登录失效：统一返回 40100
     */
    @ExceptionHandler(NotLoginException.class)
    public ApiResponse<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录或登录已失效，traceId={}, type={}",
                TraceIdHolder.get(), e.getType());
        return ApiResponse.fail(ErrorCode.UNAUTHORIZED);
    }

    /**
     * sa-token 权限不足（@SaCheckPermission）：统一返回 40300
     */
    @ExceptionHandler(NotPermissionException.class)
    public ApiResponse<Void> handleNotPermission(NotPermissionException e) {
        log.warn("权限不足，traceId={}, need={}", TraceIdHolder.get(), e.getPermission());
        return ApiResponse.fail(ErrorCode.FORBIDDEN);
    }

    /**
     * 方法参数校验异常（@RequestBody + @Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = extractFirstFieldError(e);
        log.warn("参数校验失败，traceId={}, msg={}", TraceIdHolder.get(), msg);
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, msg);
    }

    /**
     * 表单绑定校验异常
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException e) {
        String msg = extractFirstFieldError(e);
        log.warn("参数绑定失败，traceId={}, msg={}", TraceIdHolder.get(), msg);
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, msg);
    }

    /**
     * 缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数，traceId={}, param={}", TraceIdHolder.get(), e.getParameterName());
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, "缺少参数：" + e.getParameterName());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持，traceId={}, method={}", TraceIdHolder.get(), e.getMethod());
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, "不支持的请求方法：" + e.getMethod());
    }

    /**
     * 资源不存在
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        log.warn("资源不存在，traceId={}, path={}", TraceIdHolder.get(), e.getResourcePath());
        return ApiResponse.fail(ErrorCode.NOT_FOUND, "请求的资源不存在");
    }

    /**
     * 上传文件超出大小限制
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        log.warn("上传文件超出大小限制，traceId={}", TraceIdHolder.get());
        return ApiResponse.fail(ErrorCode.FILE_ERROR, "上传文件超出大小限制", extractDetail(e));
    }

    /**
     * 兜底异常：捕获所有未预期异常，统一转内部错误码，附带根因详情（不下发完整堆栈）
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常，traceId={}", TraceIdHolder.get(), e);
        monitorService.recordError(e.getClass().getSimpleName() + ": " + e.getMessage());
        return ApiResponse.fail(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMsg(), extractDetail(e));
    }

    /**
     * 提取根因的紧凑描述（异常类型 + 关键信息），便于直接在下发响应中定位问题
     */
    private String extractDetail(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        String name = root.getClass().getSimpleName();
        return (msg == null || msg.isBlank()) ? name : name + ": " + msg;
    }

    /**
     * 提取校验异常中的第一个字段错误信息
     */
    private String extractFirstFieldError(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError == null) {
            return "参数校验失败";
        }
        return fieldError.getDefaultMessage() == null
                ? "参数校验失败：" + fieldError.getField()
                : fieldError.getDefaultMessage();
    }
}