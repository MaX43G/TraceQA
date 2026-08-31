package edu.zjut.traceqa.fileservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件服务 OpenAPI 文档配置。
 */
@Configuration
public class OpenApiConfig {

    /**
     * 文件服务 OpenAPI 文档定义
     */
    @Bean
    public OpenAPI fileServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("溯知 / TraceQA - 文件服务 API")
                        .description("《数据挖掘》课程 RAG 智能问答平台 - 文件服务接口规范\n\n统一响应结构：code/msg/data/traceId。")
                        .version("1.0.0"));
    }
}