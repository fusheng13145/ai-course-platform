<template>
  <div v-if="error" class="error-boundary">
    <div class="error-content">
      <div class="error-icon">!</div>
      <h3>页面出了点问题</h3>
      <p class="error-message">{{ error.message || '未知错误' }}</p>
      <div class="error-actions">
        <el-button type="primary" @click="retry">重试</el-button>
        <el-button @click="goHome">返回首页</el-button>
        <el-button link type="info" @click="showDetails = !showDetails">
          {{ showDetails ? '隐藏详情' : '查看详情' }}
        </el-button>
      </div>
      <div v-if="showDetails" class="error-details">
        <h4>错误堆栈</h4>
        <pre>{{ error.stack || '无堆栈信息' }}</pre>
        <h4>错误信息</h4>
        <pre>{{ JSON.stringify({ message: error.message, name: error.name, cause: error.cause }, null, 2) }}</pre>
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
    default: null
  }
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
  console.error('[ErrorBoundary Component]', instance?.$options?.name || instance?.type?.name || 'Unknown')
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
  padding: var(--ac-spacing-xl);
}

.error-content {
  text-align: center;
  max-width: 400px;
}

.error-icon {
  width: 48px;
  height: 48px;
  line-height: 48px;
  border-radius: 50%;
  background: var(--ac-danger);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  margin: 0 auto var(--ac-spacing-md);
}

.error-content h3 {
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-sm);
}

.error-content p {
  color: var(--ac-text-muted);
  font-size: 13px;
  margin-bottom: var(--ac-spacing-lg);
}

.error-actions {
  display: flex;
  gap: var(--ac-spacing-sm);
  justify-content: center;
}

.error-details {
  margin-top: var(--ac-spacing-md);
  text-align: left;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  padding: var(--ac-spacing-sm);
}

.error-details pre {
  font-size: 11px;
  color: var(--ac-text-muted);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 150px;
  overflow-y: auto;
}
</style>
