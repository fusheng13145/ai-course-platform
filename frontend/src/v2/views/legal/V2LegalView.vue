<template>
  <V2Sprite />
  <div class="legal-v2">
    <header class="lv-top">
      <a class="lv-brand" href="/" aria-label="云枢教学平台">
        <span class="brand-mark">枢</span>
        <span class="lv-brand-text">
          <b>云枢教学平台</b>
          <small>YUNSHU / LEGAL</small>
        </span>
      </a>
      <div class="lv-actions">
        <button
          class="icon-btn"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggleTheme"
        >
          <V2Icon :name="theme === 'dark' ? 'sun' : 'moon'" />
        </button>
        <router-link class="btn btn-sm btn-ghost" to="/register">返回注册</router-link>
      </div>
    </header>

    <main class="lv-main">
      <article class="lv-doc">
        <header class="lv-head">
          <p class="eyebrow">YUNSHU / LEGAL / 2026</p>
          <h1>{{ doc.title }}</h1>
          <p class="lv-sub">{{ doc.subtitle }}</p>
        </header>
        <!-- eslint-disable-next-line vue/no-v-html -- 法务静态文案，走 utils/sanitize 的法务档位（无内联 style） -->
        <div class="lv-body" v-html="safeHtml"></div>
        <footer class="lv-foot">
          <span>云枢教学平台 · v2</span>
          <span>
            <router-link :to="otherPath">{{ otherLabel }}</router-link>
            <span class="lv-sep">·</span>
            <a href="/login">返回登录</a>
          </span>
        </footer>
      </article>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import '../../styles/record.css'
import '../../styles/shell-fix.css'
import '../../styles/typography.css'
import V2Sprite from '../../components/V2Sprite.vue'
import V2Icon from '../../components/V2Icon.vue'
import { sanitizeLegalHtml } from '@/utils/sanitize'
import { useV2Ui } from '../../composables/ui'
import { useV2Root } from '../../composables/useV2Root'
import legal from '../../data/legal.json'

const props = defineProps<{ kind: 'agreement' | 'privacy' }>()
const { theme, toggleTheme } = useV2Ui()
useV2Root(theme)

const doc = computed(() => legal[props.kind])
const otherPath = computed(() => (props.kind === 'agreement' ? '/privacy' : '/agreement'))
const otherLabel = computed(() => (props.kind === 'agreement' ? '隐私政策' : '用户服务协议'))

/** 档位本体在 utils/sanitize 的 LEGAL 档：与原本地配置同集（section/h2/p/strong/ul/li/br/span/em ＋ class） */
const safeHtml = computed(() => sanitizeLegalHtml(doc.value.html))
</script>

<style scoped>
.legal-v2 {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 22px clamp(18px, 4vw, 56px) 26px;
  background: var(--bg);
  color: var(--ink);
}
.lv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.lv-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-decoration: none;
}
.lv-brand .brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: var(--r-sm);
  background: var(--brand-mark-bg);
  color: #fff;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
}
.lv-brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.lv-brand-text b {
  font-size: 14px;
  letter-spacing: 0.03em;
}
.lv-brand-text small {
  margin-top: 3px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
  color: var(--ink-3);
}
.lv-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lv-main {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 18px 0;
}
.lv-doc {
  width: min(860px, 100%);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  box-shadow: var(--sh-card);
  padding: clamp(24px, 4vw, 42px);
}
.lv-head {
  padding-bottom: 18px;
  border-bottom: 1px solid var(--line);
}
.lv-head .eyebrow {
  margin: 0 0 9px;
}
.lv-head h1 {
  margin: 0;
  font-family: 'Noto Serif SC', Georgia, 'Songti SC', serif;
  font-size: clamp(28px, 3vw, 36px);
  font-weight: 600;
  letter-spacing: -0.01em;
}
.lv-sub {
  margin: 8px 0 0;
  font-size: 11.5px;
  color: var(--ink-3);
}
.lv-body :deep(section) {
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}
.lv-body :deep(section:last-child) {
  border-bottom: 0;
}
.lv-body :deep(h2) {
  margin: 0 0 9px;
  font-family: 'Noto Serif SC', Georgia, 'Songti SC', serif;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink);
}
.lv-body :deep(p) {
  margin: 0 0 9px;
  font-size: 13px;
  line-height: 1.95;
  color: var(--ink-2);
}
.lv-body :deep(p:last-child) {
  margin-bottom: 0;
}
.lv-body :deep(strong) {
  font-weight: 600;
  color: var(--ink);
}
.lv-body :deep(ul) {
  margin: 0 0 9px;
  padding-left: 20px;
}
.lv-body :deep(li) {
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-2);
}
.lv-body :deep(.contact-section) {
  margin-top: 14px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface-2);
}
.lv-foot {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 22px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 8.5px;
  color: var(--ink-3);
}
.lv-foot a {
  color: var(--ink-2);
  text-decoration: none;
}
.lv-foot a:hover {
  color: var(--accent);
}
.lv-sep {
  margin: 0 7px;
  color: var(--ink-4);
}
@media (max-width: 520px) {
  .legal-v2 {
    padding: 16px 14px 20px;
  }
  .lv-top {
    flex-wrap: wrap;
  }
  .lv-doc {
    padding: 20px 18px;
  }
}
</style>
