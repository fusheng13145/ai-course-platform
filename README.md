# AI智能课程平台

> 一个基于 Spring Boot + MyBatis-Plus + Vue 3 的智能在线教育平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-green)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5.x-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.4.x-blue)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

## 项目概述

AI智能课程平台是一套融合人工智能与教学管理的现代化在线教育系统。系统面向学生、教师与管理员三大核心角色，提供个性化学习体验、高效的教学支持及全面的管理能力。

### 核心功能

- **多角色支持**: 管理员、教师、学生三种角色权限隔离
- **课程管理**: 课程创建、章节管理、选课审批、教学资源管理
- **作业考试**: 题库管理、智能出题、在线考试、自动评分、查重检测
- **学习辅助**: 知识图谱可视化、个性化学习路径、AI智能问答、错题本
- **数据分析**: 学习进度分析、成绩分布、学习趋势、学情报告
- **AI赋能**: DeepSeek大模型集成，支持智能出题、AI对话

## 快速开始

### 后端

```bash
cd backend
# 修改 application.yaml 中的数据库配置
mvn clean compile
mvn spring-boot:run
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

## 演示账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | 123456 |
| 教师 | teacher | 123456 |
| 学生 | student | 123456 |

## 详细文档

参见 [docs/项目交付文档.md](docs/项目交付文档.md) - 包含完整的功能说明、API文档、部署指南。
