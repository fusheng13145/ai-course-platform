<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-danger)"><el-icon :size="24"><WarningFilled /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.total }}</h3><p>错题总数</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-warning)"><el-icon :size="24"><Reading /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.total - stats.mastered }}</h3><p>待复习</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background:var(--ac-success)"><el-icon :size="24"><Select /></el-icon></div>
        <div class="stat-info"><h3>{{ stats.mastered }}</h3><p>已掌握</p></div>
      </div>
    </div>
    <div class="content-card">
      <div v-for="q in questions" :key="q.id" class="question-item">
        <div class="question-header">
          <span class="question-title">{{ q.questionContent || q.content || '未知题目' }}</span>
          <el-tag :type="q.mastered ? 'success' : 'warning'" size="small">{{ q.mastered ? '已掌握' : '待复习' }}</el-tag>
        </div>
        <div class="question-answer">
          错误答案：<span class="wrong-answer">{{ q.wrongAnswer || '无' }}</span>
        </div>
        <el-button v-if="!q.mastered" text type="primary" size="small" style="margin-top:4px" @click="markMastered(q)">标记已掌握</el-button>
      </div>
      <el-empty v-if="questions.length === 0" description="暂无错题记录" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import wrongQuestionApi from '@/api/wrongQuestion'

const stats = ref({ total: 0, mastered: 0 })
const questions = ref([])

onMounted(async () => {
  try {
    const [statsRes, listRes] = await Promise.all([
      wrongQuestionApi.getWrongQuestionStats(),
      wrongQuestionApi.getWrongQuestions()
    ])
    const statsData = statsRes.data || statsRes || {}
    stats.value = {
      total: statsData.total || 0,
      mastered: statsData.mastered || 0
    }
    const list = listRes.data || listRes || []
    questions.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('Failed to load wrong questions:', e)
  }
})

async function markMastered(q) {
  try {
    await wrongQuestionApi.markAsReviewed(q.id)
    q.mastered = true
    stats.value.mastered++
    ElMessage.success('已标记为掌握')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}
</script>

<style scoped>
.question-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--ac-border);
}

.question-item:last-child {
  border-bottom: none;
}

.question-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.question-title {
  font-weight: 600;
  color: var(--ac-text-primary);
}

.question-answer {
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.wrong-answer {
  color: var(--ac-danger);
}
</style>