# ============================================================
# 溯知 / TraceQA 问答服务镜像
# ============================================================
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository mvn -T 1C -pl traceqa-qa-service -am package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 下载 OTel Java Agent 2.31.1（经 aliyun 镜像源；缓存挂载避免每次重复下载）
RUN --mount=type=cache,target=/opt/otel-cache \
    if [ ! -f /opt/otel-cache/opentelemetry-javaagent.jar ]; then \
      curl -fsSL -o /opt/otel-cache/opentelemetry-javaagent.jar "https://maven.aliyun.com/repository/public/io/opentelemetry/javaagent/opentelemetry-javaagent/2.31.1/opentelemetry-javaagent-2.31.1.jar"; \
    fi \
    && cp /opt/otel-cache/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY --from=build /app/traceqa-qa-service/target/*.jar app.jar

RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && mkdir -p /home/appuser \
    && chown -R appuser:appuser /app /home/appuser

ENV JAVA_OPTS="-javaagent:/app/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Dnacos.logging.default.config.enabled=false"

USER appuser

EXPOSE 8085

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]