<template>
  <div>
    <div class="search-form">
      <el-input v-model="search" placeholder="搜索课程" clearable style="width:280px" />
      <el-select v-model="category" placeholder="分类" clearable style="width:140px"><el-option label="编程开发" value="编程开发" /><el-option label="人工智能" value="人工智能" /><el-option label="数据分析" value="数据分析" /></el-select>
    </div>
    <el-row :gutter="20">
      <el-col :span="8" v-for="c in filteredList" :key="c.id" style="margin-bottom:20px">
        <el-card shadow="hover" :body-style="{ padding: '0' }">
          <div style="background:linear-gradient(135deg,#667eea,#764ba2);height:120px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:18px;font-weight:600">{{ c.name.charAt(0) }}</div>
          <div style="padding:16px">
            <h4 style="margin:0 0 8px;font-size:16px">{{ c.name }}</h4>
            <p style="color:#909399;font-size:13px;margin:0 0 12px;height:40px;overflow:hidden">{{ c.description }}</p>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <el-tag size="small">{{ c.category }}</el-tag>
              <el-button text type="primary" @click="$router.push(`/student/courses/${c.id}`)">查看详情</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script setup>
import { ref, computed } from 'vue'
const search = ref('')
const category = ref('')
const courseList = ref([
  { id: 1, name: "Python编程入门", category: "编程开发", description: "零基础学习Python编程，从变量到面向对象" },
  { id: 2, name: "机器学习基础", category: "人工智能", description: "机器学习核心算法与实战" },
  { id: 3, name: "数据分析实战", category: "数据分析", description: "使用Python进行数据分析与可视化" }
])
const filteredList = computed(() => {
  let list = courseList.value
  if (category.value) list = list.filter(c => c.category === category.value)
  if (search.value) { const q = search.value.toLowerCase(); list = list.filter(c => c.name.toLowerCase().includes(q)) }
  return list
})
</script>