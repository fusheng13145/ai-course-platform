<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><User /></el-icon></div><div class="stat-info"><h3>1,256</h3><p>活跃用户</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><Reading /></el-icon></div><div class="stat-info"><h3>86</h3><p>课程总数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Document /></el-icon></div><div class="stat-info"><h3>92%</h3><p>完成率</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><TrendCharts /></el-icon></div><div class="stat-info"><h3>+15%</h3><p>增长趋势</p></div></div>
    </div>
    <div class="dashboard-grid">
      <div class="chart-card"><div class="chart-title">学习趋势</div><v-chart :option="lineOption" style="height:280px" autoresize /></div>
      <div class="chart-card"><div class="chart-title">课程类别分布</div><v-chart :option="pieOption" style="height:280px" autoresize /></div>
    </div>
  </div>
</template>
<script setup>
const lineOption = { xAxis: { type: "category", data: ["1月","2月","3月","4月","5月","6月"] }, yAxis: { type: "value" }, series: [{ data: [820, 932, 901, 934, 1290, 1330], type: "line", smooth: true }], tooltip: { trigger: "axis" } }
const pieOption = { tooltip: { trigger: "item" }, series: [{ type: "pie", radius: ["40%","70%"], data: [{name:"编程",value:30},{name:"AI",value:25},{name:"数据",value:20},{name:"设计",value:15},{name:"其他",value:10}] }] }
</script>