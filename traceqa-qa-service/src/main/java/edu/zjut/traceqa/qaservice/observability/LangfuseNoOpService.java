package edu.zjut.traceqa.qaservice.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Langfuse 追踪服务空实现（当 Langfuse 未启用时）。
 *
 * <p>所有方法均为空操作，避免业务代码中出现大量 if-else 判断。
 * 独立实现而非继承，避免父类 @PostConstruct 初始化问题。</p>
 */
@Service
@ConditionalOnProperty(name = "app.langfuse.enabled", havingValue = "false", matchIfMissing = true)
public class LangfuseNoOpService extends LangfuseTraceService {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String createTrace(String name, Map<String, Object> metadata) {
        return UUID.randomUUID().toString();
    }

    @Override
    public void updateTrace(String traceId, Map<String, Object> metadata) {
        // no-op
    }

    @Override
    public String getTraceUrl(String traceId) {
        return null;
    }

    @Override
    public String startSpan(String traceId, String name, String input) {
        return UUID.randomUUID().toString();
    }

    @Override
    public void endSpan(String spanId, String output, Map<String, Object> metadata) {
        // no-op
    }

    @Override
    public void failSpan(String spanId, String errorMessage) {
        // no-op
    }

    @Override
    public void recordEvent(String traceId, String name, Map<String, Object> data) {
        // no-op
    }

    @Override
    public String startGeneration(String traceId, String name, String model, String input) {
        return UUID.randomUUID().toString();
    }

    @Override
    public void endGeneration(String generationId, String output, Long usageTokens,
                              Long promptTokens, Long completionTokens) {
        // no-op
    }

    @Override
    public void flushBatch() {
        // no-op
    }

    @Override
    public void flush() {
        // no-op
    }
}
