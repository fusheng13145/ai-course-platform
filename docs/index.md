---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "AI 课程平台"
  text: "基于知识图谱 + LLM 的 AI 助教系统"
  tagline: 江苏科技大学 大学生创新创业训练计划项目 · 项目技术文档站
  actions:
    - theme: brand
      text: 项目资料综合手册（最终版）
      link: /项目资料综合手册-最终版
    - theme: alt
      text: 架构手册
      link: /架构手册
    - theme: alt
      text: 运维手册
      link: /运维手册

features:
  - title: 知识图谱驱动
    details: 知识点关系图谱 + 学习路径规划（本地 BFS 兜底），支持图谱可视化与 AI 抽取
  - title: LLM 深度集成
    details: DeepSeek 多 API 适配层、AI 助教多轮对话、AI 出题/批阅/润色、学情诊断建议
  - title: 工程化完备
    details: 3000 后端用例 + 283 前端用例 + 32 e2e 全绿；JaCoCo LINE 94.57%；Flyway 迁移；CI 7 jobs
---
