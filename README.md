# AI智能课程平台

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.x-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.4.x-blue)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

AI智能课程平台是一套融合人工智能与教学管理的现代化在线教育系统。系统面向学生、教师与管理员三大核心角色，提供个性化学习体验、高效的教学支持及全面的管理能力。

### 核心功能

- **多角色支持**: 管理员、教师、学生三种角色权限隔离（RBAC）
- **课程管理**: 课程创建、章节管理、选课审批、教学资源管理
- **作业考试**: 题库管理、智能出题、在线考试、自动评分、查重检测
- **学习辅助**: 知识图谱可视化、个性化学习路径、AI智能问答、错题本
- **数据分析**: 学习进度分析、成绩分布、学习趋势、学情报告
- **AI赋能**: DeepSeek大模型集成，支持智能出题、AI对话
- **实时通信**: WebSocket实时通知推送

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.5.15 |
| ORM | MyBatis-Plus | 3.5.7 |
| 安全 | Spring Security + JWT | 6.x / jjwt 0.12.3 |
| 数据库 | MySQL | 8.0+ |
| 缓存 | Redis | 7.x（可选） |
| 前端框架 | Vue 3 | 3.5.x |
| 构建工具 | Vite | 8.x |
| UI库 | Element Plus | 2.4.x |
| 图表 | ECharts + vue-echarts | 5.x / 6.x |
| 状态管理 | Pinia | 2.1.x |
| 路由 | Vue Router | 4.2.x |
| 实时通信 | WebSocket | 集成Spring WebSocket |

## 快速开始

### 环境要求

| 环境 | 版本要求 |
|------|---------|
| JDK | 17+（推荐25） |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Node.js | 18+ |
| Redis | 7.x（可选） |

### 数据库初始化

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE ai_course_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 导入表结构和初始数据
mysql -u root -p ai_course_platform < backend/src/main/resources/index.sql
```

### 环境变量配置

在启动前配置以下环境变量（生产环境必需）：

| 变量名 | 说明 | 默认值（仅开发环境） |
|--------|------|-------------------|
| JWT_SECRET | JWT签名密钥 | 内置测试密钥 |
| MAIL_USERNAME | 邮箱账号 | - |
| MAIL_PASSWORD | 邮箱授权码 | - |
| DEEPSEEK_API_KEY | DeepSeek API密钥（可选） | - |

### 后端启动

```bash
cd backend
# 开发模式（含热部署）
./mvnw.cmd spring-boot:run

# 或打包运行
mvn clean package
java -jar target/ai-course-platform-backend-0.0.1-SNAPSHOT.jar
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

### 访问地址

- 前端: http://localhost:5173
- API: http://localhost:8080/api
- Swagger: http://localhost:8080/api/swagger-ui.html

### Redis 启动说明（可选）

Redis 用于会话缓存和性能优化，非必需启动。若不启动Redis：
- 应用仍可正常启动和运行
- 会话管理将使用数据库存储
- 部分缓存功能（如验证码存储）不会生效

**启动Redis：**
```bash
# Windows
redis-server

# Linux/Mac
redis-server

# Docker
docker run -d -p 6379:6379 redis:7
```

## 演示账号

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

## 项目结构

```
ai-course-platform/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/aicourse/
│   │   ├── Application.java    # 启动类
│   │   ├── controller/         # 22个控制器
│   │   ├── service/            # 50+个服务类
│   │   ├── mapper/             # 30+个Mapper接口
│   │   ├── entity/             # 32个实体类
│   │   ├── security/           # JWT认证过滤器
│   │   ├── config/             # 配置类
│   │   └── util/               # 工具类
│   └── src/main/resources/
│       ├── application.yaml    # 配置文件
│       └── index.sql           # 数据库初始化脚本
│
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── api/                # 22个API模块
│   │   ├── views/              # 35+页面组件
│   │   ├── layouts/            # 布局组件
│   │   ├── stores/             # Pinia状态管理
│   │   ├── router/             # 路由配置
│   │   └── utils/              # 工具函数
│   ├── vite.config.js          # Vite配置
│   └── package.json            # 依赖配置
│
├── docs/                       # 项目文档
│   ├── 项目需求文档.md         # 需求规格说明书
│   ├── 项目交付文档.md         # 项目交付文档
│   └── 项目完成度分析报告.md   # 完成度分析报告
├── CLAUDE.md                   # 编码规范
└── README.md                   # 项目说明
```

## 功能模块清单

### 已实现功能

| 模块 | 功能 | 状态 |
|------|------|------|
| 用户认证 | 登录/注册/退出/密码修改/密码重置 | ✅ 已完成 |
| 用户管理 | 用户CRUD、启用/禁用、搜索筛选 | ✅ 已完成 |
| 课程管理 | 课程CRUD、审批、状态切换 | ✅ 已完成 |
| 章节管理 | 章节CRUD、多级章节树 | ✅ 已完成 |
| 选课管理 | 选课/退课、审批流 | ✅ 已完成 |
| 题库管理 | 题目CRUD、分类筛选、AI生成 | ✅ 已完成 |
| 试卷管理 | 试卷CRUD、题目关联 | ✅ 已完成 |
| 在线考试 | 开始考试、提交答卷、查看结果 | ✅ 已完成 |
| 作业管理 | 任务CRUD、发布、提交统计 | ✅ 已完成 |
| 作业批改 | 提交评分、教师反馈 | ✅ 已完成 |
| 知识图谱 | 知识点CRUD、关系管理、图谱可视化 | ✅ 已完成 |
| 学习路径 | 路径CRUD、节点管理、自动生成 | ✅ 已完成 |
| 学习记录 | 学习记录创建与查询 | ✅ 已完成 |
| 错题本 | 错题CRUD、标记已掌握、统计 | ✅ 已完成 |
| 资源管理 | 文件上传/下载、版本管理 | ✅ 已完成 |
| 通知中心 | 通知列表、已读/未读、发送通知 | ✅ 已完成 |
| 权限管理 | 角色权限CRUD、启用/禁用 | ✅ 已完成 |
| 数据分析 | 仪表盘统计、学习趋势、课程分析 | ✅ 已完成 |
| 系统管理 | 配置管理、健康检查、日志查看 | ✅ 已完成 |
| AI助手 | DeepSeek对话、智能出题、内容生成 | ✅ 已完成 |
| WebSocket | 实时通知推送 | ✅ 已完成 |

### 可扩展功能

| 功能 | 当前状态 | 扩展方向 |
|------|---------|---------|
| 作业查重 | 表结构已建 | 接入真实查重服务 |
| 催交提醒 | 表结构已建 | 实现定时任务自动催交 |
| 视频学习 | 表结构已建 | 实现视频播放进度追踪 |
| AI学情报告 | API接口已预留 | 接入AI分析服务 |
| Redis缓存 | 配置已就绪 | 启动Redis启用缓存加速 |

## 开发规范

参见 [CLAUDE.md](CLAUDE.md) - 编码规范文档

## 详细文档

参见 [docs/项目交付文档.md](docs/项目交付文档.md) - 包含完整的功能说明、API文档、部署指南

## 许可证

MIT License