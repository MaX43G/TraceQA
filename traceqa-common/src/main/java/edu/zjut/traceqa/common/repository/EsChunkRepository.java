package edu.zjut.traceqa.common.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import edu.zjut.traceqa.common.config.ElasticsearchClientFactory;
import edu.zjut.traceqa.common.config.ElasticsearchProperties;
import edu.zjut.traceqa.common.model.po.EsChunk;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 文档片段仓库。
 *
 * <p>提供片段索引写入、按文档 ID 删除、BM25 全文检索等操作。</p>
 */
@Repository
public class EsChunkRepository {

    private static final Logger log = LoggerFactory.getLogger(EsChunkRepository.class);

    @Resource
    private ElasticsearchClientFactory clientFactory;

    @Resource
    private ElasticsearchProperties properties;

    /**
     * 批量索引文档片段
     */
    public void indexAll(List<EsChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        try {
            ElasticsearchClient client = clientFactory.getClient();
            List<BulkOperation> operations = new ArrayList<>();
            for (EsChunk chunk : chunks) {
                operations.add(BulkOperation.of(b -> b
                        .index(i -> i
                                .index(properties.getIndexName())
                                .document(chunk))));
            }
            BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
            if (response.errors()) {
                log.warn("ES 批量索引部分失败：items={}", response.items().size());
            } else {
                log.debug("ES 批量索引完成：count={}", chunks.size());
            }
        } catch (IOException e) {
            log.error("ES 批量索引异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 按文档 ID 删除所有关联片段
     */
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            ElasticsearchClient client = clientFactory.getClient();
            DeleteByQueryRequest request = DeleteByQueryRequest.of(b -> b
                    .index(properties.getIndexName())
                    .query(q -> q.term(t -> t
                            .field("documentId")
                            .value(documentId))));
            client.deleteByQuery(request);
            log.debug("ES 已删除文档片段：documentId={}", documentId);
        } catch (IOException e) {
            log.error("ES 删除片段异常：documentId={}, err={}", documentId, e.getMessage(), e);
        }
    }

    /**
     * BM25 全文检索
     *
     * @param queryText   查询文本
     * @param topK        返回最大数量
     * @param kbId        知识库 ID 过滤（可选，null 表示不过滤）
     * @return 检索到的片段列表
     */
    public List<EsChunk> search(String queryText, int topK, Long kbId) {
        try {
            ElasticsearchClient client = clientFactory.getClient();
            SearchRequest.Builder builder = new SearchRequest.Builder()
                    .index(properties.getIndexName())
                    .query(q -> q.bool(b -> {
                        b.must(m -> m.multiMatch(mm -> mm
                                .query(queryText)
                                .fields("content", "headings")));
                        if (kbId != null) {
                            b.filter(f -> f.term(t -> t
                                    .field("knowledgeBaseId")
                                    .value(kbId)));
                        }
                        return b;
                    }))
                    .size(topK)
                    .highlight(h -> h
                            .preTags("<em>")
                            .postTags("</em>")
                            .fields("content", f -> f));

            SearchResponse<EsChunk> response = client.search(builder.build(), EsChunk.class);
            List<EsChunk> results = new ArrayList<>();
            for (Hit<EsChunk> hit : response.hits().hits()) {
                EsChunk chunk = hit.source();
                if (chunk != null) {
                    Map<String, List<String>> highlights = hit.highlight();
                    if (highlights != null && highlights.containsKey("content")) {
                        String highlighted = String.join("...", highlights.get("content"));
                        chunk.setContent(highlighted);
                    }
                    results.add(chunk);
                }
            }
            return results;
        } catch (IOException e) {
            log.error("ES 检索异常：query={}, err={}", queryText, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 健康探测
     */
    public boolean ping() {
        try {
            ElasticsearchClient client = clientFactory.getClient();
            return client.ping().value();
        } catch (Exception e) {
            log.debug("ES 健康探测失败：{}", e.getMessage());
            return false;
        }
    }
}
