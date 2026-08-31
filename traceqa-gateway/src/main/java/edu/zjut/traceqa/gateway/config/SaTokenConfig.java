package edu.zjut.traceqa.gateway.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.enums.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 网关 Sa-Token 鉴权配置。
 *
 * <p>通过 {@link SaReactorFilter} 对进入网关的所有请求统一鉴权：
 * 公开路径直接放行，其余业务路径需登录；未登录统一返回 40100。</p>
 */
@Configuration
public class SaTokenConfig {

    private final List<String> publicPaths;

    public SaTokenConfig(TraceQaGatewayProperties properties) {
        this.publicPaths = properties.getPublicPaths();
    }

    /**
     * 注册 Sa-Token 响应式鉴权过滤器。
     *
     * <p>匹配 {@code /api/**} 业务路径，排除公开路径后执行登录校验。
     * 鉴权通过后由 {@code AuthHeaderGlobalFilter} 将用户信息透传下游。</p>
     *
     * @return Sa-Token 响应式过滤器
     */
    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                .addInclude("/**")
                .setAuth(_ -> SaRouter.match("/api/**")
                        .notMatch(publicPaths)
                        .check(_ -> StpUtil.checkLogin()))
                .setError(this::buildError);
    }

    /**
     * 构造统一鉴权失败响应。
     *
     * @param e 鉴权异常
     * @return 统一响应体
     */
    private ApiResponse<Void> buildError(Throwable e) {
        if (e instanceof NotLoginException) {
            return ApiResponse.fail(ErrorCode.UNAUTHORIZED);
        }
        if (e instanceof NotPermissionException) {
            return ApiResponse.fail(ErrorCode.FORBIDDEN);
        }
        return ApiResponse.fail(ErrorCode.TOKEN_INVALID);
    }
}