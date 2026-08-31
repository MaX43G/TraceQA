package edu.zjut.traceqa.kbservice.service;

import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.common.model.po.Document;
import edu.zjut.traceqa.kbservice.mapper.DocumentMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档解析任务队列 Worker（Redis Stream）。
 *
 * <p>文档上传后任务入队，由守护线程阻塞消费并调用 {@link DocumentParseWorker} 解析。
 * Redis 不可用时降级为同步直接解析；失败按线性退避重试，重试耗尽进入死信队列。</p>
 */
@Component
public class DocumentQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentQueueWorker.class);

    private static final String STREAM_KEY = "doc:queue";
    private static final String DEAD_KEY = "doc:queue:dead";
    private static final int MAX_RETRY = 3;

    private final StringRedisTemplate stringRedisTemplate;
    private final DocumentMapper documentMapper;
    private final DocumentParseWorker parseWorker;
    private final AtomicInteger processingCount = new AtomicInteger(0);

    public DocumentQueueWorker(StringRedisTemplate stringRedisTemplate, DocumentMapper documentMapper,
                               DocumentParseWorker parseWorker) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.documentMapper = documentMapper;
        this.parseWorker = parseWorker;
    }

    /**
     * 文档任务入队（Redis 不可用则降级为直接解析）
     */
    public void enqueue(Long documentId) {
        try {
            StreamOperations<String, Object, Object> ops = stringRedisTemplate.opsForStream();
            ops.add(STREAM_KEY, Map.of("documentId", String.valueOf(documentId), "retry", "0"));
            log.debug("文档任务已入队：docId={}", documentId);
        } catch (Exception e) {
            log.warn("文档任务入队失败，降级为直接解析：docId={}, err={}", documentId, e.getMessage());
            dispatch(documentId);
        }
    }

    /**
     * 启动队列消费守护线程
     */
    @PostConstruct
    public void start() {
        Thread consumer = new Thread(this::consumeLoop, "doc-queue-consumer");
        consumer.setDaemon(true);
        consumer.start();
        log.info("文档解析任务队列消费者已启动");
    }

    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                StreamOperations<String, Object, Object> ops = stringRedisTemplate.opsForStream();
                List<MapRecord<String, Object, Object>> records = ops.read(
                        StreamReadOptions.empty().count(5).block(Duration.ofSeconds(5)),
                        StreamOffset.create(STREAM_KEY, ReadOffset.from("0")));
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    RecordId recordId = record.getId();
                    long documentId = parseLong(record.getValue().get("documentId"));
                    int retry = (int) parseLong(record.getValue().get("retry"));
                    boolean ok = false;
                    try {
                        ok = dispatch(documentId);
                    } catch (Exception e) {
                        log.warn("文档任务处理异常：docId={}, err={}", documentId, e.getMessage());
                    }
                    ops.delete(STREAM_KEY, recordId);
                    if (!ok) {
                        handleRetryOrDead(documentId, retry);
                    }
                }
            } catch (Exception e) {
                log.warn("文档任务消费异常：{}", e.getMessage());
                sleepQuietly(1000);
            }
        }
    }

    /**
     * 分发并解析单个文档任务
     */
    private boolean dispatch(Long documentId) {
        if (documentId == null) {
            return true;
        }
        Document doc = documentMapper.selectById(documentId);
        if (doc == null) {
            return true;
        }
        if (!DocumentStatus.PENDING.name().equals(doc.getStatus())) {
            log.debug("文档非待解析状态，跳过：docId={}, status={}", doc.getId(), doc.getStatus());
            return true;
        }
        byte[] content;
        try {
            content = Files.readAllBytes(Paths.get(doc.getStoredPath()));
        } catch (Exception e) {
            log.warn("文档文件读取失败：docId={}, err={}", documentId, e.getMessage());
            return false;
        }
        processingCount.incrementAndGet();
        try {
            return parseWorker.submit(doc, content, doc.getOriginalName());
        } finally {
            processingCount.decrementAndGet();
        }
    }

    /**
     * 处理重试或进入死信
     */
    private void handleRetryOrDead(Long documentId, int retry) {
        int nextRetry = retry + 1;
        if (nextRetry <= MAX_RETRY) {
            log.warn("文档解析失败，准备第 {} 次重试：docId={}", nextRetry, documentId);
            sleepQuietly(nextRetry * 5000L);
            try {
                stringRedisTemplate.opsForStream().add(STREAM_KEY,
                        Map.of("documentId", String.valueOf(documentId), "retry", String.valueOf(nextRetry)));
            } catch (Exception e) {
                log.warn("任务重新入队失败：docId={}, err={}", documentId, e.getMessage());
            }
        } else {
            log.error("文档解析重试耗尽，进入死信：docId={}", documentId);
            try {
                stringRedisTemplate.opsForStream().add(DEAD_KEY,
                        Map.of("documentId", String.valueOf(documentId), "retry", String.valueOf(retry)));
            } catch (Exception e) {
                log.warn("死信写入失败：docId={}, err={}", documentId, e.getMessage());
            }
            markFailed(documentId);
        }
    }

    private void markFailed(Long documentId) {
        try {
            Document doc = documentMapper.selectById(documentId);
            if (doc != null && !DocumentStatus.DONE.name().equals(doc.getStatus())) {
                doc.setStatus(DocumentStatus.FAILED.name());
                doc.setErrorMsg("解析重试耗尽，已进入失败队列");
                documentMapper.updateById(doc);
            }
        } catch (Exception e) {
            log.debug("死信标记失败：{}", e.getMessage());
        }
    }

    /**
     * 队列统计（pending/dead/processing）
     */
    public Map<String, Object> queueStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("pending", stringRedisTemplate.opsForStream().size(STREAM_KEY));
            stats.put("dead", stringRedisTemplate.opsForStream().size(DEAD_KEY));
        } catch (Exception e) {
            stats.put("pending", 0);
            stats.put("dead", 0);
        }
        stats.put("processing", processingCount.get());
        return stats;
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}