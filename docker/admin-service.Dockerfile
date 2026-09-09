# ============================================================
# 溯知 / TraceQA 管理服务镜像
# ============================================================
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build

WORKDIR /app

COPY settings.xml /root/.m2/settings.xml

COPY . .

RUN --mount=type=cache,target=/root/.m2/repository mvn -T 1C -pl traceqa-admin-service -am package -DskipTests -B

# ---- 运行阶段 ----
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=build /app/traceqa-admin-service/target/*.jar app.jar

RUN rm -rf /opt/java/openjdk/lib/src.zip /opt/java/openjdk/demo \
           /opt/java/openjdk/man /opt/java/openjdk/sample /opt/java/openjdk/jmods

# 管理服务需访问宿主机 Docker Engine（/var/run/docker.sock 挂载）以提供
# 「系统资源检测 / 无用资源清理」能力。挂载 socket 已等价于宿主机 root 权限，
# 故此处以 root 运行（与 Portainer 等容器管理工具一致）。
USER root

EXPOSE 8086

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
