<template>
  <div v-loading="reportLoading">
    <!-- 无成绩方案：空状态 + 新建 -->
    <div v-if="!policy" class="l1-card">
      <div class="l1-body">
        <EmptyState :title="policyEmptyText" icon="chart">
          <el-button type="primary" :icon="Plus" @click="emit('open-policy')"
            >新建成绩方案</el-button
          >
        </EmptyState>
      </div>
    </div>

    <!-- 有方案：报告表格 -->
    <div v-else class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"
          ><el-icon><DataAnalysis /></el-icon
        ></span>
        <div>
          <div class="l1-title">
            课程成绩报告
            <el-tag size="small" type="primary" effect="light" round>{{ policy.name }}</el-tag>
          </div>
          <div v-if="report.length" class="l1-sub">共 {{ report.length }} 名学生</div>
        </div>
      </div>
      <div class="l1-body">
        <!-- 方案级缺陷提示：对所有学生都成立，放在表头一次说清 -->
        <el-alert
          v-for="(w, i) in policyWarnings"
          :key="'pw' + i"
          class="warn-line"
          :title="w"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="ungradedRows"
          class="warn-line"
          :title="
            ungradedRows +
            ' 名学生存在未评分维度，其总评已按「已评分维度的权重合计」归一化（未评分不影响总评分数）'
          "
          type="info"
          :closable="false"
          show-icon
        />
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
              <span
                class="score-cell"
                :class="{ 'is-ungraded': findScore(row, c.key) == null }"
                @click="emit('open-score', row, c)"
              >
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
        <EmptyState v-else :title="emptyText" icon="chart" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Plus, View, DataAnalysis } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const props = defineProps({
  policy: { type: Object, default: null },
  report: { type: Array, default: () => [] },
  reportLoading: { type: Boolean, default: false },
  /** 报告表格空态文案（失败时由页面传入「未能加载」文案，默认保留原语义） */
  emptyText: { type: String, default: '暂无学生成绩' },
  /** 「无成绩方案」空态文案（方案加载失败时由页面传入「未能加载」文案） */
  policyEmptyText: { type: String, default: '该课程尚未配置成绩方案' },
})

const emit = defineEmits(['open-policy', 'open-score', 'preview'])

/** 后端 FinalGradeVO 在本组件用到的键（report 数组的元素形状） */
type ReportRow = {
  studentName?: string
  finalScore?: number | null
  components?: Array<{ key?: string; score?: number | null }>
  warnings?: string[]
}
const rows = computed(() => props.report as unknown as ReportRow[])

/** 方案级警告（对全班成立，去重后置顶显示）与缺评人数 */
const policyWarnings = computed(() => {
  const set = new Set<string>()
  for (const row of rows.value) {
    for (const w of row?.warnings || []) {
      if (isRowLevel(w)) continue
      set.add(w)
    }
  }
  return [...set]
})

const ungradedRows = computed(
  () => rows.value.filter((row) => (row?.warnings || []).some(isRowLevel)).length,
)

// 「未评分…」类提示逐行不同，不适合当方案级横幅，只用于统计人数
function isRowLevel(w) {
  return w.startsWith('未评分维度不计入总评分母') || w.startsWith('该学生尚无任何计入总评')
}

function findScore(row, key) {
  const c = (row.components || []).find((x) => x.key === key)
  return c ? c.score : null
}
</script>

<style scoped>
.warn-line {
  margin-bottom: 10px;
}
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
.score-cell.is-ungraded {
  background: var(--ac-bg-subtle);
  color: var(--ac-text-tertiary);
  font-weight: 500;
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
