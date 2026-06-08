<template>
  <div>
    <div class="content-card" style="margin-bottom:var(--ac-spacing-md)">
      <h3 class="section-title">知识图谱</h3>
      <div class="graph-container">
        <el-empty description="知识图谱可视化" />
        <p class="graph-tip">需集成图数据库或前端图谱库</p>
      </div>
    </div>
    <div class="content-card">
      <h4 class="section-subtitle">知识点列表</h4>
      <div class="tag-list">
        <el-tag v-for="kp in points" :key="kp.id" class="knowledge-tag" closable>{{ kp.name }}</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
const points = [
  { id: 1, name: "变量" }, { id: 2, name: "数据类型" }, { id: 3, name: "条件判断" },
  { id: 4, name: "循环" }, { id: 5, name: "函数" }, { id: 6, name: "列表" }
]
</script>

<style scoped>
.section-title {
  margin: 0 0 var(--ac-spacing-md);
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.section-subtitle {
  margin: 0 0 var(--ac-spacing-md);
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.graph-container {
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 40px;
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--ac-text-secondary);
}

.graph-tip {
  font-size: 13px;
  margin-top: 8px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.knowledge-tag {
  margin: 0;
}
</style>