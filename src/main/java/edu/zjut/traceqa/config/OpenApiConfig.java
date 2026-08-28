package edu.zjut.traceqa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 规范配置。
 *
 * <p>后端在 {@code /v3/api-docs} 自动生成 OpenAPI JSON，前端使用
 * {@code umijs/openapi} 依据该规范自动生成 TypeScript API 客户端，
 * 杜绝前后端手写魔法字符串。</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 组装 OpenAPI 文档元信息与 JWT 认证声明
     */
    @Bean
    public OpenAPI traceQaOpenApi() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("溯知 / TraceQA API")
                        .description("""
                                《数据挖掘》课程 RAG 智能问答平台接口规范
                                
                                统一响应结构：code/msg/data/traceId。前端仅依据 code 判断成功或失败。""")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}