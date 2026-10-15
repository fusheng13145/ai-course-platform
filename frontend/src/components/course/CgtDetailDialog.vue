<template>
  <el-dialog
    :model-value="modelValue"
    title="学生成绩详情"
    width="600px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template v-if="currentStudent">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="学号">{{
          currentStudent.studentNo || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{
          currentStudent.studentName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item v-if="detailed || currentStudent.courseName || selectedCourseName" label="课程">{{
          currentStudent.courseName || selectedCourseName
        }}</el-descriptions-item>
        <el-descriptions-item label="总评">
          <span class="score-big">{{ currentStudent.totalScore ?? '--' }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="currentStudent.warnings?.length"
        class="grade-warnings"
        :title="currentStudent.warnings.join('；')"
        type="warning"
        :closable="false"
        show-icon
      />

      <div class="detail-block">
        <h4 v-if="detailed" class="detail-h4">各项明细</h4>
        <el-table :data="currentStudent.details || []" size="small" style="width: 100%">
          <el-table-column prop="taskName" label="成绩项" />
          <el-table-column prop="type" label="类型" width="80" align="center">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="
                  row.type === 'exam' ? 'danger' : row.type === 'component' ? 'primary' : 'info'
                "
                >{{
                  row.type === 'exam' ? '考试' : row.type === 'component' ? '构成项' : '作业'
                }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="得分" width="70" align="center">
            <template #default="{ row }">{{ row.score ?? '--' }}</template>
          </el-table-column>
          <el-table-column v-if="!detailed" prop="weight" label="权重" width="70" align="center">
            <template #default="{ row }">{{
              row.weight == null ? '--' : `${row.weight}%`
            }}</template>
          </el-table-column>
          <el-table-column
            v-if="!detailed"
            prop="contribution"
            label="贡献"
            width="70"
            align="center"
          >
            <template #default="{ row }">{{ row.contribution ?? '--' }}</template>
          </el-table-column>
          <el-table-column
            v-if="detailed"
            prop="fullScore"
            label="满分"
            width="70"
            align="center"
          />
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="
                  row.type === 'component'
                    ? row.graded
                      ? 'success'
                      : 'warning'
                    : row.status === 'graded'
                      ? 'success'
                      : 'warning'
                "
                >{{
                  row.type === 'component'
                    ? row.graded
                      ? '已评分'
                      : '未评分/未计入'
                    : row.status === 'graded'
                      ? '已批阅'
                      : '待批阅'
                }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column v-if="detailed" prop="submitTime" label="提交时间" />
        </el-table>
        <EmptyState
          v-if="!currentStudent.details?.length"
          title="暂无成绩构成明细"
          icon="chart"
          size="compact"
        />
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 学生成绩详情弹窗（成绩域共享实现）
 * <p>教师成绩页与学生课程成绩页共用；角色差异以 props 表达：
 * `detailed`（「课程」行 + 满分/提交时间列 + 空态）、`selectedCourseName`（课程名回退）。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 当前查看的学生成绩（含 details 明细） */
  currentStudent: { type: Object, default: null },
  /** 当前选中课程名（详情无课程时回退显示） */
  selectedCourseName: { type: String, default: '' },
  /** 是否展示完整明细（教师成绩页为 true） */
  detailed: { type: Boolean, default: false },
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.score-big {
  font-weight: 700;
  font-size: 18px;
  color: var(--ac-primary);
}

.detail-block {
  margin-top: 16px;
}
.grade-warnings {
  margin-top: 12px;
}

.detail-h4 {
  font-size: 14px;
  color: var(--ac-text-primary);
  margin-bottom: 12px;
}
</style>
