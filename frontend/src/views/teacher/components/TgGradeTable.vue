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
      <el-table-column prop="submitTime" label="最后提交" width="160" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click.stop="$emit('detail', row)"
            >详情</el-button
          >
          <el-button
            v-if="row.needsGrading"
            text
            type="warning"
            size="small"
            @click.stop="$emit('grade', row)"
            >批阅</el-button
          >
          <el-button
            v-if="row.needsGrading"
            text
            type="success"
            size="small"
            @click.stop="$emit('ai-grade', row)"
            >AI评分</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && gradeList.length === 0" description="暂无成绩数据">
      <template #image><div class="empty-deco">📊</div></template>
    </el-empty>
  </div>
</template>

<script setup>
import { monoScore } from '@/utils/chartTheme'

/**
 * 教师成绩表格
 * <p>成绩明细表（作业/考试/总评/等级/提交时间）与行级操作（详情/批阅/AI评分）。</p>
 */
defineProps({
  /** 加载态 */
  loading: { type: Boolean, default: false },
  /** 过滤后的成绩列表 */
  gradeList: { type: Array, required: true },
  /** 等级 → 标签色映射函数 */
  getGradeTagType: { type: Function, required: true },
})

defineEmits(['detail', 'grade', 'ai-grade'])
</script>

<style scoped>
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

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
