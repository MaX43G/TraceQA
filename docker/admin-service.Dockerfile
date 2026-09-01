# ============================================================
# 溯知 / TraceQA 管理服务镜像
# ============================================================
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository mvn -T 1C -pl traceqa-admin-service -am package -DskipTests -B

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

COPY --from=build /app/traceqa-admin-service/target/*.jar app.jar

RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && mkdir -p /home/appuser \
    && chown -R appuser:appuser /app /home/appuser

ENV JAVA_OPTS="-javaagent:/app/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Dnacos.logging.default.config.enabled=false"

# 说明：管理服务需访问宿主机 Docker Engine（/var/run/docker.sock 挂载）以提供
# 「系统资源检测 / 无用资源清理」能力。挂载 socket 已等价于宿主机 root 权限，
# 故此处以 root 运行（与 Portainer 等容器管理工具一致）。若不需要该能力，
# 可移除此行并去掉 compose 中的 socket 挂载。
USER root

EXPOSE 8086

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]