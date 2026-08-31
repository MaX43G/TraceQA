# ============================================================
# 溯知 / TraceQA 网关镜像（Spring Cloud Gateway + Nacos）
# 多阶段构建：阶段一编译（Maven），阶段二运行（JRE 25）
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository mvn -T 1C -pl traceqa-gateway -am package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:25-jre-noble

# OpenTelemetry Java Agent 版本（自动埋点追踪，供 Tempo Traces to Logs）
ARG OTEL_AGENT_VERSION=2.31.1

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 下载 OTel Java Agent（经 aliyun 镜像源）
RUN curl -fsSL -o /app/opentelemetry-javaagent.jar \
    "https://maven.aliyun.com/repository/public/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar"

COPY --from=build /app/traceqa-gateway/target/*.jar app.jar

RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && mkdir -p /home/appuser \
    && chown -R appuser:appuser /app /home/appuser

ENV JAVA_OPTS="-javaagent:/app/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Dnacos.logging.default.config.enabled=false"

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

