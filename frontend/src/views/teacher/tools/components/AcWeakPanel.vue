<template>
  <div v-loading="weakLoading" class="chart-card">
    <div class="chart-title">
      <el-icon><Warning /></el-icon>薄弱知识点分析
    </div>
    <div v-if="weakStats" class="stat-grid stat-grid--sm">
      <div class="mini-stat">
        <span class="mini-num">{{ weakStats.avgScore ?? '--' }}</span
        ><span class="mini-label">平均分</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ weakStats.weakCount ?? weakPoints.length }}</span
        ><span class="mini-label">薄弱点</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ weakStats.completionRate ?? '--' }}</span
        ><span class="mini-label">完成率</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ weakStats.studentCount ?? '--' }}</span
        ><span class="mini-label">学生数</span>
      </div>
    </div>
    <div v-if="weakSummary" class="ai-summary">{{ weakSummary }}</div>
    <el-table v-if="weakPoints.length" :data="weakPoints" size="small" empty-text="暂无薄弱点">
      <el-table-column prop="pointName" label="知识点" min-width="160" />
      <el-table-column prop="masteryRate" label="掌握度" width="120">
        <template #default="{ row }">
          <el-progress :percentage="numPct(row.masteryRate)" :stroke-width="10" />
        </template>
      </el-table-column>
      <el-table-column prop="weakStudentCount" label="薄弱人数" width="100" align="center" />
      <el-table-column prop="level" label="等级" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.level === 'HIGH' ? 'danger' : 'warning'" effect="light">
            {{ row.level === 'HIGH' ? '高危' : '关注' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
    <EmptyState v-else title="暂无薄弱点数据" icon="alert" />
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, watch, onMounted } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { Warning } from '@element-plus/icons-vue'
import aiApi from '@/api/ai'

const props = defineProps({
  courseId: { type: [Number, String], default: '' },
})

const weakLoading = ref(false)
const weakPoints = ref([])
const weakStats = ref(null)
const weakSummary = ref('')

function numPct(v) {
  if (v == null) return 0
  if (typeof v === 'number' && v <= 1.0001 && v > 0) return Math.round(v * 100)
  return Number(v) || 0
}

async function loadWeak() {
  if (!props.courseId) return
  weakLoading.value = true
  try {
    const r = await aiApi.weakPointsAnalysis({ courseId: props.courseId })
    const body = unwrap(r)
    const data = unwrap(body) || {}
    weakPoints.value = data.weakPoints || data.list || data.points || []
    weakStats.value = data.userStats || data.stats || null
    weakSummary.value = data.summary || ''
  } catch (e) {
    logger.error('薄弱点分析失败', e)
  } finally {
    weakLoading.value = false
  }
}

watch(() => props.courseId, loadWeak)
onMounted(loadWeak)
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

.stat-grid--sm {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.mini-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 14px 8px;
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
}
.mini-num {
  font-size: 18px;
  font-weight: 660;
  color: var(--ac-text-primary);
}
.mini-label {
  font-size: 11.5px;
  color: var(--ac-text-muted);
}

.ai-summary {
  margin: 0 0 10px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  font-size: 13.5px;
}

@media (max-width: 1100px) {
  .stat-grid--sm {
    grid-template-columns: 1fr;
  }
}
</style>
