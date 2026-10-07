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
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
