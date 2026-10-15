<template>
  <div class="content-card content-card--flush">
    <el-table v-loading="loading" :data="gradeList" @row-click="$emit('detail', $event)">
      <el-table-column prop="studentNo" label="学号" width="110" />
      <el-table-column prop="studentName" label="姓名" width="90">
        <template #default="{ row }"
          ><span class="student-name">{{ row.studentName }}</span></template
        >
      </el-table-column>
      <el-table-column prop="homeworkScore" label="作业成绩" width="95" align="center">
        <template #default="{ row }">
          <span v-if="row.homeworkScore != null" :style="{ color: monoScore(row.homeworkScore) }">{{
            row.homeworkScore
          }}</span>
          <span v-else class="cell-muted">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="examScore" label="考试成绩" width="95" align="center">
        <template #default="{ row }">
          <span v-if="row.examScore != null" :style="{ color: monoScore(row.examScore) }">{{
            row.examScore
          }}</span>
          <span v-else class="cell-muted">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="totalScore" label="总评" width="80" align="center">
        <template #default="{ row }">
          <span v-if="row.totalScore != null" class="cell-strong">{{ row.totalScore }}</span>
          <span v-else class="cell-muted">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="gradeLevel" label="等级" width="70" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.gradeLevel" :type="getGradeTagType(row.gradeLevel)" size="small">{{
            row.gradeLevel
          }}</el-tag>
          <span v-else>--</span>
        </template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程" min-width="130" show-overflow-tooltip />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click.stop="$emit('detail', row)"
            >详情</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <EmptyState v-if="!loading && gradeList.length === 0" :title="emptyDescription" icon="chart" />
  </div>
</template>

<script setup lang="ts">
import { monoScore } from '@/utils/chartTheme'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 教师成绩表格
 * <p>显示课程学情分均分与成绩方案总评；具体作业批阅入口位于待批阅任务。</p>
 */
defineProps({
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 过滤后的成绩列表 */
  gradeList: { type: Array, required: true },
  /** 等级 → 标签色映射函数 */
  getGradeTagType: { type: Function, required: true },
  /** 无结果时的说明，区分空课程与搜索无匹配 */
  emptyDescription: { type: String, default: '暂无成绩数据' },
})

defineEmits(['detail'])
</script>

<style scoped>
.content-card--flush {
  padding: 0;
  overflow: hidden;
}

.student-name {
  font-weight: 500;
}

.cell-muted {
  color: var(--ac-text-muted);
}

.cell-strong {
  font-weight: 600;
  color: var(--ac-text-primary);
}
</style>
