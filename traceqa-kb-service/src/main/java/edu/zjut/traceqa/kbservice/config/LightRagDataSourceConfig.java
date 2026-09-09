package edu.zjut.traceqa.kbservice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * LightRAG 数据库（traceqa_lightrag）独立数据源配置。
 */
@Configuration
public class LightRagDataSourceConfig {

    @Value("${app.lightrag.datasource.jdbc-url}")
    private String jdbcUrl;

    @Value("${app.lightrag.datasource.username}")
    private String username;

    @Value("${app.lightrag.datasource.password}")
    private String password;

    @Bean("lightRagDataSource")
    public HikariDataSource lightRagDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(2);
        ds.setPoolName("lightRag-pool");
        return ds;
    }

    @Bean("lightRagJdbcTemplate")
    public JdbcTemplate lightRagJdbcTemplate() {
        return new JdbcTemplate(lightRagDataSource());
    }
}
