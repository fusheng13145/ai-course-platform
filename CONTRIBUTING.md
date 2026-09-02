# 贡献指南（CONTRIBUTING）

> 欢迎参与 AI 课程平台（AI Course Platform）开源共建。本文件是外部贡献者的**唯一入口**：
> 从 Issue 到合入的完整流程、分支策略、编码规范与质量门禁。开发环境细节见 `docs/项目手册.md`（第六部分）。

---

## 1. 项目简介与定位

- **定位**：融合知识图谱与大语言模型（RAG）的智能在线教育平台，以《计算机网络》专业课为试点，面向学生 / 教师 / 管理员三端。
- **技术栈**：Spring Boot 3.4.13（JDK 21）+ MyBatis-Plus + MySQL 8 + Redis 7 + Vue 3 / Vite / Element Plus / ECharts。
- **职责边界**：知识图谱引擎（8001）与 RAG 检索引擎（8002）为独立 Python 微服务，由引擎侧维护者负责；Java 端仅实现消费侧与标准化集成层（`com.aicourse.integration`）。
- **开源协议**：MIT（见 `LICENSE`）。若团队后续决策切换 AGPL-3.0，仅需替换 `LICENSE` 文件并在本文件 §6 同步说明。

## 2. 参与方式总览

```
报告问题(ISSUE) → 认领/讨论 → 创建分支 → 提交代码 → PR → CI 门禁 → Code Review → 合入
```

| 环节 | 说明 |
|------|------|
| **报告 Bug / 提需求** | 使用 Issue 模板（`.github/ISSUE_TEMPLATE/`），遵循「可复现 / 可验收」原则 |
| **认领任务** | 在 Issue 评论区声明认领，维护者分配；避免与既有 PR 冲突 |
| **提交代码** | 遵循 §3 分支策略与 §4 编码规范 |
| **发起 PR** | 使用 PR 模板（`.github/pull_request_template.md`），描述改动、测试与影响面 |
| **CI 门禁** | 全部通过后方可合入（见 §5） |
| **Code Review** | 至少 1 位维护者 approve；重大架构改动需 2 位 |

## 3. 分支策略（Git Workflow）

```
main（稳定基线，受保护）
  └── leyon（日常开发集成分支，PR 默认目标）
```

| 规则 | 说明 |
|------|------|
| **默认基线** | 日常改动一律在 `leyon` 上开发并推送；对外协作经 Issue + PR 指向 `leyon` |
| **main 保护** | `main` 为受保护稳定分支；任何推送 / 合并 / 重置操作须经项目负责人明确同意后方可执行 |
| **提交信息** | Conventional Commits：`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `chore:` + 简述；引用 Issue 号 |
| **禁止** | 禁止未经许可直接向 `main` 推送；禁止大段无关改动混入同一 PR（保持 PR 可审查） |

## 4. 编码规范（摘要，详见 `docs/项目手册.md` 第六部分）

### 后端（Java）
- 类 `UpperCamelCase`、方法/变量 `lowerCamelCase`、常量 `UPPER_SNAKE`；类级 Javadoc 说明职责与边界。
- 写操作在 Service 实现类加 `@Transactional(rollbackFor = Exception.class)`。
- Controller 方法必须声明角色约束（`@PreAuthorize`），禁止绕过 RBAC 直连数据。
- **集成契约**：涉及 Python 对接（KG/RAG）的 HTTP 调用必须引用 `ContractPaths` 常量，禁止硬编码路径/字段名；修改契约须同步 `docs/项目手册.md`（第八部分）与 Python 端。
- 禁止 `System.out`；业务异常 `log.warn`、系统异常 `log.error`（`@Slf4j`）。

### 前端（Vue 3）
- 组合式 API（`<script setup>`）；组件 `PascalCase.vue`。
- 样式优先使用设计令牌 CSS 变量（双主题自适应），禁止硬编码色值。
- 所有请求经 `api/` 模块封装，禁止页面内散落 axios 调用。
- 加载态 / 错误态 / 空态三态齐全；禁止 mock 业务数据。

## 5. 质量门禁（不达标不合并）

| 门禁 | 工具 | 阈值 |
|------|------|------|
| 后端单测 + 集成测试 | JUnit 5 / Testcontainers | 全绿（`mvn verify`） |
| 后端覆盖率 | JaCoCo | LINE ≥ 40% / BRANCH ≥ 25%（只许上调） |
| 后端静态分析 | SpotBugs | 0 error |
| 后端架构约束 | ArchUnit | 0 violation |
| 前端 Lint | ESLint | 0 errors |
| 前端单测 | Vitest | 全绿 |
| 前端构建 | Vite build | 成功 |
| 依赖漏洞 | OSV-Scanner / npm audit | 0 已知漏洞（高危） |
| Python 契约 | pytest（kg_service） | 全绿 |

> CI 流水线定义见 `.github/workflows/ci.yml`；本地验证命令见 `docs/项目手册.md` 第六部分。
> **注意**：集成层契约测试（`KnowledgeGraphServiceImplTest` / `RAGServiceImplTest` / `LocalKnowledgeGraphProviderTest` / `DisabledRagProviderTest` / `Http*ContractTest` / `HttpRagProviderBoostTest`）为契约守护，禁止删除或绕过。

## 6. 开源发布注意事项

- **许可证**：当前 MIT。若切换 AGPL-3.0，同时更新 `LICENSE`、README 徽章与本文件。
- **密钥安全**：`application-local.yaml`、`.env` 等含密钥文件已被 `.gitignore` 忽略，严禁提交；生产密钥一律经环境变量注入。
- **演示数据**：README 演示账号与种子数据（seed 20260731）保持一致，勿单独改动。
- **发布节奏**：`main` 打 tag `v*.*.*` 触发 `.github/workflows/release.yml` 自动构建 Release。

## 7. 行为准则

- 尊重维护者与协作者；讨论聚焦技术事实，拒绝人身攻击。
- 不引入未经验证的第三方依赖；新增依赖须在 PR 中说明用途与安全评估。
- 复现类 Bug 报告须附：环境版本、复现步骤、期望/实际结果、日志片段。

---
*维护者：项目技术负责人（详见 `docs/项目手册.md` 第一部分团队信息）。*
