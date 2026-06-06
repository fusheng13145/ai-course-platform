<template>
  <div>
    <div class="table-toolbar"><div class="toolbar-left"><el-button type="primary"><el-icon><Plus /></el-icon>添加题目</el-button><el-button>AI生成</el-button></div><div class="toolbar-right"><el-input placeholder="搜索题目" clearable style="width:220px" /></div></div>
    <div class="content-card">
      <el-table :data="list" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="80"><template #default="{row}"><el-tag size="small">{{ row.type }}</el-tag></template></el-table-column>
        <el-table-column prop="difficulty" label="难度" width="80"><template #default="{row}"><el-rate :model-value="row.difficulty === 'EASY' ? 1 : row.difficulty === 'MEDIUM' ? 2 : 3" disabled size="small" /></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="{row}"><el-button text type="primary">编辑</el-button><el-button text type="danger">删除</el-button></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup>
const list = [
  { id: 1, content: "Python中列表和元组的主要区别是什么？", type: "简答", difficulty: "EASY" },
  { id: 2, content: "什么是机器学习中的过拟合？如何避免？", type: "简答", difficulty: "MEDIUM" }
]
</script>