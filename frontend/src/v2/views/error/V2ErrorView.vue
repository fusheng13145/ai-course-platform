<template>
  <V2Sprite />
  <div class="err-v2">
    <header class="ev-top">
      <a class="ev-brand" href="/v2" aria-label="云枢教学平台">
        <span class="brand-mark">枢</span>
        <span class="ev-brand-text">
          <b>云枢教学平台</b>
          <small>YUNSHU / RECORD WORKSPACE</small>
        </span>
      </a>
      <button
        class="icon-btn"
        :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
        @click="toggleTheme"
      >
        <V2Icon :name="theme === 'dark' ? 'sun' : 'moon'" />
      </button>
    </header>

    <main class="ev-main">
      <section class="ev-card">
        <p class="ev-kicker">YUNSHU / ERROR / {{ code }}</p>
        <h1>{{ code }}</h1>
        <h2>{{ copy.title }}</h2>
        <p class="ev-desc">{{ copy.desc }}</p>
        <p v-if="code === '503'" class="ev-status" role="status">{{ statusText }}</p>
        <div class="ev-actions">
          <button
            v-if="code === '503'"
            class="btn btn-primary"
            :disabled="checking"
            @click="checkNow"
          >
            {{ checking ? '检测中…' : '立即重试' }}
          </button>
          <button class="btn btn-ghost" @click="goHome">返回工作台</button>
          <router-link class="text-btn" to="/v2/login">登录页</router-link>
        </div>
        <p v-if="code === '503'" class="ev-note">
          服务恢复后会自动跳转；本页每 3 秒检测一次，预算耗尽后改为手动重试。
        </p>
        <p v-else class="ev-note">如果是账号权限问题，请退出后更换账号登录。</p>
      </section>
    </main>

    <footer class="ev-foot">
      <span>云枢教学平台 · v2</span>
      <span><a href="/login">旧版登录</a></span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { setProbeSilence } from '@/api/request'
import '../../styles/record.css'
import '../../styles/shell-fix.css'
import '../../styles/typography.css'
import V2Sprite from '../../components/V2Sprite.vue'
import V2Icon from '../../components/V2Icon.vue'
import { useV2Ui } from '../../composables/ui'
import { useV2Root } from '../../composables/useV2Root'
import { ROLE_HOME, type V2Role } from '../../types'

const POLL_INTERVAL_MS = 3000
const MAX_POLLS = 40

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { theme, toggleTheme } = useV2Ui()
useV2Root(theme)

const code = computed(() => String(route.meta.v2error || '404'))
const copy = computed(() => {
  if (code.value === '403') {
    return {
      title: '没有访问权限',
      desc: '当前账号无权访问该页面。请返回工作台，或退出后更换账号登录。',
    }
  }
  if (code.value === '503') {
    return {
      title: '后端服务连接失败',
      desc: '服务可能仍在启动中（Spring Boot 冷启动通常需要 30–60 秒），或已经停止运行。',
    }
  }
  return {
    title: '页面不存在',
    desc: '你访问的页面不存在或已经被移动。',
  }
})

const redirectTarget = computed(() => {
  const raw = route.query.redirect
  const value = String(raw || '')
  if (
    value.startsWith('/v2/') &&
    !value.startsWith('/v2/login') &&
    !value.startsWith('/v2/register') &&
    !value.startsWith('/v2/503')
  ) {
    return value
  }
  return ''
})

const checking = ref(false)
const polls = ref(0)
const exhausted = ref(false)
let timer: number | undefined

const statusText = computed(() => {
  if (exhausted.value) {
    return '仍未连接到后端服务。请确认启动脚本正在运行，且 8080 端口已监听。'
  }
  if (checking.value) return `正在连接后端服务…（第 ${polls.value + 1} 次检测）`
  return `等待后端服务就绪…（已检测 ${polls.value} / ${MAX_POLLS} 次）`
})

function v2Home(): string {
  const role = String(auth.effectiveRole || auth.userRole || 'student').toLowerCase()
  return ROLE_HOME[role as V2Role] || ROLE_HOME.student
}

function goHome() {
  router.push(auth.isLoggedIn ? v2Home() : '/v2/login')
}

function stopTimer() {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
}

async function probe() {
  if (checking.value) return
  checking.value = true
  try {
    const verdict = await auth.fetchPermissions()
    if (verdict === 'ok') {
      await router.replace(redirectTarget.value || v2Home())
      return
    }
    if (verdict === 'expired') {
      await router.replace({
        path: '/v2/login',
        query: { redirect: redirectTarget.value || undefined },
      })
      return
    }
    polls.value += 1
    if (polls.value >= MAX_POLLS) {
      exhausted.value = true
      stopTimer()
    }
  } finally {
    checking.value = false
  }
}

function checkNow() {
  exhausted.value = false
  polls.value = 0
  probe().then(() => {
    if (!exhausted.value) {
      stopTimer()
      timer = window.setInterval(probe, POLL_INTERVAL_MS)
    }
  })
}

onMounted(() => {
  if (code.value !== '503') return
  setProbeSilence(true)
  if (auth.permissionsLoaded) {
    router.replace(redirectTarget.value || v2Home())
    return
  }
  timer = window.setInterval(probe, POLL_INTERVAL_MS)
})

onBeforeUnmount(() => {
  stopTimer()
  setProbeSilence(false)
})
</script>

<style scoped>
.err-v2 {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 22px clamp(18px, 4vw, 56px) 26px;
  background: var(--bg);
  color: var(--ink);
}
.ev-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.ev-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: inherit;
  text-decoration: none;
}
.ev-brand .brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: #171b22;
  color: #fff;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 16px;
}
.ev-brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.ev-brand-text b {
  font-size: 14px;
}
.ev-brand-text small {
  margin-top: 3px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
  color: var(--ink-3);
}
.ev-main {
  flex: 1;
  display: grid;
  place-items: center;
  padding: 20px 0;
}
.ev-card {
  width: min(560px, 100%);
  padding: clamp(26px, 4vw, 44px);
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--sh-card);
}
.ev-kicker {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--ink-3);
}
.ev-card h1 {
  margin: 10px 0 0;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: clamp(64px, 10vw, 96px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--accent);
}
.ev-card h2 {
  margin: 14px 0 0;
  font-family: 'Noto Serif SC', Georgia, serif;
  font-size: 22px;
  font-weight: 600;
}
.ev-desc {
  margin: 9px 0 0;
  font-size: 13px;
  line-height: 1.85;
  color: var(--ink-2);
}
.ev-status {
  margin: 12px 0 0;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface-2);
  font-size: 11px;
  color: var(--ink-3);
}
.ev-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
}
.ev-note {
  margin: 14px 0 0;
  font-size: 10px;
  line-height: 1.7;
  color: var(--ink-4);
}
.ev-foot {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--ink-3);
}
.ev-foot a {
  color: var(--ink-2);
  text-decoration: none;
}
@media (max-width: 520px) {
  .err-v2 {
    padding: 16px 14px 20px;
  }
  .ev-card {
    padding: 22px 18px;
  }
}
</style>
