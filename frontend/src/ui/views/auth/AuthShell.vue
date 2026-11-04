<template>
  <Sprite />
  <div class="auth">
    <header class="av-top">
      <a class="av-brand" href="/">
        <span class="brand-mark">枢</span>
        <span class="av-brand-text">
          <b>云枢 AI 助教</b>
          <small>YUNSHU / AI TUTOR</small>
        </span>
      </a>
      <div class="av-actions">
        <button
          class="icon-btn"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          :aria-pressed="theme === 'dark'"
          @click="toggleTheme"
        >
          <Icon :name="theme === 'dark' ? 'sun' : 'moon'" />
        </button>
      </div>
    </header>

    <main class="av-main">
      <section class="av-card">
        <aside class="av-story">
          <p class="eyebrow">YUNSHU / KNOWLEDGE GRAPH + LLM / 2026</p>
          <h1>让每一次提问，<br /><em>都有据可依的答案。</em></h1>
          <p class="lede">
            课程知识图谱与大语言模型双引擎，把答疑、出题、批改与学情诊断连成一条线。AI
            只给建议，教师保留最终确认权。
          </p>
          <div class="av-record" aria-hidden="true">
            <div class="av-record-head">
              <span>AI 助教 / 计算机网络 · 第 5 章</span>
              <span class="chip chip-warn">演示对话</span>
            </div>
            <div class="av-record-row"><span>学生提问 · 慢启动阈值怎么变？</span><b>Q1</b></div>
            <div class="av-record-row"><span>图谱依据 · 拥塞控制 / 快重传</span><b>KG</b></div>
            <div class="av-record-row"><span>学习建议 · 补 5.3 节 + 3 道同类题</span><b>AI</b></div>
            <div class="av-record-bar"><i style="width: 68%"></i></div>
            <div class="av-record-foot">
              <span>回答附检索依据</span>
              <span>AI 建议 · 教师可审核</span>
            </div>
          </div>
          <ul class="av-points">
            <li>课程知识图谱</li>
            <li>智能答疑</li>
            <li>AI 预批建议</li>
            <li>学情诊断</li>
          </ul>
        </aside>
        <section class="av-panel">
          <slot name="form" />
        </section>
      </section>
    </main>

    <footer class="av-foot">
      <span>云枢 AI 助教 · 知识图谱 + LLM 驱动</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import '../../styles/record.css'
import '../../styles/shell-fix.css'
import '../../styles/typography.css'
import '../../styles/auth.css'
import Sprite from '../../components/Sprite.vue'
import Icon from '../../components/Icon.vue'
import { useUi } from '../../composables/ui'
import { useRoot } from '../../composables/useRoot'

const { theme, toggleTheme } = useUi()
useRoot(theme)
</script>
