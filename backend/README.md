# AI 助教系统 — 后端

> 基于 Spring Boot 3 + MyBatis-Plus + MySQL/Redis 的在线教育平台后端服务

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.4.4 |
| 语言 | Java | 21 |
| ORM | MyBatis-Plus | 3.5.7 |
| 安全 | Spring Security + JWT (jjwt) | 0.12.6 |
| 数据库 | MySQL | 8.0+ |
| 缓存 | Redis | 7.x |
| API 文档 | Springdoc OpenAPI | 2.8.17 |
| 测试 | JUnit 5 + Mockito + JaCoCo | — |
| 构建 | Maven | 3.8+ |

## 目录结构

```
backend/
├── src/main/java/com/aicourse/
│   ├── controller/         # 40 个 RESTful 控制器
│   ├── service/             # 服务接口 + 55 个实现类
│   ├── mapper/              # MyBatis-Plus Mapper 接口
│   ├── entity/              # 47 个数据实体
│   ├── dto/                 # 71 个数据传输对象
│   ├── config/              # Spring Security / CORS / 异步等配置
│   ├── llm/                 # LLM 抽象层（DeepSeek / OpenAI / Qwen 热切换）
│   ├── common/              # ApiResponse / 异常 / 分页 公共类
│   ├── exception/           # BusinessException / 全局异常处理
│   ├── util/                # JWT / IP / 工具类
│   ├── aspect/              # 操作日志 AOP
│   └── websocket/           # WebSocket 实时通信
├── src/main/resources/
│   ├── application.yaml     # 主配置（生产安全默认）
│   ├── application-local.yaml # 本地开发配置
│   └── index.sql            # 数据库初始化脚本（47 表 + 种子数据）
├── pom.xml
└── README.md
```

## 快速启动

### 1. 环境要求
- JDK 21（**必须** — Lombok 不兼容 JDK 25）
- MySQL 8.0+
- Redis 7.x（可选，部分功能降级）
- Maven 3.8+（或项目自带 mvnw）

### 2. 初始化数据库
```bash
mysql -u root -p < src/main/resources/index.sql
```
> 脚本内含 `SET NAMES utf8mb4;` 解决中文写入乱码问题；包含 47 张表 + 演示数据。

### 3. 配置环境变量
复制 `.env.example` 为 `.env`（或直接注入环境变量）：
```env
JWT_SECRET=<64+字符随机密钥>
MYSQL_ROOT_PASSWORD=<数据库密码>
DEEPSEEK_API_KEY=<DeepSeek API Key（可选，AI 功能需配置）>
```

### 4. 启动
```bash
# 使用 Maven Wrapper（推荐）
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 或打包后运行
./mvnw package -DskipTests
java -jar target/ai-course-platform-backend-*.jar --spring.profiles.active=local
```

服务启动后访问 `http://localhost:8080/api/actuator/health` 确认运行状态。

### 5. 演示账号

> 以下账号由 `backend/src/main/resources/index.sql` 初始化，密码统一为 `123456`（BCrypt 加密）。
> 与项目根目录 `README.md` 的「演示账号」章节保持一致；生产环境必须更换。

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 管理员 | admin | 123456 | 系统管理员，拥有全部权限 |
| 教师 | teacher | 123456 | 张教授，可管理课程、作业、考试 |
| 教师 | teacher2 | 123456 | 李副教授，数学学院教师 |
| 学生 | student | 123456 | 王同学，已选3门课 |
| 学生 | student2 | 123456 | 赵同学 |
| 学生 | student3 | 123456 | 刘同学 |
| 学生 | student4 | 123456 | 陈同学 |
| 学生 | student5 | 123456 | 杨同学 |

> ⚠️ 演示密码仅限本地开发，**生产环境必须更换**。

## 关键约定

- **返回格式**：所有 Controller 统一返回 `ApiResponse<T>` 信封（`{code, message, data}`）
- **异常处理**：`GlobalExceptionHandler` 捕获 9 种异常类型，业务异常抛出 `BusinessException`
- **认证**：JWT 存放于 HttpOnly Cookie，Refresh Token 机制支持自动续期
- **权限**：三端基于 Spring Security 的 RBAC，配合 `@PreAuthorize` 方法级鉴权
- **事务**：写方法使用 `@Transactional(rollbackFor = Exception.class)`，查询方法使用 `@Transactional(readOnly = true)`
- **日志**：统一 SLF4J（Lombok @Slf4j），禁止 `System.out.println`

## 配置层次

| 配置源 | 说明 |
|--------|------|
| `application.yaml` | 默认安全配置，密钥使用 `${ENV}` 占位符 |
| `application-local.yaml` | 本地开发配置，硬编码测试密钥（禁止用于生产） |
| `.env` | 环境变量文件（已加入 .gitignore，**禁止提交**） |

## API 文档

启动后端后访问：
- OpenAPI JSON：`http://localhost:8080/api/v3/api-docs`
- Swagger UI（需配置）：`http://localhost:8080/api/swagger-ui/index.html`

## 测试

```bash
# 运行全部测试
./mvnw test

# 跳过测试打包
./mvnw package -DskipTests

# 查看覆盖率报告
open target/site/jacoco/index.html
```

项目包含 83 个测试文件 / ~1300 测试方法，JaCoCo 门禁：行覆盖率 ≥65%、分支覆盖率 ≥50%。
