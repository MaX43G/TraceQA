package edu.zjut.traceqa.common.util;

import jakarta.annotation.Resource;
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

    @Resource
    private ObjectMapper objectMapper;

    

    /** 对象序列化为 JSON 字符串，失败返回 null */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败：{}", e.getMessage());
            return null;
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