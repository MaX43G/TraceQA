package edu.zjut.traceqa.gateway.config;

import edu.zjut.traceqa.common.auth.SaTokenDaoRedis;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关自定义配置。
 *
 * <p>承载无需鉴权的公开路径列表（{@code traceqa.gateway.public-paths}，
 * 可从 application.yaml 覆盖），供 {@code SaReactorFilter} 与鉴权过滤器使用。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "traceqa.gateway")
public class TraceQaGatewayProperties {

    /**
     * 无需登录即可访问的路径列表（从配置注入）
     */
    private List<String> publicPaths = new ArrayList<>();

    /**
     * 构造 Sa-Token 的 Redis 持久化 Dao 并全局注册，使网关与业务服务共享登录态。
     *
     * @param stringRedisTemplate Redis 模板
     * @return Sa-Token Dao
     */
    @Bean
    public SaTokenDaoRedis saTokenDao(StringRedisTemplate stringRedisTemplate) {
        return new SaTokenDaoRedis(stringRedisTemplate);
    }

}