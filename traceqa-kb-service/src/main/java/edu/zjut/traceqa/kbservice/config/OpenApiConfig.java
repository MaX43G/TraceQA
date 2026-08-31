package edu.zjut.traceqa.kbservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库服务配置与 OpenAPI 文档。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class OpenApiConfig {

    /**
     * 知识库服务 OpenAPI 文档定义
     */
    @Bean
    public OpenAPI kbServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("溯知 / TraceQA - 知识库服务 API")
                        .description("《数据挖掘》课程 RAG 智能问答平台 - 知识库服务接口规范\n\n统一响应结构：code/msg/data/traceId。")
                        .version("1.0.0"));
    }
}