# ============================================================
# 溯知 / TraceQA 网关镜像（Spring Cloud Gateway + Nacos）
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository mvn -T 1C -pl traceqa-gateway -am package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# 清理 JRE 冗余文件
RUN rm -rf /opt/java/openjdk/lib/src.zip \
           /opt/java/openjdk/demo \
           /opt/java/openjdk/man \
           /opt/java/openjdk/sample \
           /opt/java/openjdk/jmods \
    2>/dev/null; true

COPY --from=build /app/traceqa-gateway/target/*.jar app.jar

RUN addgroup -S appuser && adduser -S -G appuser -h /home/appuser appuser \
    && chown -R appuser:appuser /app /home/appuser

ENV JAVA_OPTS="-javaagent:/app/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Dnacos.logging.default.config.enabled=false"

USER appuser

EXPOSE 8080

# Alpine 自带 busybox wget，无需安装 curl
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
