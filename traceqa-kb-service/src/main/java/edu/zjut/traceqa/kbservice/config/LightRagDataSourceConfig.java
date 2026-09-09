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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * LightRAG 数据库（traceqa_lightrag）独立数据源配置。
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
