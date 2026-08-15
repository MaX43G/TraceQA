package edu.zjut.traceqa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 配置。
 *
 * <p>仅引入 {@code spring-security-crypto} 提供 BCrypt 密码加密，
 * 不引入完整 Spring Security 框架，保持架构极简。</p>
 */
@Configuration
public class SecurityBeans {

    /** BCrypt 密码编码器 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}