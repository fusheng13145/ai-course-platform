<template>
  <!-- 数据看板 + 图表区 -->
  <div class="grid-32 animate-in d2">
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico ico-ok"
          ><svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M3 3v18h18M18 17V9M13 17V5M8 17v-3" /></svg
        ></span>
        <div><div class="l1-title">近 30 天用户增长趋势</div></div>
      </div>
      <div class="l1-body">
        <div class="flex gap-12 wrap mb-12">
          <span class="tag blue">新增注册</span><span class="tag outline">按天统计</span>
        </div>
        <BarChart :data="trendData" :w="540" :h="190" />
      </div>
    </section>
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico ico-info"
          ><svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M21.21 15.89A10 10 0 1 1 8 2.83M22 12A10 10 0 0 0 12 2v10z" /></svg
        ></span>
        <div><div class="l1-title">全校成绩分布</div></div>
      </div>
      <div class="l1-body">
        <DonutChart :data="gradeDist" center-label="人次" :size="160" />
      </div>
    </section>
  </div>

  <!-- 院系对比 + 进度表 -->
  <section class="l1-card">
    <div class="l1-head">
      <span class="l1-ico ico-violet"
        ><svg
          viewBox="0 0 24 24"
          width="17"
          height="17"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18z" /></svg
      ></span>
      <div><div class="l1-title">课程运营数据</div></div>
      <div class="l1-extra">
        <router-link to="/admin/analytics" class="btn btn-plain btn-sm">查看详情</router-link>
      </div>
    </div>
    <div class="l1-body">
      <CollapsePanel title="课程分类分布" icon="chart" :open="true">
        <BarChart :data="deptData" :horizontal="true" :w="540" :h="190" />
      </CollapsePanel>
      <CollapsePanel title="热门课程 TOP5（按选课人数）" icon="list" :open="false" flush>
        <div v-if="topCourses.length" class="tbl-wrap">
          <table class="tbl">
            <thead>
              <tr>
                <th>课程</th>
                <th>课程号</th>
                <th>分类</th>
                <th>选课人数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(p, i) in topCourses" :key="p.courseId || i">
                <td class="cell-main">{{ p.courseName }}</td>
                <td class="mono">{{ p.courseCode || '—' }}</td>
                <td>
                  <span class="tag outline">{{ p.category || '未分类' }}</span>
                </td>
                <td class="num">{{ p.enrollmentCount ?? 0 }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <el-empty v-else description="暂无热门课程数据" :image-size="80" />
      </CollapsePanel>
    </div>
  </section>
</template>

<script setup lang="ts">
import BarChart from '@/components/charts/BarChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'

defineProps({
  trendData: { type: Array, default: () => [] },
  gradeDist: { type: Array, default: () => [] },
  deptData: { type: Array, default: () => [] },
  topCourses: { type: Array, default: () => [] },
})
</script>

<style scoped>
.ico-ok {
  background: var(--ok-soft);
  color: var(--ok);
}
.ico-info {
  background: var(--info-soft);
  color: var(--info);
}
.ico-violet {
  background: var(--violet-soft);
  color: var(--violet);
}
</style>
