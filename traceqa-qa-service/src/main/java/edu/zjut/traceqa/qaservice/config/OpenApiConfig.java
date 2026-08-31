package edu.zjut.traceqa.qaservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 问答服务配置与 OpenAPI 文档。
 */
@Configuration
@EnableConfigurationProperties(QaProperties.class)
public class OpenApiConfig {

    /**
     * 问答服务 OpenAPI 文档定义
     */
    @Bean
    public OpenAPI qaServiceOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("溯知 / TraceQA - 问答服务 API")
                        .description("""
                                《数据挖掘》课程 RAG 智能问答平台 - 问答服务接口规范
                                
                                统一响应结构：code/msg/data/traceId。SSE 流式对话事件：thinking/delta/references/stats/done/error。""")
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