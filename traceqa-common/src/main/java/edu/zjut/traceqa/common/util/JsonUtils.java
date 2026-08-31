package edu.zjut.traceqa.common.util;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * JSON 序列化/反序列化工具（基于 Spring Boot 4 默认的 Jackson 3）。
 *
 * <p>统一封装 {@link ObjectMapper} 的调用，序列化失败返回 null、反序列化失败返回空集合，
 * 避免在业务代码中频繁捕获 JSON 异常。</p>
 */
@Component
public class JsonUtils {

    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 序列化为 JSON 字符串，失败返回 null
     */
    public String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 JSON 数组为指定元素类型的列表，失败返回空列表
     */
    public <T> List<T> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}