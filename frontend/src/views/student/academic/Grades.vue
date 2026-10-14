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
        :value="loadError ? '--' : avgScore"
        icon="chart"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="已完成考试"
        :value="loadError ? '--' : list.length"
        icon="doc"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="班级排名"
        :value="loadError ? '--' : rankDisplay"
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
        <!-- 加载失败提示：失败时列表被置空，仅剩"暂无成绩"会让学生误判为"我还没考过试" -->
        <el-alert
          v-if="loadError"
          class="load-error"
          :title="loadError"
          type="error"
          :closable="false"
          show-icon
        />
        <SgChartsPanel
          :loading="loading"
          :grouped-by-course="groupedByCourse"
          :list="list"
          :radar-empty-text="radarEmptyText"
          :trend-empty-text="trendEmptyText"
        />
        <SgCourseGrades
          v-model:show-table="showTable"
          :loading="loading"
          :grouped-by-course="groupedByCourse"
          :list="list"
          :empty-text="gradesEmptyText"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import SgChartsPanel from './components/SgChartsPanel.vue'
import SgCourseGrades from './components/SgCourseGrades.vue'
import { useStudentGrades } from '@/composables/useStudentGrades'

const { loading, loadError, list, showTable, avgScore, rankDisplay, groupedByCourse, loadData } =
  useStudentGrades()

// D2-1：双态空文案下传给子面板——加载失败时不得把「失败」显示成
// 「暂无成绩记录 / 暂无考试记录 / 选课不足」，让学生误判自己没考过试。
const LOAD_FAILED_TEXT = '成绩数据未能加载，请查看上方提示后重试'
const gradesEmptyText = computed(() =>
  loadError.value ? LOAD_FAILED_TEXT : '暂无成绩记录',
)
const trendEmptyText = computed(() => (loadError.value ? LOAD_FAILED_TEXT : '暂无考试记录'))
const radarEmptyText = computed(() =>
  loadError.value
    ? LOAD_FAILED_TEXT
    : '选课不足（至少3门），暂无法生成多维对比分析',
)

const route = useRoute()
onMounted(() => loadData(route.params.id || route.query.courseId || null))
</script>
