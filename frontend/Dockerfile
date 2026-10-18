# ============================================================
# AI 课程平台 - 前端镜像
# 构建阶段使用 Node 20（Vite 8 要求 Node >= 20.19）
# 运行阶段使用 Nginx 提供 vite build 产物（dist）静态资源
#
# 【基础镜像固定】2026-10-07 与后端镜像同口径钉到「tag + manifest digest」。
# 实测：node v20.20.2、nginx/1.31.6（`docker run --rm --entrypoint nginx nginx:alpine -v`）。
# `nginx:alpine` 是上游滚动 tag，单看 tag 不可复现，故由 digest 兜住；换版本时覆盖 build-arg。
# ============================================================
ARG NODE_IMAGE=node:20-alpine@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293
ARG NGINX_IMAGE=nginx:alpine@sha256:df221db836e1754089190208cee7eeda94f233197056426eda74a43ab1abeac2
FROM ${NODE_IMAGE} AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ==== 运行阶段 ====
FROM ${NGINX_IMAGE}
# 【DR-34 非 root 化】2026-10-08：沿用已钉住的 nginx:alpine，不引入 nginxinc/nginx-unprivileged
# （多一个上游就要多一套 tag+digest 维护）。nginx:alpine 自带 uid/gid=101 的 nginx 账号，
# 但 /var/cache/nginx 与 /run 属主是 root，非 root 起不来 → 显式把运行时可写落点交给 101。
# 监听端口 80→8080：<1024 的端口在 Linux 上只有 root 能绑，这是本改动唯一的功能性代价，
#   宿主机侧端口不变（compose 仍默认 80），对外访问 URL 无感知。
RUN mkdir -p /var/cache/nginx /var/log/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/log/nginx \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid
COPY --chown=nginx:nginx --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
USER nginx:nginx
EXPOSE 8080
# 说明：/etc/nginx/nginx.conf 是上游文件，其中 `user nginx;` 在非 root master 下会打一条
# WARN（"the 'user' directive makes sense only if the master process runs with super-user
# privileges"）。实测仅为告警、不影响启动与转发，属预期日志，不是缺陷。
CMD ["nginx", "-g", "daemon off;"]
