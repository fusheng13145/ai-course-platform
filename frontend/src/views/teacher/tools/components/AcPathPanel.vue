<template>
  <div class="chart-card">
    <div class="chart-title">
      <el-icon><Guide /></el-icon>生成学习路径
    </div>
    <div class="path-form">
      <el-input
        v-model="pathGoal"
        placeholder="输入学习目标，如：掌握线性代数中的矩阵运算"
        style="width: 100%"
      />
      <el-button type="primary" :loading="pathLoading" @click="runPath">
        <el-icon><Promotion /></el-icon>生成路径
      </el-button>
    </div>
    <div v-if="pathResult" v-loading="pathLoading" class="path-result">
      <el-tag
        size="small"
        :type="pathResult.status === 'SUCCESS' ? 'success' : 'info'"
        effect="light"
      >
        {{ pathResult.status || '完成' }}
      </el-tag>
      <p class="ai-summary">{{ pathResult.message || '已生成学习路径' }}</p>
      <pre v-if="pathRaw" class="path-json">{{ pathRaw }}</pre>
    </div>
    <el-empty v-else description="输入目标后生成学习路径" :image-size="64" />
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Guide, Promotion } from '@element-plus/icons-vue'
import aiApi from '@/api/ai'

const props = defineProps({
  courseId: { type: [Number, String], default: '' },
})

const pathGoal = ref('')
const pathLoading = ref(false)
const pathResult = ref(null)
const pathRaw = ref('')

async function runPath() {
  if (!pathGoal.value.trim()) return ElMessage.warning('请输入学习目标')
  if (!props.courseId) return ElMessage.warning('请先选择课程')
  pathLoading.value = true
  try {
    const r = await aiApi.generateLearningPath({
      courseId: props.courseId,
      goal: pathGoal.value.trim(),
    })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    pathResult.value = { status: data.status, message: data.message || data.summary }
    pathRaw.value = JSON.stringify(data, null, 2)
    ElMessage.success('学习路径已生成')
  } catch (e) {
    logger.error('学习路径生成失败', e)
  } finally {
    pathLoading.value = false
  }
}

// 切换课程时清空结果
watch(
  () => props.courseId,
  () => {
    pathResult.value = null
    pathRaw.value = ''
  },
)
onMounted(() => {
  pathResult.value = null
  pathRaw.value = ''
})
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

.path-form {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.path-result {
  padding-top: 8px;
}
.ai-summary {
  margin: 0 0 10px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  font-size: 13.5px;
}
.path-json {
  background: var(--ac-surface-2);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 12px;
  font-size: 12px;
  color: var(--ac-text-secondary);
  overflow-x: auto;
  margin-top: 10px;
}
</style>
