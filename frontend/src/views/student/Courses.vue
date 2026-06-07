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
import { ref, computed, onMounted } from 'vue'
import courseApi from '@/api/course'

const search = ref('')
const category = ref('')
const courseList = ref([])

const filteredList = computed(() => {
  let list = courseList.value
  if (category.value) list = list.filter(c => c.category === category.value)
  if (search.value) { const q = search.value.toLowerCase(); list = list.filter(c => c.name.toLowerCase().includes(q)) }
  return list
})

onMounted(async () => {
  try {
    const res = await courseApi.getCourseList()
    courseList.value = res.data?.list || res.data || res || []
  } catch (e) {
    console.error('Failed to load courses:', e)
  }
})
</script>
