<template>
  <div>
    <div class="content-card" style="margin-bottom:20px">
      <h3 style="margin:0 0 16px">我的学习路径</h3>
      <el-timeline>
        <el-timeline-item v-for="(item, i) in pathNodes" :key="item.id" :type="item.completed ? 'success' : item.current ? 'primary' : 'info'" :timestamp="item.time">
          {{ item.title }}
        </el-timeline-item>
      </el-timeline>
    </div>
    <div class="content-card">
      <h4 style="margin:0 0 12px">推荐路径</h4>
      <el-row :gutter="16">
        <el-col :span="8" v-for="p in paths" :key="p.id" style="margin-bottom:12px">
          <el-card shadow="hover"><h4 style="margin:0 0 8px">{{ p.name }}</h4><p style="color:#909399;font-size:13px;margin:0">{{ p.count }} 个知识点</p></el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>
<script setup>
const pathNodes = [
  { id: 1, title: "Python基础语法", time: "2026-05-20", completed: true },
  { id: 2, title: "数据结构与算法入门", time: "2026-05-25", current: true },
  { id: 3, title: "面向对象编程", time: "即将开始" }
]
const paths = [{ id: 1, name: "Python开发", count: 12 }, { id: 2, name: "数据分析", count: 8 }, { id: 3, name: "AI入门", count: 10 }]
</script>