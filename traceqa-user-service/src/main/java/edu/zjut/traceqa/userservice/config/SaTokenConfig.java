package edu.zjut.traceqa.userservice.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.SaManager;
import edu.zjut.traceqa.common.auth.SaTokenDaoRedis;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用户服务 Sa-Token 配置。
 *
 * <p>注册基于 Redis 的持久化 Dao，使登录态写入共享 Redis，网关与其他服务
 * 据此完成统一鉴权与用户信息透传。</p>
 */
@Configuration
public class SaTokenConfig {

    /**
     * 构造并注册 Sa-Token 的 Redis 持久化 Dao。
     *
     * @param stringRedisTemplate Redis 模板
     * @return Sa-Token Dao
     */
    @Bean
    public SaTokenDao saTokenDao(StringRedisTemplate stringRedisTemplate) {
        SaTokenDao dao = new SaTokenDaoRedis(stringRedisTemplate);
        SaManager.setSaTokenDao(dao);
        return dao;
    }
}