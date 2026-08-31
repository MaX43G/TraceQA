package edu.zjut.traceqa.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户服务 OpenAPI 文档配置。
 *
 * <p>声明统一响应说明与 Bearer 鉴权方案，供网关聚合生成全局接口文档。</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 用户服务 OpenAPI 文档定义
     */
    @Bean
    public OpenAPI userServiceOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("溯知 / TraceQA - 用户服务 API")
                        .description("""
                                《数据挖掘》课程 RAG 智能问答平台 - 用户服务接口规范
                                
                                统一响应结构：code/msg/data/traceId。前端仅依据 code 判断成功或失败。""")
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