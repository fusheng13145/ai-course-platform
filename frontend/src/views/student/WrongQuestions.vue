<template>
  <div>
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-icon" style="background:#f56c6c"><el-icon :size="24"><WarningFilled /></el-icon></div><div class="stat-info"><h3>12</h3><p>错题总数</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#e6a23c"><el-icon :size="24"><Reading /></el-icon></div><div class="stat-info"><h3>5</h3><p>待复习</p></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:#67c23a"><el-icon :size="24"><Select /></el-icon></div><div class="stat-info"><h3>7</h3><p>已掌握</p></div></div>
    </div>
    <div class="content-card">
      <div v-for="q in questions" :key="q.id" style="padding:16px 0;border-bottom:1px solid #f0f0f0">
        <div style="display:flex;justify-content:space-between;margin-bottom:8px">
          <span style="font-weight:600">{{ q.content }}</span>
          <el-tag :type="q.reviewed ? 'success' : 'warning'" size="small">{{ q.reviewed ? '已掌握' : '待复习' }}</el-tag>
        </div>
        <div style="font-size:13px;color:#909399">正确答案：{{ q.correctAnswer }} | 你的答案：<span style="color:#f56c6c">{{ q.yourAnswer }}</span></div>
        <el-button v-if="!q.reviewed" text type="primary" size="small" style="margin-top:4px" @click="q.reviewed = true">标记已掌握</el-button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref } from 'vue'
const questions = ref([
  { id: 1, content: "Python中列表和元组的主要区别？", correctAnswer: "列表可变，元组不可变", yourAnswer: "都可以变", reviewed: false },
  { id: 2, content: "什么是时间复杂度？", correctAnswer: "算法执行时间与数据规模的关系", yourAnswer: "代码运行的时间", reviewed: false },
  { id: 3, content: "HTTP状态码404表示什么？", correctAnswer: "资源未找到", yourAnswer: "服务器错误", reviewed: true }
])
</script>