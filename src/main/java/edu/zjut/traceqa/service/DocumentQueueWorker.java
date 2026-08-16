package edu.zjut.traceqa.service;

import edu.zjut.traceqa.common.enums.DocumentStatus;
import edu.zjut.traceqa.entity.Document;
import edu.zjut.traceqa.mapper.DocumentMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
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
 * 文档异步任务队列（Redis Stream）。
 *
 * <p>上传接口将任务推入 Redis Stream，本组件以独立守护线程**同步**消费并解析
 * （解析受 TPM 限速，串行更合理）。失败任务自动重试（指数退避），超过上限进入
 * 死信队列 {@code doc:queue:dead}。Redis 不可用时上传直接降级为同步解析。</p>
 */
@Slf4j
@Component
public class DocumentQueueWorker {

    private static final String STREAM_KEY = "doc:queue";
    private static final String DEAD_KEY = "doc:queue:dead";
    /** 最大重试次数 */
    private static final int MAX_RETRY = 3;
    /** 重试退避基础（秒）：retry 1 → 5s，2 → 10s，3 → 15s */

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentParseWorker parseWorker;

    /** 当前处理中的文档数（统计用） */
    private final AtomicInteger processingCount = new AtomicInteger(0);

    /** 入队：提交一个文档解析任务 */
    public void enqueue(Long documentId) {
        try {
            StreamOperations<String, Object, Object> ops = stringRedisTemplate.opsForStream();
            ops.add(STREAM_KEY, Map.of("documentId", String.valueOf(documentId), "retry", "0"));
            log.debug("文档任务已入队：docId={}", documentId);
        } catch (Exception e) {
            log.warn("文档任务入队失败，降级为直接解析：docId={}, err={}", documentId, e.getMessage());
            dispatch(Long.valueOf(documentId), 0);
        }
    }

    /** 启动消费线程（守护线程） */
    @PostConstruct
    public void start() {
        Thread consumer = new Thread(this::consumeLoop, "doc-queue-consumer");
        consumer.setDaemon(true);
        consumer.start();
        log.info("文档解析任务队列消费者已启动");
    }

    /** 阻塞消费循环：读任务 -> 同步解析 -> 成功删除 / 失败重试或进死信 */
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
                        ok = dispatch(documentId, retry);
                    } catch (Exception e) {
                        log.warn("文档任务处理异常：docId={}, err={}", documentId, e.getMessage());
                    }
                    if (ok) {
                        ops.delete(STREAM_KEY, recordId);
                    } else {
                        ops.delete(STREAM_KEY, recordId);
                        handleRetryOrDead(documentId, retry);
                    }
                }
            } catch (Exception e) {
                log.warn("文档任务消费异常：{}", e.getMessage());
                sleepQuietly(1000);
            }
        }
    }

    /** 处理单个任务：读取文档并同步解析，返回是否成功 */
    private boolean dispatch(Long documentId, int retry) {
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
            return parseWorker.parse(doc, content, doc.getOriginalName());
        } finally {
            processingCount.decrementAndGet();
        }
    }

    /** 失败处理：重试（指数退避）或进入死信队列 */
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
            markFailed(documentId, "解析重试耗尽，已进入失败队列");
        }
    }

    /** 死信兜底：确保文档标记为失败（正常情况下 parse 已标记） */
    private void markFailed(Long documentId, String message) {
        try {
            Document doc = documentMapper.selectById(documentId);
            if (doc != null && !DocumentStatus.DONE.name().equals(doc.getStatus())) {
                doc.setStatus(DocumentStatus.FAILED.name());
                doc.setErrorMsg(message);
                documentMapper.updateById(doc);
            }
        } catch (Exception e) {
            log.debug("死信标记失败：{}", e.getMessage());
        }
    }

    /** 解析队列统计（管理后台可视化） */
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
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
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
