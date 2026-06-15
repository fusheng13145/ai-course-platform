<template>
  <div v-if="error" class="error-boundary">
    <div class="error-content">
      <div class="error-icon">!</div>
      <h3>页面出了点问题</h3>
      <p>{{ error.message || '未知错误' }}</p>
      <div class="error-actions">
        <el-button type="primary" @click="retry">重试</el-button>
        <el-button @click="goHome">返回首页</el-button>
      </div>
      <details v-if="showDetails" class="error-details">
        <pre>{{ error.stack }}</pre>
      </details>
    </div>
  </div>
  <slot v-else />
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  fallbackRender: Function
})

const router = useRouter()
const error = ref(null)
const showDetails = ref(false)

function captureError(err) {
  error.value = err
  console.error('ErrorBoundary caught:', err)
}

function retry() {
  error.value = null
}

function goHome() {
  router.push('/')
}

// 暴露错误捕获方法供外部调用
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
