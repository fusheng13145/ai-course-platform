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
            知识图谱与 LLM 双引擎，答疑、出题、批改、学情一条线；AI 只给建议，教师保留最终确认权。
          </p>
          <ul class="av-feat" aria-hidden="true">
            <li><Icon name="graph" /><b>课程知识图谱</b><small>答疑与出题有据可依</small></li>
            <li><Icon name="message" /><b>智能答疑</b><small>回答附引用来源</small></li>
            <li><Icon name="check" /><b>AI 预批建议</b><small>教师保留最终确认权</small></li>
            <li><Icon name="chart" /><b>学情诊断</b><small>逐周跟踪与预警</small></li>
          </ul>
          <p class="av-motto" aria-hidden="true">AI SUGGESTS / TEACHER DECIDES</p>
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
