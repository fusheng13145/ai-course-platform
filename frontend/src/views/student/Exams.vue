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
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import examApi from '@/api/exam'
import { useRouter } from 'vue-router'

const router = useRouter()
const search = ref('')
const examList = ref([])

const filteredList = computed(() => {
  if (!search.value) return examList.value
  return examList.value.filter(e => e.title?.includes(search.value))
})

onMounted(async () => {
  try {
    const res = await examApi.getExamPaperList()
    examList.value = res.data?.list || res.data || res || []
  } catch (e) {
    console.error('Failed to load exams:', e)
  }
})

async function startExam(exam) {
  try {
    const res = await examApi.startExam(exam.id)
    const sessionId = res.data?.id || res?.id
    if (sessionId) {
      ElMessage.success('考试已开始')
    }
  } catch (e) {
    ElMessage.info('开始考试：' + exam.title + '（功能开发中）')
  }
}
</script>