<template>
  <div>
    <div class="search-form">
      <el-input v-model="search" placeholder="搜索考试" clearable style="width:220px" />
    </div>
    <el-row :gutter="20">
      <el-col :span="8" v-for="exam in filteredList" :key="exam.id" style="margin-bottom:20px">
        <el-card shadow="hover">
          <h4 style="margin:0 0 8px">{{ exam.title }}</h4>
          <div style="font-size:13px;color:#909399;margin-bottom:12px">
            <p style="margin:2px 0">{{ exam.courseName }} · {{ exam.totalScore }}分</p>
            <p style="margin:2px 0">时长：{{ exam.duration }}分钟</p>
          </div>
          <el-button type="primary" size="small" @click="startExam(exam)" :disabled="exam.completed">{{ exam.completed ? '已完成' : '开始考试' }}</el-button>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>
<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
const search = ref('')
const examList = ref([
  { id: 1, title: "Python期末考试", courseName: "Python编程入门", totalScore: 100, duration: 120, completed: false },
  { id: 2, title: "机器学习期中", courseName: "机器学习基础", totalScore: 100, duration: 90, completed: true }
])
const filteredList = computed(() => search.value ? examList.value.filter(e => e.title.includes(search.value)) : examList.value)
function startExam(exam) { ElMessage.info("开始考试：" + exam.title + "（功能开发中）") }
</script>