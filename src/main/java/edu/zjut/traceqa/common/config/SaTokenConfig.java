package edu.zjut.traceqa.common.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import edu.zjut.traceqa.common.auth.SaTokenDaoRedis;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * sa-token 配置：将默认内存存储替换为 Redis 持久化。
 *
 * <p>sa-token 的登录态、token 与会话数据经 {@link SaTokenDaoRedis} 存 Redis，
 * 重启/多实例共享登录状态。</p>
 */
@Configuration
public class SaTokenConfig {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Bean
    public SaTokenDao saTokenDao() {
        SaTokenDaoRedis dao = new SaTokenDaoRedis(stringRedisTemplate);
        SaManager.setSaTokenDao(dao);
        return dao;
    }
}
