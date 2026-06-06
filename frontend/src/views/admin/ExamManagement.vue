<template>
  <div>
    <div class="table-toolbar"><div class="toolbar-left"><el-button type="primary" @click="$router.push('/teacher/exams')">管理考试</el-button></div></div>
    <div class="content-card">
      <el-table :data="list" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="考试名称" min-width="150" />
        <el-table-column prop="courseName" label="所属课程" />
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column prop="duration" label="时长(分钟)" width="100" />
        <el-table-column prop="isActive" label="状态" width="80"><template #default="{row}"><el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}"><el-button text type="primary">查看</el-button><el-button text type="danger">删除</el-button></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
const list = [
  { id: 1, title: "Python期末考试", courseName: "Python编程入门", totalScore: 100, duration: 120, isActive: true },
  { id: 2, title: "机器学习期中", courseName: "机器学习基础", totalScore: 100, duration: 90, isActive: true }
]
</script>