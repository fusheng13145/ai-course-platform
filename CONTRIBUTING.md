# 贡献指南

感谢您对 AI 智能课程平台的关注！我们欢迎所有形式的贡献，包括报告 Bug、提交新功能、改进文档等。

## 目录

- [行为准则](#行为准则)
- [如何报告 Bug](#如何报告-bug)
- [如何提交功能请求](#如何提交功能请求)
- [开发环境搭建](#开发环境搭建)
- [代码风格指南](#代码风格指南)
- [提交 PR 流程](#提交-pr-流程)
- [测试要求](#测试要求)
- [Commit 规范](#commit-规范)

## 行为准则

本项目遵循开源社区行为准则。请保持尊重、包容和协作的态度。不允许任何形式的骚扰、歧视或不专业行为。

## 如何报告 Bug

1. **搜索现有 Issues** — 在提交新 Issue 前，请先搜索是否已有相同或类似的问题
2. **��用 Bug 报告模板** — 创建 Issue 时选择 Bug Report 模板，包含：
   - **标题**：简洁描述问题
   - **描述**：清晰描述期望行为与实际行为
   - **复现步骤**：详细列出复现该问题的步骤
   - **环境信息**：操作系统、浏览器、JDK 版本、Node.js 版本等
   - **截图/日志**：如有相关截图或错误日志，请附上
   - **严重程度**：标注是阻塞性/严重/一般/轻微

## 如何提交功能请求

1. 先在 Issues 中搜索是否有类似建议
2. 使用 Feature Request 模板，包含：
   - **功能描述**：清晰说明你希望增加的功能
   - **使用场景**：该功能解决什么实际问题
   - **实现思路**（可选）：你对该功能实现的初步想法

## 开发环境搭建

### 环境要求

| 环境 | 版本要求 |
|------|---------|
| JDK | **21**（Temurin 21 推荐） |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Node.js | 20.19+（Vite 8 要求） |
| Redis | 7.x（可选，本地开发可跳过） |

### 后端

```bash
# 1. 克隆仓库
git clone https://github.com/example/ai-course-platform.git
cd ai-course-platform

# 2. 创建数据库
mysql -u root -p -e "CREATE DATABASE ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. 导入数据表
mysql -u root -p ai_course_platform < backend/src/main/resources/index.sql

# 4. 配置环境变量（可选，默认使用本地开发配置）
# 参考 README.md 中的环境变量说明

# 5. 启动后端
cd backend
./mvnw.cmd spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

### 访问地址

- 前端: http://localhost:5173
- API: http://localhost:8080/api
- Swagger: http://localhost:8080/api/swagger-ui.html

## 代码风格指南

### 后端（Java）

- **语言级别**：Java 21，使用 `var` 在局部变量类型明显时优先使用
- **命名规范**：
  - 类名：PascalCase（如 `AuthController`）
  - 方法名：camelCase（如 `getUserById`）
  - 常量：UPPER_SNAKE_CASE（如 `MAX_FILE_SIZE`）
  - 包名：全小写（如 `com.aicourse.controller`）
- **代码组织**：
  - Controller 层：处理请求/响应，调用 Service 层
  - Service 层：业务逻辑，事务管理
  - Mapper 层：数据库访问
  - Entity 层：数据实体
- **使用 Lombok**：`@Data`、`@Slf4j`、构造器注入
- **使用构造器注入**：不使用 `@Autowired` 字段注入
- **RESTful 风格**：遵循标准 HTTP 方法和资源命名
- **统一响应**：所有接口返回 `ApiResponse<T>` 信封
- **参数校验**：使用 `jakarta.validation` 注解
- **异常处理**：使用 `BusinessException` 抛出业务异常
- **Swagger 注解**：Controller 类添加 `@Tag`，关键方法添加 `@Operation`

### 前端（JavaScript/Vue）

- **命名规范**：
  - 组件名：PascalCase（如 `LoginForm.vue`）
  - 变量/函数：camelCase
  - 常量：UPPER_SNAKE_CASE
  - Pinia Store：以 `use` 开头、以 `Store` 结尾（如 `useAuthStore`）
- **Vue 3**：使用 Composition API (`<script setup>`)
- **状态管理**：使用 Pinia
- **类型检查**：尽可能使用 JSDoc 注释
- **格式化**：使用 Prettier（运行 `npm run format`）
- **Lint**：使用 ESLint（运行 `npm run lint`）
- **避免**：不使用 `any` 类型，不在模板中写复杂逻辑

## 提交 PR 流程

1. **Fork 仓库** — 在 GitHub 上 Fork 本项目
2. **创建分支** — 从 `main` 分支创建新分支：
   ```bash
   git checkout -b feat/my-feature
   # 或
   git checkout -b fix/my-bug-fix
   ```
3. **开发** — 实现你的更改
4. **编写测试** — 为新功能或修复添加测试
5. **运行测试** — 确保所有测试通过（见下方测试要求）
6. **提交代码** — 遵循 Commit 规范
7. **Push 到远程**：
   ```bash
   git push origin feat/my-feature
   ```
8. **创建 Pull Request** — 提交 PR 到 `main` 分支，PR 模板包含：
   - **变更描述**：清晰说明改了什么、为什么改
   - **关联 Issue**：如有关联 Issue 请注明
   - **测试说明**：如何验证你的更改
   - **截图**：如有 UI 变更，请附截图对比

### PR 审查标准

- 所有自动化检查（CI、测试、Lint）必须通过
- 至少 1 位维护者 Review 通过
- 代码覆盖率不降低
- 无安全漏洞引入

## 测试要求

### 后端测试（JUnit 5）

- **测试框架**：JUnit 5 + Mockito
- **测试类型**：
  - **单元测试**：Service 层核心逻辑
  - **集成测试**：Controller 层 + Repository 层（使用 Testcontainers）
- **覆盖率要求**：
  - 行覆盖率 >= 30%
  - 分支覆盖率 >= 20%
- **运行测试**：
  ```bash
  cd backend
  mvn test              # 运行单元测试
  mvn verify            # 运行单元测试 + 集成测试 + 覆盖率检查
  mvn verify -P security-scan  # 额外运行依赖漏洞扫描
  ```
- **测试命名**：`{TestedClass}Test.java` 或 `{TestedClass}IT.java`
- **目录结构**：测试类放在与被测试类相同的包路径下

### 前端测试（Vitest）

- **测试框架**：Vitest（运行 `npx vitest`）
- **测试类型**：
  - **组件测试**：Vue 组件渲染与交互
  - **Store 测试**：Pinia Store 逻辑
  - **Utils 测试**：工具函数
- **运行测试**：
  ```bash
  cd frontend
  npx vitest            # 运行所有测试
  npx vitest --run      # 运行一次（非 watch 模式）
  ```

### CI/CD

- PR 提交时自动触发 CI
- CI 包含：Lint 检查 → 单元测试 → 集成测试 → 构建验证
- 所有步骤通过后方可合并

## Commit 规范

本项目使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档变更 |
| `style` | 代码格式（不影响功能） |
| `refactor` | 重构 |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `chore` | 构建/工具链变更 |
| `ci` | CI 配置变更 |
| `security` | 安全修复 |

### 示例

```
feat(auth): add refresh token rotation

Implement refresh token rotation to prevent token reuse attacks.
Old refresh tokens are invalidated after each refresh.

Closes #123
```

```
fix(course): fix approval status not updating after rejection

The approval status field was not being updated when a course
was rejected by admin.

Fixes #456
```
