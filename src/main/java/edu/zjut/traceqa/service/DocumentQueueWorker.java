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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 文档异步任务队列（Redis Stream）。
 *
 * <p>上传接口将任务推入 Redis Stream，本组件以独立守护线程阻塞消费，
 * 再委托给 {@link DocumentParseWorker#parse}（docExecutor 线程池）执行解析。
 * 解耦上传与处理，Redis 不可用时上传照常入库（任务消费会延迟/重试），
 * 队列消费后即删除，避免重复处理。</p>
 */
@Slf4j
@Component
public class DocumentQueueWorker {

    private static final String STREAM_KEY = "doc:queue";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private DocumentMapper documentMapper;

    @Resource
    private DocumentParseWorker parseWorker;

    /** 入队：提交一个文档解析任务 */
    public void enqueue(Long documentId) {
        try {
            StreamOperations<String, Object, Object> ops = stringRedisTemplate.opsForStream();
            RecordId id = ops.add(STREAM_KEY, Collections.singletonMap("documentId", String.valueOf(documentId)));
            log.debug("文档任务已入队：docId={}, recordId={}", documentId, id);
        } catch (Exception e) {
            log.warn("文档任务入队失败，将降级为直接解析：docId={}, err={}", documentId, e.getMessage());
            // Redis 不可用时降级：直接异步解析
            Document doc = documentMapper.selectById(documentId);
            if (doc != null) {
                dispatch(doc);
            }
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

    /** 阻塞消费循环：从 Stream 读取任务 -> 处理 -> 删除记录 */
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
                    try {
                        Object rawId = record.getValue().get("documentId");
                        if (rawId != null) {
                            dispatch(documentMapper.selectById(Long.valueOf(String.valueOf(rawId))));
                        }
                    } finally {
                        ops.delete(STREAM_KEY, record.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("文档任务消费异常：{}", e.getMessage());
                sleepQuietly(1000);
            }
        }
    }

    /** 派发单个文档解析任务（仅处理待解析状态） */
    private void dispatch(Document doc) {
        if (doc == null) {
            return;
        }
        if (!DocumentStatus.PENDING.name().equals(doc.getStatus())) {
            log.debug("文档非待解析状态，跳过：docId={}, status={}", doc.getId(), doc.getStatus());
            return;
        }
        try {
            byte[] content = Files.readAllBytes(Paths.get(doc.getStoredPath()));
            parseWorker.parse(doc, content, doc.getOriginalName());
        } catch (Exception e) {
            log.warn("文档任务读取失败：docId={}, err={}", doc.getId(), e.getMessage());
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
