package edu.zjut.traceqa.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JSON 序列化工具。
 *
 * <p>集中封装实体与 DTO 间的 JSON 转换，所有解析失败均返回空集合，
 * 实现「优雅降级」，绝不因脏数据抛出异常。</p>
 */
@Slf4j
@Component
public class JsonUtils {

    private final ObjectMapper objectMapper;

    public JsonUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 对象序列化为 JSON 字符串，失败返回 null */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败：{}", e.getMessage());
            return null;
        }
    }

    /** 解析 JSON 为对象，失败返回默认值 */
    public <T> T parse(String json, Class<T> clazz, T defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("JSON 解析失败：{}", e.getMessage());
            return defaultValue;
        }
    }

    /** 解析 JSON 为对象列表，失败返回空列表 */
    public <T> List<T> parseList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("JSON 列表解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 解析 JSON 为对象列表（按元素类型），失败返回空列表 */
    public <T> List<T> parseList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            log.warn("JSON 列表解析失败：{}", e.getMessage());
            return List.of();
        }
    }
}