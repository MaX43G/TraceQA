package edu.zjut.traceqa.common.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.Getter;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 客户端工厂。
 *
 * <p>创建并管理 {@link ElasticsearchClient} 实例，供知识库服务（写入）与问答服务（检索）共用。</p>
 */
@Component
@EnableAutoConfiguration
@EnableConfigurationProperties(ElasticsearchProperties.class)
public class ElasticsearchClientFactory {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchClientFactory.class);

    @Resource
    private ElasticsearchProperties properties;

    @Getter
    private ElasticsearchClient client;

    @Getter
    private RestClient restClient;

    @PostConstruct
    public void init() {
        HttpHost host = new HttpHost(properties.getHost(), properties.getPort(), "http");
        restClient = RestClient.builder(host)
                .setRequestConfigCallback(b -> b
                        .setConnectTimeout(properties.getConnectTimeout())
                        .setSocketTimeout(properties.getSocketTimeout()))
                .build();
        // ES Java Client 8.x 需要 Jackson 2.x ObjectMapper（com.fasterxml）
        com.fasterxml.jackson.databind.ObjectMapper jackson2Mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(jackson2Mapper));
        client = new ElasticsearchClient(transport);
        log.info("Elasticsearch 客户端已初始化：{}:{}", properties.getHost(), properties.getPort());
    }

    @PreDestroy
    public void close() {
        try {
            restClient.close();
        } catch (Exception e) {
            log.debug("ES RestClient 关闭异常：{}", e.getMessage());
        }
    }
}
