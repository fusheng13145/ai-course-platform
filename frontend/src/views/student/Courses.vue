<template>
  <div>
    <el-tabs v-model="activeTab" style="margin-bottom:20px">
      <el-tab-pane label="我的课程" name="my">
        <div class="search-form" style="margin-bottom:16px">
          <el-input v-model="mySearch" placeholder="搜索我的课程" clearable style="width:280px" />
        </div>
        <el-row :gutter="20" v-if="myFilteredList.length > 0">
          <el-col :span="8" v-for="e in myFilteredList" :key="e.id" style="margin-bottom:20px">
            <el-card shadow="hover" :body-style="{ padding: '0' }">
              <div :style="{ background: getCourseColor(e.category), height: '100px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '18px', fontWeight: '600' }">{{ e.name?.charAt(0) || 'C' }}</div>
              <div style="padding:16px">
                <h4 style="margin:0 0 8px;font-size:16px">{{ e.name }}</h4>
                <div style="margin-bottom:8px">
                  <span style="font-size:12px;color:#909399">学习进度：</span>
                  <el-progress :percentage="e.progress" :stroke-width="6" style="display:inline-block;width:120px" />
                </div>
                <div style="display:flex;justify-content:space-between;align-items:center">
                  <el-tag :type="e.approvalStatus === 'APPROVED' ? 'success' : 'warning'" size="small">{{ e.approvalStatus === 'APPROVED' ? '已通过' : '待审核' }}</el-tag>
                  <el-button text type="primary" @click="$router.push(`/student/courses/${e.courseId}`)">继续学习</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="您还没有加入任何课程，去选课中心看看吧">
          <el-button type="primary" @click="activeTab = 'all'">去选课</el-button>
        </el-empty>
      </el-tab-pane>
      <el-tab-pane label="选课中心" name="all">
        <div class="search-form">
          <el-input v-model="search" placeholder="搜索课程" clearable style="width:280px" />
          <el-select v-model="category" placeholder="分类" clearable style="width:140px">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
          <el-select v-model="enrollFilter" placeholder="选课状态" clearable style="width:120px;margin-left:8px">
            <el-option label="已选课" value="enrolled" />
            <el-option label="未选课" value="notEnrolled" />
          </el-select>
        </div>
        <el-row :gutter="20">
          <el-col :span="8" v-for="c in filteredList" :key="c.id" style="margin-bottom:20px">
            <el-card shadow="hover" :body-style="{ padding: '0' }">
              <div :style="{ background: getCourseColor(c.category), height: '120px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '18px', fontWeight: '600' }">{{ c.name?.charAt(0) || 'C' }}</div>
              <div style="padding:16px">
                <h4 style="margin:0 0 8px;font-size:16px">{{ c.name }}</h4>
                <p style="color:#909399;font-size:13px;margin:0 0 12px;height:40px;overflow:hidden">{{ c.description || '暂无简介' }}</p>
                <div style="display:flex;justify-content:space-between;align-items:center">
                  <div>
                    <el-tag size="small">{{ c.category || '未分类' }}</el-tag>
                    <el-tag v-if="c.isEnrolled" type="success" size="small" style="margin-left:4px">已选</el-tag>
                  </div>
                  <el-button text type="primary" @click="$router.push(`/student/courses/${c.id}`)">{{ c.isEnrolled ? '继续学习' : '查看详情' }}</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-if="!loading && filteredList.length === 0" description="暂无可选课程" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import courseApi from '@/api/course'
import enrollmentApi from '@/api/enrollment'

const activeTab = ref('my')
const search = ref('')
const category = ref('')
const enrollFilter = ref('')
const mySearch = ref('')
const courseList = ref([])
const myEnrollments = ref([])
const categories = ref(['编程开发', '人工智能', '数据分析', '设计创意', '其他'])
const loading = ref(false)

const categoryColors = {
  '编程开发': 'linear-gradient(135deg,#667eea,#764ba2)',
  '人工智能': 'linear-gradient(135deg,#f093fb,#f5576c)',
  '数据分析': 'linear-gradient(135deg,#4facfe,#00f2fe)',
  '设计创意': 'linear-gradient(135deg,#43e97b,#38f9d7)',
  '其他': 'linear-gradient(135deg,#fa709a,#fee140)'
}

function getCourseColor(cat) {
  return categoryColors[cat] || categoryColors['其他']
}

// 已选课程ID集合
const enrolledCourseIds = computed(() => {
  return new Set(myEnrollments.value.filter(e => e.isActive).map(e => e.courseId))
})

// 已选课程列表（带课程信息）
const myCoursesList = computed(() => {
  const courseMap = {}
  courseList.value.forEach(c => { courseMap[c.id] = c })
  return myEnrollments.value.map(e => {
    const course = courseMap[e.courseId] || {}
    return {
      ...e,
      name: course.name || '未知课程',
      category: course.category || '其他',
      progress: e.progress ? Math.round(Number(e.progress) * 100) : 0
    }
  }).filter(e => e.isActive)
})

const myFilteredList = computed(() => {
  if (!mySearch.value) return myCoursesList.value
  const q = mySearch.value.toLowerCase()
  return myCoursesList.value.filter(e => e.name?.toLowerCase().includes(q))
})

// 选课中心列表（标记已选/未选）
const filteredList = computed(() => {
  let list = courseList.value.filter(c => c.status === 'ACTIVE' && c.approvalStatus === 'APPROVED')
  
  // 标记已选课状态
  list = list.map(c => ({
    ...c,
    isEnrolled: enrolledCourseIds.value.has(c.id)
  }))
  
  if (category.value) list = list.filter(c => c.category === category.value)
  if (search.value) { const q = search.value.toLowerCase(); list = list.filter(c => c.name?.toLowerCase().includes(q)) }
  if (enrollFilter.value === 'enrolled') list = list.filter(c => c.isEnrolled)
  if (enrollFilter.value === 'notEnrolled') list = list.filter(c => !c.isEnrolled)
  
  return list
})

onMounted(async () => {
  loading.value = true
  try {
    const [courseRes, enrollRes] = await Promise.all([
      courseApi.getCourseList(),
      enrollmentApi.getMyEnrollments()
    ])
    const list = courseRes.data?.list || courseRes.data || courseRes || []
    courseList.value = list
    myEnrollments.value = enrollRes.data?.list || enrollRes.data || enrollRes || []
    
    // Extract unique categories from courses
    const cats = [...new Set(list.map(c => c.category).filter(Boolean))]
    if (cats.length > 0) categories.value = cats
  } catch (e) {
    console.error('Failed to load courses:', e)
  } finally {
    loading.value = false
  }
})
</script>
