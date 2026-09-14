# ============================================================
# 溯知 / TraceQA 问答服务镜像
# ============================================================
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository mvn -T 1C -pl traceqa-qa-service -am package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:25-jre

WORKDIR /app

# 安装 Python3 和 uv（用于 MCP 联网搜索）
RUN apk add --no-cache python3 py3-pip curl \
    && curl -LsSf https://astral.sh/uv/install.sh | env INSTALLER_NO_MODIFY_PATH=1 sh \
    && mv /root/.local/bin/uv /usr/local/bin/uv \
    && mv /root/.local/bin/uvx /usr/local/bin/uvx \
    && uv --version

COPY --from=build /app/traceqa-qa-service/target/*.jar app.jar

RUN rm -rf /opt/java/openjdk/lib/src.zip /opt/java/openjdk/demo \
           /opt/java/openjdk/man /opt/java/openjdk/sample /opt/java/openjdk/jmods \
    && addgroup -S appuser && adduser -S -G appuser -h /home/appuser appuser \
    && chown -R appuser:appuser /app /home/appuser
ENV JAVA_OPTS="-javaagent:/app/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Dnacos.logging.default.config.enabled=false"

USER appuser

EXPOSE 8085

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
