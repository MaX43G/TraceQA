package edu.zjut.traceqa.adminservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 管理服务配置与 OpenAPI 文档。
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class OpenApiConfig {

    /**
     * 管理服务 OpenAPI 文档定义
     */
    @Bean
    public OpenAPI adminServiceOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("溯知 / TraceQA - 管理服务 API")
                        .description("《数据挖掘》课程 RAG 智能问答平台 - 管理服务接口规范\n\n统一响应结构：code/msg/data/traceId。")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}