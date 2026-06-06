<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><Reading /></el-icon></div><div class="stat-info"><h3>3</h3><p>在学课程</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><TrophyBase /></el-icon></div><div class="stat-info"><h3>85</h3><p>平均分</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Calendar /></el-icon></div><div class="stat-info"><h3>7</h3><p>连续学习天数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><WarningFilled /></el-icon></div><div class="stat-info"><h3>2</h3><p>待办事项</p></div></div>
    </div>
    <div class="dashboard-grid">
      <div class="chart-card"><div class="chart-title">本周学习时长</div><v-chart :option="barOption" style="height:280px" autoresize /></div>
      <div class="chart-card">
        <div class="chart-title">课程进度</div>
        <div v-for="c in courses" :key="c.id" style="margin-bottom:16px">
          <div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:4px"><span>{{ c.name }}</span><span>{{ c.progress }}%</span></div>
          <el-progress :percentage="c.progress" :color="c.color" :stroke-width="8" />
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
const barOption = { xAxis: { type: "category", data: ["周一","周二","周三","周四","周五","周六","周日"] }, yAxis: { type: "value" }, series: [{ type: "bar", data: [2.5, 3.0, 1.8, 4.0, 2.2, 3.5, 1.0], itemStyle: { borderRadius: [4,4,0,0], color: "#409eff" } }], tooltip: { trigger: "axis" }, grid: { left: 40, right: 20, bottom: 30, top: 10 } }
const courses = [{ id: 1, name: "Python编程入门", progress: 65, color: "#409eff" }, { id: 2, name: "机器学习基础", progress: 30, color: "#67c23a" }, { id: 3, name: "数据分析实战", progress: 80, color: "#e6a23c" }]
</script>