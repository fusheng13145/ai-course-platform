# AI 课程平台 - 后端服务

## 技术栈

- **Java 21** / Spring Boot 3.4.4 / Spring Security 6
- **MyBatis-Plus 3.5** / MySQL 8.0 / Redis 7
- **JWT** 认证（HttpOnly Cookie + 双提交 CSRF）
- **DeepSeek API** / OpenAI / Qwen 多 LLM 热切换
- **JaCoCo** 测试覆盖率门禁 + ArchUnit 架构约束

## 快速启动

### 前提

- JDK 21（`C:\Program Files\Java\jdk-21`）
- Docker Desktop（MySQL 8.0 + Redis 7）
- Maven（项目根 `mvnw.cmd` 或使用 `~/.m2/wrapper/dists/` 缓存）

### 1. 启动基础设施

```bash
docker compose up -d mysql redis
```

### 2. 初始化数据库

```bash
# 连入 MySQL 执行统一初始化脚本（47张表 + 种子数据）
mysql -u root -p -e "SET NAMES utf8mb4; SOURCE src/main/resources/index.sql"
```

### 3. 配置环境变量

复制并编辑 `.env`（勿提交 git）：

```env
MYSQL_ROOT_PASSWORD=YOUR_PASSWORD
JWT_SECRET=YOUR_64_CHAR_SECRET
DEEPSEEK_API_KEY=sk-xxxx   # 可选，不填则 AI 回退为知识库原文
MAIL_USERNAME=             # 可选
MAIL_PASSWORD=             # 可选
```

### 4. 启动

```bash
# 使用项目根 start-backend.bat（自动检测 JDK21 + 设置 profile）
cd .. && start-backend.bat

# 或手动
set JAVA_HOME=C:\Program Files\Java\jdk-21
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

服务启动后访问 `http://localhost:8080/api`。默认测试账号：`admin` / `123456`。

## 目录结构

```
backend/src/main/java/com/aicourse/
├── controller/     # 40 个 REST 控制器（统一 ApiResponse 信封）
├── service/        # 业务接口（54 个）
│   └── impl/       # 业务实现（50 个，平均 205 行/类）
├── mapper/         # MyBatis-Plus Mapper（47 张表）
├── entity/         # 实体类（47 个）
├── dto/            # 数据传输对象（50+ 个，VO/Req 分离）
├── config/         # Spring 配置（安全、异步、缓存、限流）
├── security/       # JWT 认证 + CSRF + 配额熔断
├── llm/            # LLM 抽象层（DeepSeek/OpenAI/Qwen 热切换）
├── common/         # ApiResponse / 分页 / 工具类
└── aspect/         # 操作日志 AOP
```

## 关键约定

- **返回信封**：统一 `ApiResponse<T>`（code/message/data），`ResourceController` 除外
- **异常处理**：`GlobalExceptionHandler` 覆盖 9 类异常，Controller 禁止裸 try-catch
- **认证**：JWT 存储于 HttpOnly Cookie，`authentication.principal` 为 Long userId
- **权限**：`@PreAuthorize("hasRole('ADMIN')")` + 对象级 `#userId == authentication.principal`
- **事务**：写操作统一 `@Transactional(rollbackFor = Exception.class)`
- **日志**：`@Slf4j`（禁止 System.out）
- **API 文档**：`/v3/api-docs`（OpenAPI JSON），Swagger UI 见 `static/api-docs.html`

## 测试

```bash
# 单元测试 + 切片测试（1186+ 用例）
mvnw test

# 覆盖率报告（JaCoCo 门禁：行 ≥40%，分支 ≥30%）
mvnw verify
```

## 配置层次

| 文件 | 用途 |
|------|------|
| `application.yaml` | 生产默认（密钥全部 `${ENV}` 注入） |
| `application-local.yaml` | 开发覆盖（仅 local profile 激活） |

## 架构约束

- Controller 不得直接注入 Mapper
- LLM 调用必须通过 `LLMProvider` 接口，不得直接依赖具体供应商
- 知识图谱当前为关系表实现（Neo4j/Milvus 为规划能力）
- RAG 为 Java 自包含实现（TF-IDF 余弦 + DeepSeek，不调用 Python 微服务）
