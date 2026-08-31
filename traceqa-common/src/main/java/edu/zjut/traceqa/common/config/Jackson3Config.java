package edu.zjut.traceqa.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson 3（Spring Boot 4 默认 HTTP JSON 引擎）定制。
 *
 * <p>将 {@code Long}（雪花主键）统一序列化为字符串，避免 19 位数字超出
 * JavaScript {@code Number.MAX_SAFE_INTEGER} 造成精度丢失，前后端 ID 全程按字符串传输。</p>
 */
@Configuration
public class Jackson3Config {

    /**
     * 全局 Long 序列化为字符串
     */
    @Bean
    public JsonMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("long-to-string");
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.addModule(module);
        };
    }
}