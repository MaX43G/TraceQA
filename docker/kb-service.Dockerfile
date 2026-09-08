# ============================================================
# 溯知 / TraceQA 知识库服务镜像
# ============================================================
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository mvn -T 1C -pl traceqa-kb-service -am package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl

# 下载 OTel Java Agent 2.31.1（缓存挂载避免每次重复下载）
RUN --mount=type=cache,target=/opt/otel-cache \
    if [ ! -f /opt/otel-cache/opentelemetry-javaagent.jar ]; then \
      curl -fsSL -o /opt/otel-cache/opentelemetry-javaagent.jar "https://maven.aliyun.com/repository/public/io/opentelemetry/javaagent/opentelemetry-javaagent/2.31.1/opentelemetry-javaagent-2.31.1.jar"; \
    fi \
    && cp /opt/otel-cache/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=build /app/traceqa-kb-service/target/*.jar app.jar

RUN addgroup -S appuser && adduser -S -G appuser -h /home/appuser appuser \
    && mkdir -p /app/data \
    && chown -R appuser:appuser /app /home/appuser

ENV JAVA_OPTS="-javaagent:/app/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Dnacos.logging.default.config.enabled=false"

USER appuser

EXPOSE 8084

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
