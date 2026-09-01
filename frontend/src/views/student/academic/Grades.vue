<template>
  <div class="fade-in">
    <PageHeader
      title="我的成绩"
      subtitle="多维成绩分析与学习趋势跟踪"
      icon="chart"
      icon-bg="accent"
    />

    <div class="stat-grid animate-in d1">
      <StatCard
        label="平均分"
        :value="avgScore"
        icon="chart"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="已完成考试"
        :value="list.length"
        icon="doc"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="班级排名"
        :value="rankDisplay"
        icon="award"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M3 3v18h18M18 17V9M13 17V5M8 17v-3" />
          </svg>
        </span>
        <div>
          <div class="l1-title">成绩分析</div>
          <div class="l1-sub">成绩维度雷达图、历次趋势与课程明细</div>
        </div>
      </div>
      <div class="l1-body">
        <SgChartsPanel :loading="loading" :grouped-by-course="groupedByCourse" :list="list" />
        <SgCourseGrades
          v-model:show-table="showTable"
          :loading="loading"
          :grouped-by-course="groupedByCourse"
          :list="list"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import SgChartsPanel from './components/SgChartsPanel.vue'
import SgCourseGrades from './components/SgCourseGrades.vue'
import { useStudentGrades } from '@/composables/useStudentGrades'

const { loading, list, showTable, avgScore, rankDisplay, groupedByCourse, loadData } =
  useStudentGrades()

onMounted(loadData)
</script>
