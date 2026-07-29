# AI 助教系统 — 前端

> 基于 Vue 3 + Vite 8 + Element Plus 的智能在线教育平台前端

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Vue 3 (Composition API) | 3.5.x |
| 构建 | Vite | 8.x |
| UI 库 | Element Plus | 2.4.x |
| 图表 | ECharts + vue-echarts | 5.x / 6.x |
| 状态管理 | Pinia | 2.1.x |
| 路由 | Vue Router | 4.2.x |
| 请求库 | Axios | 1.7.x |
| 测试 | Vitest + @vue/test-utils | 1.6.x / 2.4.x |
| E2E | Playwright | — |
| 表格处理 | SheetJS (xlsx) | 0.20.x |

## 目录结构

```
frontend/
├── src/
│   ├── api/                  # 37 个 API 模块（~380 个导出函数）
│   │   ├── request.js        # Axios 封装（拦截器/重试/去重）
│   │   ├── auth.js           # 认证相关
│   │   ├── exam.js           # 考试/试卷 API
│   │   ├── course.js         # 课程 API
│   │   ├── ai.js             # AI 问答/流式 API
│   │   └── ...
│   ├── views/                # 66 个页面
│   │   ├── auth/             # 登录/注册/找回密码
│   │   ├── student/          # 学生端（Dashboard/学习/AI助手/考试）
│   │   ├── teacher/          # 教师端（驾驶舱/课程/题库/学情）
│   │   └── admin/            # 管理端（用户/课程/系统配置/审计）
│   ├── router/               # 路由配置（62 条路由，含懒加载）
│   ├── stores/               # Pinia 状态管理
│   ├── composables/          # 组合式函数（图表主题/提交防重）
│   ├── components/           # 通用组件
│   ├── layouts/              # 三端布局（AppShell/TeacherLayout/AdminLayout）
│   ├── utils/                # 工具函数（Sanitize/WebSocket/日期格式）
│   └── styles/               # 全局样式（CSS 变量/设计令牌/暗色主题）
├── public/                   # 静态资源
├── dist/                     # 构建输出
├── vitest.config.ts          # 测试配置
└── package.json
```

## 快速启动

### 1. 环境要求
- Node.js 20.19+（Vite 8 要求）
- 后端服务运行中（`:8080` 或配置代理）

### 2. 安装依赖
```bash
npm install
```

### 3. 启动开发服务器
```bash
npm run dev
```
开发服务器默认启动在 `http://localhost:5173`，API 请求通过 Vite proxy 代理至 `http://localhost:8080/api`。

### 4. 构建生产版本
```bash
npm run build
```
构建产物输出至 `dist/` 目录，可直接部署至 Nginx 等静态服务器。

## 关键约定

### 认证与路由
- 未登录用户自动重定向至 `/login`（保留目标 URL，登录后跳回）
- 越权访问（角色不匹配）跳转 `/403`
- JWT 存放于 HttpOnly Cookie，前端不持有令牌

### API 调用
- 统一通过 `@/api/` 模块调用，不直接使用 Axios
- 响应拦截器按状态码分类提示（401 静默刷新、5xx 自动重试）
- 相同消息 2s 内去重弹出

### 样式
- 全局设计令牌：`--ac-primary` / `--ac-text-primary` / `--ac-bg` 等 CSS 变量
- 暗色模式：`[data-theme='dark']` 选择器驱动
- 布局：`AppShell.vue` 提供侧边栏 + 头部 + 内容三段式结构

### 测试
```bash
# 运行单元测试
npm run test

# 监听模式
npm run test:watch

# E2E 测试（需 Playwright）
npm run test:e2e
```

## 部署

构建后将 `dist/` 目录部署至 Nginx，参考 `nginx.conf`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /path/to/dist;
    index index.html;

    location /api/ {
        proxy_pass http://backend:8080;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```
