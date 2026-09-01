<template>
  <div>
    <!-- 图表区域第一行：用户趋势 + 课程分布 -->
    <div class="charts-grid charts-2col animate-in d2">
      <div class="chart-card">
        <div class="chart-title">用户注册趋势</div>
        <v-chart v-if="hasUserTrend" :option="userTrendOption" style="height: 340px" autoresize />
        <el-empty v-else description="暂无用户注册趋势数据" :image-size="80" />
      </div>
      <div class="chart-card">
        <div class="chart-title">课程类别分布</div>
        <v-chart
          v-if="hasCategory"
          :option="categoryPieOption"
          style="height: 340px"
          autoresize
        />
        <el-empty v-else description="暂无课程类别数据" :image-size="80" />
      </div>
    </div>

    <!-- 学习时长热力图 -->
    <div class="chart-card full-width animate-in d2">
      <div class="chart-title">学习时长热力图</div>
      <template v-if="hasHeatmap">
        <div class="heatmap-container">
          <v-chart :option="heatmapOption" style="height: 200px" autoresize />
          <div class="heatmap-legend">
            <span>少</span>
            <div class="legend-colors">
              <span
                v-for="(color, i) in heatmapColors"
                :key="i"
                :style="{ background: color }"
              ></span>
            </div>
            <span>多</span>
          </div>
        </div>
      </template>
      <el-empty v-else description="暂无学习时长热力图数据" :image-size="80" />
    </div>

    <!-- 图表区域第二行：角色活跃度 + 成绩分布 -->
    <div class="charts-grid charts-2col">
      <div class="chart-card">
        <div class="chart-title">各角色活跃度对比</div>
        <v-chart
          v-if="hasRoleActivity"
          :option="roleActivityOption"
          style="height: 320px"
          autoresize
        />
        <el-empty v-else description="暂无角色活跃度数据" :image-size="80" />
      </div>
      <div class="chart-card">
        <div class="chart-title">成绩分布直方图</div>
        <v-chart v-if="hasGradeDist" :option="gradeDistOption" style="height: 320px" autoresize />
        <el-empty v-else description="暂无成绩分布数据" :image-size="80" />
      </div>
    </div>

    <!-- 热门课程TOP10 -->
    <div class="chart-card full-width animate-in d3">
      <div class="chart-title">热门课程 TOP10</div>
      <v-chart v-if="hasTopCourses" :option="topCoursesOption" style="height: 420px" autoresize />
      <el-empty v-else description="暂无热门课程数据" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 数据分析图表区域
 * <p>聚合展示六类图表（用户趋势/课程分布/学习热力图/角色活跃度/成绩分布/热门课程 TOP10），
 * 配置对象与空态标志均由父组件（useAnalytics）注入；无数据时展示 el-empty 降级。</p>
 */
defineProps({
  userTrendOption: { type: Object, required: true },
  categoryPieOption: { type: Object, required: true },
  heatmapOption: { type: Object, required: true },
  heatmapColors: { type: Array, default: () => [] },
  roleActivityOption: { type: Object, required: true },
  gradeDistOption: { type: Object, required: true },
  topCoursesOption: { type: Object, required: true },
  hasUserTrend: { type: Boolean, default: false },
  hasCategory: { type: Boolean, default: false },
  hasHeatmap: { type: Boolean, default: false },
  hasRoleActivity: { type: Boolean, default: false },
  hasGradeDist: { type: Boolean, default: false },
  hasTopCourses: { type: Boolean, default: false },
})
</script>

<style scoped>
.charts-grid {
  display: grid;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.charts-2col {
  grid-template-columns: repeat(2, 1fr);
}
.chart-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
}
.chart-card:hover {
  box-shadow: var(--ac-shadow-md);
}
.chart-card.full-width {
  grid-column: 1 / -1;
}
.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}
.heatmap-container {
  position: relative;
}
.heatmap-legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  font-size: 12px;
  color: var(--ac-text-muted);
}
.legend-colors {
  display: flex;
  gap: 3px;
}
.legend-colors span {
  width: 16px;
  height: 12px;
  border-radius: 2px;
}

@media (max-width: 1024px) {
  .charts-2col {
    grid-template-columns: 1fr;
  }
  .chart-card.full-width {
    grid-column: 1;
  }
}
</style>
