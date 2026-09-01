<template>
  <div>
    <!-- 各课程详细成绩卡片 -->
    <div class="grades-section">
      <h3 class="section-title">课程成绩详情</h3>
      <div class="course-grade-grid">
        <div
          v-for="(group, courseName) in groupedByCourse"
          :key="courseName"
          class="grade-course-card"
        >
          <div class="card-header">
            <h4 class="course-name">{{ courseName }}</h4>
            <el-tag :type="getGradeLevelType(group.avgScore)" size="small">
              {{ getGradeLevelLabel(group.avgScore) }}
            </el-tag>
          </div>

          <!-- 该课程的平均分 -->
          <div class="score-highlight">
            <span class="score-big">{{ group.avgScore }}</span>
            <span class="score-max">/ {{ group.maxTotal || 100 }}</span>
          </div>

          <!-- 排名信息 -->
          <div v-if="group.rank" class="rank-info">
            <span class="rank-label">排名：</span>
            <span class="rank-value">{{ group.rank }} / {{ group.totalStudents || '--' }}</span>
          </div>

          <!-- 成绩列表 -->
          <div class="exam-list-mini">
            <div v-for="exam in group.exams" :key="exam.id" class="exam-mini-item">
              <span class="mini-name">{{ exam.examTitle || '考试' }}</span>
              <el-tag :type="getGradeLevelTag(exam.score)" size="small">
                {{ exam.score }} 分
              </el-tag>
              <span class="mini-time">{{ formatTime(exam.submitTime, '--') }}</span>
            </div>
          </div>
        </div>
      </div>

      <el-empty
        v-if="!loading && Object.keys(groupedByCourse).length === 0"
        description="暂无成绩记录"
        :image-size="100"
      >
        <template #image><div class="empty-deco">📉</div></template>
      </el-empty>

      <!-- 完整表格（折叠） -->
      <div v-if="list.length > 0" class="table-section">
        <el-button text class="table-toggle" @click="$emit('update:showTable', !showTable)">
          {{ showTable ? '收起' : '展开' }}完整成绩表
          <el-icon><ArrowDown :class="{ rotated: showTable }" /></el-icon>
        </el-button>
        <transition name="expand-fade">
          <el-table
            v-if="showTable"
            v-loading="loading"
            :data="list"
            stripe
            class="grade-table"
            row-class-name="grade-table-row"
          >
            <el-table-column prop="examTitle" label="考试名称" min-width="160" />
            <el-table-column prop="courseName" label="课程" width="140" />
            <el-table-column prop="score" label="成绩" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="getGradeLevelTag(row.score)" size="small">{{
                  row.score
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalScore" label="总分" width="70" align="center" />
            <el-table-column prop="submitTime" label="提交时间" width="160">
              <template #default="{ row }">{{ formatTime(row.submitTime, '--') }}</template>
            </el-table-column>
            <el-table-column label="等级" width="80" align="center">
              <template #default="{ row }">
                <span class="level-text" :class="'level-' + getGradeClass(row.score)">
                  {{ getGradeLevelLabel(row.score) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatTime } from '@/utils/datetime'
import { ArrowDown } from '@element-plus/icons-vue'
import { useStudentGrades } from '@/composables/useStudentGrades'

defineProps({
  loading: { type: Boolean, default: false },
  groupedByCourse: { type: Object, default: () => ({}) },
  list: { type: Array, default: () => [] },
  showTable: { type: Boolean, default: false },
})
defineEmits(['update:showTable'])

const { getGradeLevelTag, getGradeLevelType, getGradeLevelLabel, getGradeClass } =
  useStudentGrades()
</script>

<style scoped>
/* ---- 课程成绩卡片 ---- */
.grades-section {
  margin-top: var(--ac-spacing-lg);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-md);
  padding-left: 12px;
  border-left: 3px solid var(--ac-primary);
}

.course-grade-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--ac-spacing-md);
}

.grade-course-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
}

.grade-course-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--ac-shadow-md);
  border-left: 4px solid var(--ac-primary);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-sm);
}

.course-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.score-highlight {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: var(--ac-spacing-xs);
}

.score-big {
  font-size: 32px;
  font-weight: 700;
  color: var(--ac-primary);
  line-height: 1;
}

.score-max {
  font-size: 16px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.rank-info {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-bottom: var(--ac-spacing-md);
}

.rank-label {
  color: var(--ac-text-muted);
}

.rank-value {
  font-weight: 600;
  color: var(--ac-text-primary);
}

.exam-list-mini {
  border-top: 1px solid var(--ac-border);
  padding-top: var(--ac-spacing-sm);
}

.exam-mini-item {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  padding: 6px 0;
  font-size: 13px;
}

.mini-name {
  flex: 1;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mini-time {
  font-size: 11px;
  color: var(--ac-text-muted);
  white-space: nowrap;
}

/* ---- 表格区域 ---- */
.table-section {
  margin-top: var(--ac-spacing-lg);
}

.table-section .el-icon {
  transition: transform var(--ac-transition-fast);
}

.table-toggle {
  margin-bottom: var(--ac-spacing-sm);
}

.grade-table {
  width: 100%;
}

.table-section .el-icon.rotated {
  transform: rotate(180deg);
}

.level-text {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.level-excellent {
  background: var(--ok-soft);
  color: var(--ok);
}
.level-good {
  background: var(--accent-soft);
  color: var(--accent-strong);
}
.level-pass {
  background: var(--warn-soft);
  color: var(--warn);
}
.level-fail {
  background: var(--accent-soft);
  color: var(--accent-strong);
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

.expand-fade-enter-active {
  transition: all 0.25s ease;
}
.expand-fade-leave-active {
  transition: all 0.15s ease;
}
.expand-fade-enter-from,
.expand-fade-leave-to {
  opacity: 0;
  max-height: 0;
  overflow: hidden;
}

@media (max-width: 900px) {
  .course-grade-grid {
    grid-template-columns: 1fr;
  }
}
</style>
