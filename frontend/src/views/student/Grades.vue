<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#409eff"><el-icon :size="24"><TrophyBase /></el-icon></div><div class="stat-info"><h3>85</h3><p>平均分</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><TrendCharts /></el-icon></div><div class="stat-info"><h3>第3名</h3><p>班级排名</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Document /></el-icon></div><div class="stat-info"><h3>8</h3><p>已完成考试</p></div></div>
    </div>
    <div class="content-card">
      <el-table :data="list" stripe style="width:100%">
        <el-table-column prop="examTitle" label="考试" min-width="150" />
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="score" label="成绩" width="80"><template #default="{row}"><el-tag :type="row.score >= 60 ? 'success' : 'danger'" size="small">{{ row.score }}</el-tag></template></el-table-column>
        <el-table-column prop="totalScore" label="总分" width="60" />
        <el-table-column prop="submitTime" label="时间" width="160" />
        <el-table-column label="操作" width="100" fixed="right"><template #default><el-button text type="primary">详情</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
const list = [
  { id: 1, examTitle: "Python期末考试", courseName: "Python编程入门", score: 92, totalScore: 100, submitTime: "2026-06-01 14:30" },
  { id: 2, examTitle: "机器学习期中", courseName: "机器学习基础", score: 78, totalScore: 100, submitTime: "2026-05-28 10:00" },
  { id: 3, examTitle: "数据结构测验", courseName: "数据结构", score: 45, totalScore: 100, submitTime: "2026-05-20 09:00" }
]
</script>