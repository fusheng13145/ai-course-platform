<template>
  <div v-loading="adviceLoading" class="chart-card">
    <div class="chart-title">
      <el-icon><Lightning /></el-icon>个性化学习建议
    </div>
    <!-- 加载失败提示：失败不得渲染成「暂无学习建议」（D2-1 双态规范） -->
    <el-alert
      v-if="adviceError"
      :title="adviceError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ac-spacing-md)"
    />
    <p v-if="adviceSummary" class="ai-summary">{{ adviceSummary }}</p>
    <div v-if="adviceItems.length" class="advice-list">
      <div v-for="(it, i) in adviceItems" :key="i" class="advice-item">
        <div class="ai-head">
          <el-tag size="small" :type="levelType(it.level)" effect="light">{{
            levelText(it.level)
          }}</el-tag>
          <span class="ai-title">{{ it.title }}</span>
        </div>
        <p class="ai-detail">{{ it.detail }}</p>
      </div>
    </div>
    <EmptyState
      v-else
      :title="adviceError ? '学习建议未能加载，请查看上方提示后重试' : '暂无学习建议'"
      icon="book"
    />
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, watch, onMounted } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { Lightning } from '@element-plus/icons-vue'
import aiApi from '@/api/ai'

const props = defineProps({
  courseId: { type: [Number, String], default: '' },
})

const adviceLoading = ref(false)
const adviceItems = ref([])
const adviceSummary = ref('')
/** 加载失败文案；非空表示「加载失败」而非「无数据」（D2-1） */
const adviceError = ref('')

function levelType(l) {
  return l === 'HIGH' || l === '高'
    ? 'danger'
    : l === 'MEDIUM' || l === '中'
      ? 'warning'
      : 'success'
}
function levelText(l) {
  return l === 'HIGH' || l === '高' ? '高' : l === 'MEDIUM' || l === '中' ? '中' : '低'
}

async function loadAdvice() {
  if (!props.courseId) return
  adviceLoading.value = true
  adviceError.value = ''
  try {
    const r = await aiApi.learningAdvice({ courseId: props.courseId })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    adviceSummary.value = data.summary || ''
    adviceItems.value = data.items || data.list || []
  } catch (e) {
    logger.error('学习建议加载失败', e)
    adviceError.value = '学习建议加载失败，请稍后重试'
  } finally {
    adviceLoading.value = false
  }
}

watch(() => props.courseId, loadAdvice)
onMounted(loadAdvice)
</script>

<style scoped>
.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.ai-summary {
  margin: 0 0 10px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  font-size: 13.5px;
}
.advice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.advice-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.ai-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.ai-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.ai-detail {
  margin: 0;
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
}
</style>
