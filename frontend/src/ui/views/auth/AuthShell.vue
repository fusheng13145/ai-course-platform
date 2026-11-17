<template>
  <Sprite />
  <div class="auth">
    <header class="av-top">
      <a class="av-brand" href="/">
        <span class="brand-mark">枢</span>
        <span class="av-brand-text">
          <b>云枢 AI 助教</b>
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
        <section class="av-panel">
          <slot name="form" />
        </section>
      </section>
    </main>

    <footer class="av-foot">
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

const { theme, toggleTheme } = useUi()
useRoot(theme)
</script>
