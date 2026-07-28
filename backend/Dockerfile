# ============================================================
# AI 课程平台 - 后端镜像
# 构建阶段使用官方 Maven 镜像，避免 maven-wrapper 在 Alpine 下的兼容问题
#   （仓库 .mvn/wrapper 仅 distributionType=only-script，且缺少 maven-wrapper.jar，
#    在部分环境下 ./mvnw 无法引导，故改用官方 maven 镜像更稳健）
# 运行阶段使用 Eclipse Temurin 21 JRE（与 Spring Boot 3.5 / Java 21 对应）
# 服务监听 8080，统一接口前缀 /api（见 application.yaml）
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B clean package -DskipTests

# ==== 运行阶段 ====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 安装 curl：容器健康探针（docker-compose healthcheck）依赖 curl 探活 /api/actuator/health
RUN apk add --no-cache curl
COPY --from=builder /app/target/*.jar app.jar
RUN mkdir -p uploads
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    TZ=Asia/Shanghai
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
