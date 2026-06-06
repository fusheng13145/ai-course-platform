<template>
  <div>
    <div class="content-card" style="margin-bottom:20px">
      <h3 style="margin:0 0 16px">知识图谱</h3>
      <div style="border:1px solid #e6e6e6;border-radius:8px;padding:40px;min-height:300px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#909399">
        <el-empty description="知识图谱可视化" />
        <p style="font-size:13px">需集成图数据库或前端图谱库</p>
      </div>
    </div>
    <div class="content-card">
      <h4 style="margin:0 0 12px">知识点列表</h4>
      <el-tag v-for="kp in points" :key="kp.id" style="margin:4px" closable>{{ kp.name }}</el-tag>
    </div>
  </div>
</template>
<script setup>
const points = [
  { id: 1, name: "变量" }, { id: 2, name: "数据类型" }, { id: 3, name: "条件判断" },
  { id: 4, name: "循环" }, { id: 5, name: "函数" }, { id: 6, name: "列表" }
]
</script>