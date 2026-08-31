package edu.zjut.traceqa.common.exception;

import edu.zjut.traceqa.common.enums.ErrorCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>业务代码中主动抛出的受检信号，由 {@link GlobalExceptionHandler} 统一拦截
 * 并转换为标准 {@code ApiResponse}。严禁在业务层直接返回 HTTP 500 或抛出底层异常。</p>
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码
     */
    private final ErrorCode errorCode;

    /**
     * 以默认错误码构造
     */
    public BizException(String message) {
        super(message);
        this.errorCode = ErrorCode.BIZ_ERROR;
    }

    /**
     * 以指定错误码构造
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }

    /**
     * 以指定错误码 + 自定义提示构造
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 带底层原因构造（原因不会暴露给前端，仅记录日志）
     */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}