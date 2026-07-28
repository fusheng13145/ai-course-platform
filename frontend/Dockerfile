# ============================================================
# AI 课程平台 - 前端镜像
# 构建阶段使用 Node 20（Vite 8 要求 Node >= 20.19）
# 运行阶段使用 Nginx 提供 vite build 产物（dist）静态资源
# ============================================================
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ==== 运行阶段 ====
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
