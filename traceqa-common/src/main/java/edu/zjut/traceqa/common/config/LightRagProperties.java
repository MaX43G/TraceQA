package edu.zjut.traceqa.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LightRAG 服务配置属性。
 *
 * <p>LightRAG 是知识图谱/向量检索的统一后端，知识库服务、问答服务与管理服务共享。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.lightrag")
public class LightRagProperties {

    /** LightRAG 服务地址 */
    private String baseUrl = "http://localhost:9621";

    /** API Key */
    private String apiKey = "";

    /** 检索返回的最大片段数 */
    private int topK = 8;

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时（毫秒） */
    private int readTimeout = 120000;
}