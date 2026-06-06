<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><User /></el-icon></div><div class="stat-info"><h3>128</h3><p>总学生</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><TrendCharts /></el-icon></div><div class="stat-info"><h3>85%</h3><p>平均分</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Document /></el-icon></div><div class="stat-info"><h3>12</h3><p>待批改</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><WarningFilled /></el-icon></div><div class="stat-info"><h3>5</h3><p>低分预警</p></div></div>
    </div>
    <div class="dashboard-grid">
      <div class="chart-card"><div class="chart-title">成绩分布</div><v-chart :option="barOption" style="height:280px" autoresize /></div>
      <div class="chart-card"><div class="chart-title">通过率</div><v-chart :option="pieOption" style="height:280px" autoresize /></div>
    </div>
  </div>
</template>
<script setup>
const barOption = { xAxis: { type: "category", data: ["90-100", "80-89", "70-79", "60-69", "60以下"] }, yAxis: { type: "value" }, series: [{ type: "bar", data: [25, 40, 35, 20, 8], itemStyle: { borderRadius: [4,4,0,0], color: "#409eff" } }], tooltip: { trigger: "axis" }, grid: { left: 40, right: 20, bottom: 30, top: 10 } }
const pieOption = { tooltip: { trigger: "item" }, series: [{ type: "pie", radius: ["40%","70%"], data: [{name:"通过",value:120},{name:"未通过",value:8}], label: { show: true, formatter: "{b}: {d}%" } }] }
</script>