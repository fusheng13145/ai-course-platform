<template>
  <div v-loading="reportLoading">
    <!-- 无成绩方案：空状态 + 新建 -->
    <div v-if="!policy" class="l1-card">
      <div class="l1-body">
        <el-empty description="该课程尚未配置成绩方案">
          <el-button type="primary" :icon="Plus" @click="emit('open-policy')">新建成绩方案</el-button>
        </el-empty>
      </div>
    </div>

    <!-- 有方案：报告表格 -->
    <div v-else class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"><el-icon><DataAnalysis /></el-icon></span>
        <div>
          <div class="l1-title">
            课程成绩报告
            <el-tag size="small" type="primary" effect="light" round>{{ policy.name }}</el-tag>
          </div>
          <div v-if="report.length" class="l1-sub">共 {{ report.length }} 名学生</div>
        </div>
      </div>
      <div class="l1-body">
        <el-table v-if="report.length" :data="report" size="small" border max-height="560">
          <el-table-column prop="studentName" label="学生" min-width="110" fixed />
          <el-table-column
            v-for="c in policy.components"
            :key="c.key"
            :label="c.name + ' (' + c.weight + '%)'"
            min-width="120"
            align="center"
          >
            <template #default="{ row }">
              <span class="score-cell" @click="emit('open-score', row, c)">
                {{ findScore(row, c.key) ?? '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="总评" min-width="90" align="center" fixed="right">
            <template #default="{ row }">
              <span class="final-score">{{ row.finalScore ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="emit('preview', row)"
                >预览总评</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无学生成绩" :image-size="72" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus, View, DataAnalysis } from '@element-plus/icons-vue'

defineProps({
  policy: { type: Object, default: null },
  report: { type: Array, default: () => [] },
  reportLoading: { type: Boolean, default: false },
})

const emit = defineEmits(['open-policy', 'open-score', 'preview'])

function findScore(row, key) {
  const c = (row.components || []).find((x) => x.key === key)
  return c ? c.score : null
}
</script>

<style scoped>
.score-cell {
  display: inline-block;
  min-width: 44px;
  padding: 2px 8px;
  border-radius: var(--ac-radius-sm);
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  cursor: pointer;
  font-weight: 600;
  transition: background var(--ac-transition-fast);
}
.score-cell:hover {
  background: var(--ac-primary);
  color: var(--ac-on-primary);
}
.final-score {
  font-weight: 700;
  color: var(--ac-text-primary);
}
</style>
