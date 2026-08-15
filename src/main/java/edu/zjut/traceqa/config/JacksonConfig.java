package edu.zjut.traceqa.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置。
 *
 * <p>Spring Boot 4 不再默认提供 Jackson 2 的 {@link ObjectMapper} Bean，
 * 此处显式声明并注册 Java 时间模块，保证 LocalDateTime 等类型的统一序列化。</p>
 *
 * <p>同时将 {@code Long}（雪花主键）统一序列化为字符串，避免 19 位数字超出
 * JavaScript {@code Number.MAX_SAFE_INTEGER} 造成精度丢失，前后端 ID 全程按字符串传输。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        // Long 统一转字符串（雪花 ID 精度保护）
        SimpleModule longModule = new SimpleModule();
        longModule.addSerializer(Long.class, ToStringSerializer.instance);
        longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(longModule)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}