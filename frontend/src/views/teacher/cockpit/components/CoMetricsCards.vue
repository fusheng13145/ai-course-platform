<template>
  <div>
    <!-- 概览 + 内容 + 学习 指标卡 -->
    <div class="stat-grid animate-in d1">
      <StatCard
        label="选课学生"
        :value="ov.totalStudents || 0"
        icon="users"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="活跃学生"
        :value="ov.activeStudents || 0"
        icon="chart"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="待审批"
        :value="ov.pendingApproval || 0"
        icon="bell"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="待批改"
        :value="grading.pendingGrading || 0"
        icon="edit"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <!-- 内容与学习指标 -->
    <div class="stat-grid">
      <div class="mini-stat">
        <span class="mini-num">{{ ct.chapterCount || 0 }}</span
        ><span class="mini-label">章节</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ ct.publishedChapterCount || 0 }}</span
        ><span class="mini-label">已发布章节</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ ct.taskCount || 0 }}</span
        ><span class="mini-label">任务点</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num"
          >{{ ct.taskPointsBoundToChapters || 0 }}/{{ ct.taskPointsTotal || 0 }}</span
        >
        <span class="mini-label">已绑章节任务</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ ct.examCount || 0 }}</span
        ><span class="mini-label">试卷</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ ct.questionCount || 0 }}</span
        ><span class="mini-label">题库</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ pct(lr.avgProgress) }}</span
        ><span class="mini-label">平均进度</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ pct(lr.completionRate) }}</span
        ><span class="mini-label">完成率</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ lr.avgScore ?? '--' }}</span
        ><span class="mini-label">平均分</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ pct(lr.passRate) }}</span
        ><span class="mini-label">及格率</span>
      </div>
      <div class="mini-stat">
        <span class="mini-num">{{ pct(lr.excellentRate) }}</span
        ><span class="mini-label">优秀率</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import StatCard from '@/components/ui/StatCard.vue'

defineProps({
  ov: { type: Object, default: () => ({}) },
  ct: { type: Object, default: () => ({}) },
  lr: { type: Object, default: () => ({}) },
  grading: { type: Object, default: () => ({}) },
})

function pct(v) {
  if (v == null) return '--'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}
</script>

<style scoped>
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
</style>
