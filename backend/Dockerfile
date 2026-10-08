# ============================================================
# AI 课程平台 - 后端镜像
# 构建阶段使用官方 Maven 镜像，避免 maven-wrapper 在 Alpine 下的兼容问题
#   （仓库 .mvn/wrapper 仅 distributionType=only-script，且缺少 maven-wrapper.jar，
#    在部分环境下 ./mvnw 无法引导，故改用官方 maven 镜像更稳健）
# 运行阶段使用 Eclipse Temurin 21 JRE（与 Spring Boot 3.5 / Java 21 对应）
# 服务监听 8080，统一接口前缀 /api（见 application.yaml）
#
# 【基础镜像固定】2026-10-07 按「上游 tag + manifest digest」钉住：tag 供人读与升级 diff，
# digest 保证可复现（同一 tag 上游会滚动重发）。核对方式：
#   docker pull <image> 后  docker image inspect <image> --format '{{.RepoDigests}}'
#   docker run --rm --entrypoint <bin> <image> -version
# 实测：maven 3.9.16 + Temurin 21.0.12.1+1。构建期如需换版本，覆盖这两个 build-arg 即可。
# ============================================================
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B clean package -DskipTests

# ==== 运行阶段 ====
FROM ${RUNTIME_IMAGE}
WORKDIR /app
# 安装 curl：容器健康探针（docker-compose healthcheck）依赖 curl 探活 /api/actuator/health/probe
RUN apk add --no-cache curl
# 【DR-34 非 root 化】2026-10-08：原镜像未声明 USER → 进程以 UID 0 运行，被 Trivy misconfig
# DS-0002（HIGH）点红。容器内 Java 一旦被 RCE，root 可直接改挂载点与 /run/secrets，故按
# 「能修绝不豁免」在本镜像根治，而不是继续靠 .trivyignore 兜。
#   · UID/GID 固定 10001：命名卷的属主靠数字 UID 传递，不能依赖用户名解析；
#   · uploads/ 与 backups/ 在 chown 之前建好，见下方卷属主说明；
#   · busybox 的 adduser 只有 `-h DIR`（没有 GNU 的 `-d`），`-H` 表示不新建目录——/app 已由 WORKDIR 建好。
RUN addgroup -g 10001 -S app \
    && adduser -u 10001 -S -G app -h /app -H -D app \
    && mkdir -p /app/uploads /app/backups \
    && chown -R app:app /app
COPY --chown=app:app --from=builder /app/target/*.jar app.jar
# ⚠ 命名卷属主只在**卷首次创建**时从镜像目录继承（Docker 的 volume chown-on-create 语义）。
#   存量 uploads-data / backups-data 卷仍是 root:root → 升级后上传与备份会 500，
#   上线前必须一次性迁移（处置命令与取证见 docs/项目手册.md §24.2.4）。
USER app:app
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    TZ=Asia/Shanghai
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
