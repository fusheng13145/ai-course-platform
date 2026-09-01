import { defineConfig } from 'vitepress'

/**
 * AI 课程平台 · 文档站配置
 * sourceDir 默认即本目录（docs/），5 份权威 md 直接作为页面。
 */
export default defineConfig({
  title: 'AI 课程平台',
  description: '基于知识图谱 + LLM 的 AI 助教系统 · 项目文档',
  lang: 'zh-CN',
  lastUpdated: true,
  cleanUrls: false,
  // 文档含本地开发环境示例链接（localhost:5173/8080），构建时跳过死链校验
  ignoreDeadLinks: true,

  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '综合手册', link: '/项目资料综合手册-最终版' },
      { text: '架构手册', link: '/架构手册' },
      { text: '开发指南', link: '/开发指南' },
      { text: '运维手册', link: '/运维手册' },
      { text: '集成契约', link: '/集成契约-知识图谱与RAG' },
    ],

    sidebar: [
      {
        text: '项目总览',
        items: [{ text: '项目资料综合手册（最终版）', link: '/项目资料综合手册-最终版' }],
      },
      {
        text: '技术手册',
        items: [
          { text: '架构手册', link: '/架构手册' },
          { text: '开发指南', link: '/开发指南' },
          { text: '运维手册', link: '/运维手册' },
          { text: '集成契约 · 知识图谱与 RAG', link: '/集成契约-知识图谱与RAG' },
        ],
      },
    ],

    footer: {
      message: '江苏科技大学 大学生创新创业训练计划项目',
      copyright: '基于知识图谱 + LLM 的 AI 助教系统',
    },

    docFooter: { prev: '上一页', next: '下一页' },
    outline: { label: '本页目录', level: [2, 3] },
    lastUpdated: { text: '最后更新', formatOptions: { dateStyle: 'short', timeStyle: 'short' } },

    search: {
      provider: 'local',
      options: {
        translations: {
          button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
          modal: {
            noResultsText: '未找到相关结果',
            resetButtonTitle: '清除查询条件',
            footer: { selectText: '选择', navigateText: '切换', closeText: '关闭' },
          },
        },
      },
    },

    darkModeSwitchLabel: '主题',
    returnToTopLabel: '返回顶部',
    sidebarMenuLabel: '菜单',
    outlineTitle: '本页目录',
  },
})
