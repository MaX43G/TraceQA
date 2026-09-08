package edu.zjut.traceqa.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Elasticsearch 连接配置属性。
 *
 * <p>供知识库服务（索引写入）与问答服务（BM25 检索）共享。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {

    /** ES 地址 */
    private String host = "localhost";

    /** ES 端口 */
    private int port = 9200;

    /** 索引名称 */
    private String indexName = "traceqa_chunks";

    /** 连接超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读取超时（毫秒） */
    private int socketTimeout = 120000;

    /**
     * 完整的 ES 地址（http://host:port）
     */
    public String getEndpoint() {
        return "http://" + host + ":" + port;
    }
}
