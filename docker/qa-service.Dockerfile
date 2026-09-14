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

# 安装 Python3 和 uv，配置国内镜像源
ENV UV_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-pip curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl -LsSf https://astral.sh/uv/install.sh | env INSTALLER_NO_MODIFY_PATH=1 sh \
    && mv /root/.local/bin/uv /usr/local/bin/uv \
    && mv /root/.local/bin/uvx /usr/local/bin/uvx \
    && uv --version

COPY --from=build /app/traceqa-qa-service/target/*.jar app.jar

RUN rm -rf /opt/java/openjdk/lib/src.zip /opt/java/openjdk/demo \
           /opt/java/openjdk/man /opt/java/openjdk/sample /opt/java/openjdk/jmods \
    && groupadd -r appuser && useradd -r -g appuser -d /home/appuser appuser \
    && mkdir -p /home/appuser && chown -R appuser:appuser /app /home/appuser

# 切换到 appuser 预装 MCP 工具（确保缓存在正确位置）
USER appuser
RUN uv tool install free-search-mcp
USER root

ENV PATH="/home/appuser/.local/bin:$PATH"
ENV JAVA_OPTS="-javaagent:/app/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Dnacos.logging.default.config.enabled=false"

USER appuser

EXPOSE 8085

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
