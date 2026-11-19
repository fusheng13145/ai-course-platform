<template>
  <Sprite />
  <div class="auth">
    <header class="av-top">
      <router-link class="av-brand" to="/">
        <span class="brand-mark">枢</span>
        <span class="av-brand-text">
          <b>云枢 AI 助教</b>
          <small>教学空间</small>
        </span>
      </router-link>
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

    <main class="av-main" :class="{ 'av-main-wide': wide }">
      <aside class="av-context" aria-hidden="true">
        <div class="av-context-meta">
          <span>YUNSHU / AI TEACHING</span>
          <span>2026.11</span>
        </div>
        <div class="av-context-mark">
          <span class="av-context-glyph">枢</span>
          <span class="av-context-caption">COURSE · GRAPH · PATH</span>
        </div>
        <ol class="av-context-index">
          <li><b>01</b><span>课程空间</span></li>
          <li><b>02</b><span>知识图谱</span></li>
          <li><b>03</b><span>学习路径</span></li>
        </ol>
      </aside>

      <section class="av-card" :class="{ 'av-card-wide': wide }">
        <section class="av-panel">
          <slot name="form" />
        </section>
      </section>
    </main>

    <footer class="av-foot">
      <span>云枢 · 教学空间</span>
      <span class="av-foot-sep">/</span>
      <router-link to="/agreement">用户协议</router-link>
      <span>·</span>
      <router-link to="/privacy">隐私政策</router-link>
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

withDefaults(defineProps<{ wide?: boolean }>(), { wide: false })

const { theme, toggleTheme } = useUi()
useRoot(theme)
</script>
