<template>
  <el-dialog
    :model-value="modelValue"
    title="成绩详情"
    width="600px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="grade" class="grade-detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="学生姓名">{{ grade.studentName }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ grade.studentId }}</el-descriptions-item>
        <el-descriptions-item label="课程名称">{{ grade.courseName }}</el-descriptions-item>
        <el-descriptions-item label="考试名称">{{ grade.examTitle }}</el-descriptions-item>
        <el-descriptions-item label="得分">
          <span class="score-badge large" :class="getScoreClass(grade.score)">
            {{ grade.score }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="排名">{{ grade.rank || '--' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间" :span="2">{{
          grade.submitTime
        }}</el-descriptions-item>
      </el-descriptions>

      <!-- 答题明细（如果有） -->
      <div v-if="grade.details && grade.details.length > 0" class="answer-section">
        <h4>答题明细</h4>
        <el-table :data="grade.details" size="small" max-height="250">
          <el-table-column prop="questionNum" label="题号" width="70" />
          <el-table-column prop="questionContent" label="题目内容" show-overflow-tooltip />
          <el-table-column prop="userAnswer" label="作答" width="120" />
          <el-table-column prop="correctAnswer" label="正确答案" width="120" />
          <el-table-column prop="points" label="得分" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.points > 0 ? 'success' : 'danger'" size="small">
                {{ row.points }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 成绩详情对话框
 * <p>展示单条成绩的基本信息（学生/课程/考试/得分/排名/提交时间）与答题明细表；
 * 可见性通过 v-model 双向绑定。</p>
 */
defineProps({
  /** 对话框可见性（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 当前选中的成绩记录 */
  grade: { type: Object, default: null },
  /** 分数段样式函数 */
  getScoreClass: { type: Function, required: true },
})

const emit = defineEmits(['update:modelValue'])
</script>

<style scoped>
.grade-detail h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 16px 0 12px;
}
.answer-section {
  margin-top: 16px;
}

/* 成绩徽章（与列表同套样式，large 为详情放大版） */
.score-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 6px;
  font-weight: 600;
  font-size: 13px;
}
.score-badge.excellent {
  background: var(--ok-soft);
  color: var(--ok);
}
.score-badge.good {
  background: var(--info-soft);
  color: var(--info);
}
.score-badge.medium {
  background: var(--violet-soft);
  color: var(--violet);
}
.score-badge.pass {
  background: var(--warn-soft);
  color: var(--warn);
}
.score-badge.fail {
  background: var(--accent-soft);
  color: var(--accent);
}
.score-badge.large {
  font-size: 20px;
  padding: 6px 16px;
}
</style>
