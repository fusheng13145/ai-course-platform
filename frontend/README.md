# AI 课程平台 - 前端

## 技术栈

- **Vue 3.4** / Composition API + `<script setup>`
- **Vite 5** / Element Plus / Pinia / Axios / ECharts
- **Vitest** + jsdom 单元测试
- **三套布局**：StudentLayout / TeacherLayout / AdminLayout

## 快速启动

### 前提

- Node.js ≥ 18（项目使用 22.22.2）
- 后端已启动（`http://localhost:8080/api`）

### 安装与运行

```bash
cd frontend
npm install
npm run dev     # → http://localhost:5173
```

Vite 开发服务器自动代理 `/api` 至后端（见 `vite.config.js` 的 proxy 配置）。

## 目录结构

```
frontend/src/
├── api/              # Axios 实例 + 33 个 API 模块（auth/course/exam/ai/...）
│   ├── request.js    # 核心拦截器：全局loading、消息去重、自动重试、401刷新
│   └── __tests__/
├── stores/           # Pinia 状态管理（auth + 模块内联）
├── router/           # Vue Router 4（~60 条路由，全部懒加载）
├── views/
│   ├── student/      # 学生端视图（Dashboard/Courses/LearningCenter/AIAssistant/Exams/...）
│   │   └── __tests__/
│   ├── teacher/      # 教师端视图（课程管理/作业批阅/学情分析/...）
│   ├── admin/        # 管理员视图（用户管理/系统监控/...）
│   └── auth/         # 登录/注册
├── components/       # 公用组件（ThemeSwitcher/PageHeader/...）
├── composables/      # 组合式函数（useTheme/...）
├── utils/            # 工具函数（logger/websocket/response）
└── layouts/          # 布局组件（学生/教师/管理员三套）
```

## 关键约定

- **认证**：JWT 以 HttpOnly Cookie 下发，前端不可读令牌；`withCredentials: true` 自动携带
- **路由守卫**：`router.beforeEach` 校验 `meta.requiresAuth` + `meta.role`，未登录跳 `/login?redirect`
- **权限控制**：`v-if="hasPermission('xxx')"` 或 `v-if="isRole('ADMIN')"`
- **错误处理**：axios 响应拦截器按 10+ 状态码分类中文提示，自动重试，401 静默刷新
- **API 调用**：所有 API 归于 `src/api/` 模块，View 不直接发请求
- **UI 组件**：全量 Element Plus（按需引入），禁止混用其他 UI 库
- **样式**：优先 Element Plus 内置主题变量，`@/assets/styles` 仅放全局覆盖

## 测试

```bash
npm test               # vitest run（~120 用例）
npm run test:watch     # 监听模式
```

测试文件放在对应模块的 `__tests__/` 目录下，适用于 stores / api / views / composables / components。

## 构建部署

```bash
npm run build          # → dist/
```

生产环境由 Nginx 反向代理，`/api` 转发至后端 8080。Docker Compose 编排见项目根 `docker-compose.yml`。
