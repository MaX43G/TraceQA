# ============================================================
# 溯知 / TraceQA 后端镜像（Spring Boot + Java 25）
# 多阶段构建：阶段一编译，阶段二运行（JRE 25）
# ============================================================

# ---- 构建阶段 ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app

# 利用 Maven Wrapper 下载依赖（only-script 分发，自动拉取 Maven）
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>\
<settings>\
  <mirrors>\
    <mirror>\
      <id>aliyun</id>\
      <mirrorOf>central</mirrorOf>\
      <name>Aliyun Maven Mirror</name>\
      <url>https://maven.aliyun.com/repository/public</url>\
    </mirror>\
  </mirrors>\
</settings>' > /root/.m2/settings.xml
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

# 拷贝源码并打包
COPY src src
RUN ./mvnw -q -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:25-jdk-noble
WORKDIR /app

# 健康检查所需工具
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 从构建阶段拷贝产物
COPY --from=build /app/target/*.jar app.jar

# 运行参数
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx768m"

EXPOSE 8080

# 健康检查：探测健康接口
HEALTHCHECK --interval=15s --timeout=5s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
