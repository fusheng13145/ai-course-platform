<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><Reading /></el-icon></div><div class="stat-info"><h3>5</h3><p>我的课程</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><User /></el-icon></div><div class="stat-info"><h3>128</h3><p>学生总数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Document /></el-icon></div><div class="stat-info"><h3>12</h3><p>待批改</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><TrendCharts /></el-icon></div><div class="stat-info"><h3>92%</h3><p>平均完成率</p></div></div>
    </div>
    <div class="dashboard-grid">
      <div class="chart-card"><div class="chart-title">课程学习趋势</div><v-chart :option="lineOption" style="height:280px" autoresize /></div>
      <div class="chart-card"><div class="chart-title">近期动态</div>
        <div v-for="a in activities" :key="a.id" style="padding:8px 0;border-bottom:1px solid #f0f0f0;font-size:13px">
          <span style="color:#606266">{{ a.text }}</span><span style="color:#c0c4cc;float:right;font-size:12px">{{ a.time }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
const lineOption = { xAxis: { type: "category", data: ["第1周","第2周","第3周","第4周","第5周","第6周"] }, yAxis: { type: "value" }, series: [{ data: [30, 45, 52, 68, 75, 90], type: "line", smooth: true, areaStyle: {} }], tooltip: { trigger: "axis" }, grid: { left: 40, right: 20, bottom: 30, top: 10 } }
const activities = [{ id: 1, text: "张三 提交了 Python作业", time: "10分钟前" }, { id: 2, text: "李四 完成了 机器学习期中考试", time: "1小时前" }, { id: 3, text: "王五 加入了 Python编程入门", time: "2小时前" }]
</script>