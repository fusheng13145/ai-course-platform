<template>
  <div class="error-page">
    <div class="error-card animate-in">
      <h1>503</h1>
      <p>后端服务连接失败</p>
      <p class="hint">
        服务可能仍在启动中（Spring Boot 冷启动通常需要 30-60 秒），或已经停止运行。
        本页会自动重试，连接恢复后自动跳转到目标页面。
      </p>
      <p class="status" role="status">{{ statusText }}</p>
      <el-button type="primary" :loading="checking" @click="checkNow">立即重试</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { setProbeSilence } from '@/api/request'

// 轮询周期与预算：覆盖 Spring Boot 冷启动（约 30-60s）窗口；
// 预算耗尽后停止自动轮询，转手动"立即重试"（重置预算重新开始）。
const POLL_INTERVAL_MS = 3000
const MAX_POLLS = 40

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const checking = ref(false)
const polls = ref(0)
const exhausted = ref(false)
let timer = null

// redirect 只接受站内路径，防止开放重定向
const redirectTarget = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : ''
})

const statusText = computed(() => {
  if (exhausted.value) {
    return '仍未连接到后端服务。请确认启动脚本（start-all.bat / start-backend.bat）正在运行，且 8080 端口已监听。'
  }
  if (checking.value) return `正在连接后端服务…（第 ${polls.value + 1} 次检测）`
  return `等待后端服务就绪…（已检测 ${polls.value} / ${MAX_POLLS} 次）`
})

async function probe() {
  checking.value = true
  try {
    const verdict = await authStore.fetchPermissions()
    if (verdict === 'ok') {
      // 服务端已确认会话：回到守卫原本要去的页面（无 redirect 时回落角色默认落地页）
      await router.replace(redirectTarget.value || authStore.getDefaultRoute())
      return
    }
    if (verdict === 'expired') {
      // 服务端明确会话失效：与守卫口径一致，带 redirect 去登录页
      await router.replace({
        path: '/login',
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

function startTimer() {
  stopTimer()
  timer = setInterval(probe, POLL_INTERVAL_MS)
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function checkNow() {
  exhausted.value = false
  polls.value = 0
  probe().then(startTimer)
}

onMounted(() => {
  // 轮询期间屏蔽 request.ts 的"网络异常/服务器错误"弹窗与自动重试：
  // 本页自带重试 UI 与状态展示，每 3 秒弹一次 toast 只会掩盖真实状态。
  setProbeSilence(true)
  if (authStore.permissionsLoaded) {
    // 权限已加载（后端就绪后才落到本页）：直接放行，不必再探
    router.replace(redirectTarget.value || authStore.getDefaultRoute())
    return
  }
  startTimer()
})

onUnmounted(() => {
  stopTimer()
  setProbeSilence(false)
})
</script>

<style scoped>
.error-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ac-bg);
}
.error-card {
  text-align: center;
  max-width: 520px;
  padding: 0 24px;
}
.error-card h1 {
  font-size: 120px;
  color: var(--ac-primary);
  margin: 0;
  line-height: 1;
}
.error-card p {
  font-size: 16px;
  color: var(--ac-text-muted);
  margin: 16px 0 24px;
}
.error-card .hint {
  font-size: 14px;
  line-height: 1.7;
}
.error-card .status {
  font-size: 13px;
  margin-bottom: 16px;
  min-height: 20px;
}
</style>
