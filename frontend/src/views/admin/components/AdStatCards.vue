<template>
  <div class="stat-grid animate-in d1">
    <StatCard
      label="平台总用户"
      icon="users"
      tone="var(--accent-soft)"
      tone-color="var(--accent)"
      :foot="`教师 ${fmt(overview.roleDistribution?.teacher)} · 学生 ${fmt(overview.roleDistribution?.student)}`"
      ><template #value><CountUp :value="Number(overview.totalUsers ?? 0)" /></template
    ></StatCard>
    <StatCard
      label="课程总数"
      icon="book"
      tone="var(--info-soft)"
      tone-color="var(--info)"
      :foot="`${overview.activeCourses ?? 0} 门活跃课程`"
      ><template #value><CountUp :value="Number(overview.totalCourses ?? 0)" /></template
    ></StatCard>
    <StatCard label="选课总人次" icon="send" tone="var(--ok-soft)" tone-color="var(--ok)"
      ><template #value><CountUp :value="Number(overview.totalEnrollments ?? 0)" /></template
    ></StatCard>
    <StatCard
      label="作业提交率"
      :value="submitRate"
      icon="chart"
      tone="var(--warn-soft)"
      tone-color="var(--warn)"
      :foot="`成绩优良率 ${excellentRate}%`"
    />
  </div>
</template>

<script setup lang="ts">
import StatCard from '@/components/ui/StatCard.vue'
import CountUp from '@/components/CountUp.vue'

defineProps({
  overview: { type: Object, default: () => ({}) },
  submitRate: { type: [String, Number], default: '—' },
  excellentRate: { type: [String, Number], default: '—' },
})

function fmt(n) {
  return (n ?? 0).toLocaleString('zh-CN')
}
</script>
