<template>
  <div class="essay-grid">
    <div class="chart-card">
      <div class="chart-title">
        <el-icon><EditPen /></el-icon>作文内容
      </div>
      <el-input
        v-model="essayText"
        type="textarea"
        :rows="12"
        placeholder="粘贴学生作文内容…"
        maxlength="5000"
        show-word-limit
      />
      <div class="essay-actions">
        <el-button type="primary" :loading="essayLoading" @click="runCorrect">
          <el-icon><Check /></el-icon>批改
        </el-button>
        <el-button :loading="polishLoading" :disabled="!essayText.trim()" @click="runPolish">
          <el-icon><MagicStick /></el-icon>AI 润色
        </el-button>
        <el-button text @click="clearAll">清空</el-button>
      </div>
    </div>
    <div class="chart-card">
      <div class="chart-title">
        <el-icon><Document /></el-icon>批改结果
      </div>
      <div
        v-if="essayResult"
        v-loading="essayLoading"
        class="ai-summary"
        style="white-space: pre-wrap"
      >
        {{ essayResult }}
      </div>
      <EmptyState v-else title="点击「批改」查看结果" icon="checks" />
    </div>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { ElMessage } from 'element-plus'
import { EditPen, Check, Document, MagicStick } from '@element-plus/icons-vue'
import aiApi from '@/api/ai'

const essayText = ref('')
const essayResult = ref('')
const essayLoading = ref(false)
const polishLoading = ref(false)

async function runCorrect() {
  if (!essayText.value.trim()) return ElMessage.warning('请输入作文内容')
  essayLoading.value = true
  try {
    const r = await aiApi.essayCorrect({ content: essayText.value.trim() })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    essayResult.value = data.result || data.corrected || data.feedback || '（暂无批改结果）'
    ElMessage.success('批改完成')
  } catch (e) {
    logger.error('作文批改失败', e)
  } finally {
    essayLoading.value = false
  }
}

async function runPolish() {
  if (!essayText.value.trim()) return ElMessage.warning('请输入作文内容')
  polishLoading.value = true
  try {
    const r = await aiApi.essayPolish({ content: essayText.value.trim() })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    const polished = data.result || data.polished || data.content || ''
    if (polished) {
      essayText.value = polished
      ElMessage.success('已润色')
    } else {
      ElMessage.warning('未返回润色结果')
    }
  } catch (e) {
    logger.error('作文润色失败', e)
  } finally {
    polishLoading.value = false
  }
}

function clearAll() {
  essayText.value = ''
  essayResult.value = ''
}
</script>

<style scoped>
.essay-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ac-spacing-md);
}
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
.essay-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.ai-summary {
  margin: 0 0 10px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  font-size: 13.5px;
}

@media (max-width: 1100px) {
  .essay-grid {
    grid-template-columns: 1fr;
  }
}
</style>
