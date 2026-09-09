package edu.zjut.traceqa.kbservice.config;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 多数据源配置：
 * - 主库（traceqa_kb）：@Primary，供主 Mapper 使用
 * - LightRAG 库（traceqa_lightrag）：独立 SqlSessionFactory
 */
@Configuration
@MapperScan(basePackages = "edu.zjut.traceqa.kbservice.lightrag",
        sqlSessionFactoryRef = "lightRagSqlSessionFactory")
public class LightRagDataSourceConfig {

    @Value("${app.lightrag.datasource.jdbc-url}")
    private String jdbcUrl;

    @Value("${app.lightrag.datasource.username}")
    private String username;

    @Value("${app.lightrag.datasource.password}")
    private String password;

    /** 主库 DataSource（traceqa_kb） */
    @Bean("dataSource")
    @Primary
    public HikariDataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${spring.datasource.password}") String dbPassword) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(dbUsername);
        ds.setPassword(dbPassword);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(10);
        ds.setPoolName("main-pool");
        return ds;
    }

    /** 主库 SqlSessionFactory（traceqa_kb） */
    @Bean("sqlSessionFactory")
    @Primary
    public SqlSessionFactory sqlSessionFactory(
            @Qualifier("dataSource") HikariDataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        com.baomidou.mybatisplus.core.config.GlobalConfig globalConfig = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        globalConfig.setBanner(false);
        factory.setGlobalConfig(globalConfig);
        com.baomidou.mybatisplus.core.MybatisConfiguration config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        return factory.getObject();
    }

    /** LightRAG 库 DataSource（traceqa_lightrag） */
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

    /** LightRAG 库 SqlSessionFactory（traceqa_lightrag） */
    @Bean("lightRagSqlSessionFactory")
    public SqlSessionFactory lightRagSqlSessionFactory(
            @Qualifier("lightRagDataSource") HikariDataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        com.baomidou.mybatisplus.core.config.GlobalConfig globalConfig = new com.baomidou.mybatisplus.core.config.GlobalConfig();
        globalConfig.setBanner(false);
        factory.setGlobalConfig(globalConfig);
        com.baomidou.mybatisplus.core.MybatisConfiguration config = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        return factory.getObject();
    }

    @Bean("lightRagSqlSessionTemplate")
    public SqlSessionTemplate lightRagSqlSessionTemplate(
            @Qualifier("lightRagSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean("lightRagTransactionManager")
    public DataSourceTransactionManager lightRagTransactionManager(
            @Qualifier("lightRagDataSource") HikariDataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
