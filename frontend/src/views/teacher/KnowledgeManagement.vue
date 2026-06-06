<template>
  <div>
    <div class="content-card" style="margin-bottom:20px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h3 style="margin:0">知识图谱</h3>
        <el-button type="primary"><el-icon><Plus /></el-icon>添加知识点</el-button>
      </div>
      <div style="border:1px solid #e6e6e6;border-radius:8px;padding:20px;min-height:300px;display:flex;align-items:center;justify-content:center;color:#909399">
        <el-empty description="知识图谱功能需集成图数据库" />
      </div>
    </div>
    <div class="content-card">
      <el-table :data="knowledgePoints" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="知识点名称" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column label="操作" width="200" fixed="right"><template #default><el-button text type="primary">编辑</el-button><el-button text type="danger">删除</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
const knowledgePoints = ref([{ id: 1, name: "变量与数据类型", type: "基础" }, { id: 2, name: "条件判断", type: "基础" }, { id: 3, name: "循环结构", type: "进阶" }])
import { ref } from 'vue'
</script>