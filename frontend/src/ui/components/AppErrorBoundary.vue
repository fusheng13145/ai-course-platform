<template>
  <div v-if="error" class="error-boundary">
    <div class="error-content">
      <div class="error-icon">!</div>
      <h3>页面出了点问题</h3>
      <p class="error-message">{{ error.message || '未知错误' }}</p>
      <div class="error-actions">
        <button class="eb-btn eb-btn-primary" @click="retry">重试</button>
        <button class="eb-btn" @click="goHome">返回首页</button>
        <button class="eb-btn eb-btn-link" @click="showDetails = !showDetails">
          {{ showDetails ? '隐藏详情' : '查看详情' }}
        </button>
      </div>
      <div v-if="showDetails" class="error-details">
        <h4>错误堆栈</h4>
        <pre>{{ error.stack || '无堆栈信息' }}</pre>
        <h4>错误信息</h4>
        <pre>{{
          JSON.stringify({ message: error.message, name: error.name, cause: error.cause }, null, 2)
        }}</pre>
      </div>
    </div>
  </div>
  <slot v-else />
</template>

<script setup>
import { ref, onErrorCaptured } from 'vue'
import { useRouter } from 'vue-router'

defineProps({
  fallbackRender: {
    type: Function,
    default: null,
  },
})

const router = useRouter()
const error = ref(null)
const showDetails = ref(false)

/**
 * 暴露给外部手动调用（如 App.vue 的 Options API errorCaptured）。
 * @param {Error} err - 捕获的错误对象
 */
function captureError(err) {
  error.value = err
  console.error('[ErrorBoundary] 捕获错误:', err)
}

/**
 * Vue 3 Composition API 自动错误捕获。
 * 使用 onErrorCaptured 钩子自动捕获子组件树中抛出的任何未处理错误。
 * 返回 false 阻止错误继续向上传播到 window.onerror。
 */
onErrorCaptured((err, instance, info) => {
  console.error('[ErrorBoundary onErrorCaptured]', err)
  console.error('[ErrorBoundary Source]', info)
  console.error(
    '[ErrorBoundary Component]',
    instance?.$options?.name || instance?.type?.name || 'Unknown',
  )
  error.value = err
  // 返回 false 阻止错误继续向上冒泡
  return false
})

function retry() {
  error.value = null
}

function goHome() {
  router.push('/')
}

// 暴露错误捕获方法供外部调用（兼容 Options API 的 errorCaptured 钩子）
defineExpose({ captureError })
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  padding: 24px;
}

.error-content {
  text-align: center;
  max-width: 400px;
}

.error-icon {
  width: 48px;
  height: 48px;
  line-height: 48px;
  border-radius: var(--r-circle);
  background: var(--danger, #b3261e);
  color: var(--surface, #ffffff);
  font-size: 24px;
  font-weight: 700;
  margin: 0 auto 12px;
}

.error-content h3 {
  color: var(--ink, #17181a);
  margin-bottom: 8px;
}

.error-content p {
  color: var(--ink-3, #8a8d90);
  font-size: 13px;
  margin-bottom: 16px;
}

.error-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.error-details {
  margin-top: 12px;
  text-align: left;
  background: var(--surface-2);
  border-radius: var(--r-sm);
  padding: 8px;
}

.error-details pre {
  font-size: 11px;
  color: var(--ink-3, #8a8d90);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow-y: auto;
}

.eb-btn {
  padding: 6px 14px;
  border: 1px solid var(--line-strong, #c9c7bd);
  border-radius: var(--r-card);
  background: var(--surface, #fff);
  color: var(--ink-2, #4d5054);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.eb-btn:hover {
  border-color: var(--accent, #cf3f2f);
  color: var(--accent, #cf3f2f);
}
.eb-btn-primary {
  background: var(--accent, #cf3f2f);
  border-color: var(--accent, #cf3f2f);
  color: var(--accent-ink, #fff);
}
.eb-btn-primary:hover {
  color: var(--accent-ink, #fff);
  opacity: 0.92;
}
.eb-btn-link {
  border-color: transparent;
  background: transparent;
  color: var(--ink-3, #8a8d90);
}
</style>
