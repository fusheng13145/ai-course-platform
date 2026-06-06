<template>
  <div>
    <div class="table-toolbar"><div class="toolbar-left"><el-button type="primary"><el-icon><Plus /></el-icon>发送通知</el-button></div></div>
    <div class="content-card">
      <el-table :data="list" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="type" label="类型" width="100"><template #default="{row}"><el-tag size="small">{{ row.type }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="发送时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right"><template #default="{row}"><el-button text type="danger">删除</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
const list = [{ id: 1, title: "系统升级通知", type: "系统", createdAt: "2026-06-05 10:00" }, { id: 2, title: "新课程上线：深度学习", type: "课程", createdAt: "2026-06-04 14:30" }]
</script>