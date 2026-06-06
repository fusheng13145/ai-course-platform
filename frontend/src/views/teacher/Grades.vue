<template>
  <div>
    <div class="content-card">
      <el-table :data="list" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="studentName" label="学生姓名" />
        <el-table-column prop="courseName" label="课程" />
        <el-table-column prop="examTitle" label="考试" />
        <el-table-column prop="score" label="成绩" width="80"><template #default="{row}"><el-tag :type="row.score >= 60 ? 'success' : 'danger'" size="small">{{ row.score }}</el-tag></template></el-table-column>
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <el-table-column label="操作" width="160" fixed="right"><template #default="{row}"><el-button text type="primary">批改</el-button><el-button text type="primary">详情</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
const list = [
  { id: 1, studentName: "张三", courseName: "Python编程入门", examTitle: "期末考试", score: 85, submitTime: "2026-06-01 14:30" },
  { id: 2, studentName: "李四", courseName: "机器学习基础", examTitle: "期中考试", score: 58, submitTime: "2026-05-28 10:00" }
]
</script>