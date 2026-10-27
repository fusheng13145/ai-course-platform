<template>
  <div class="ai-page fade-in">
    <!-- 顶部课程选择器 -->
    <div class="page-head">
      <div class="page-title">
        <el-icon :size="20" class="ttl-icon"><MagicStick /></el-icon>
        <div>
          <h3>AI 教学中心</h3>
          <p>智能答疑、学情分析、作文批改与学习路径规划</p>
        </div>
      </div>
      <el-select
        v-model="courseId"
        placeholder="请选择课程"
        filterable
        clearable
        style="width: 240px"
        :loading="courseLoading"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>

    <div v-loading="loading">
      <el-alert v-if="!courseId" title="请选择课程" type="info" :closable="false" show-icon>
        <template #default>选择一门课程后，即可使用各项 AI 教学能力。</template>
      </el-alert>

      <template v-else>
        <!-- P4-MIG-07：el-tabs→TabGroup；pane 内容 v-show（等价 el-tabs 全挂载隐藏语义） -->
        <TabGroup
          :tabs="AI_TABS"
          :active="activeTab"
          variant="line"
          class="ai-tabs"
          @change="activeTab = $event"
        />
          <!-- 智能答疑 -->
          <div v-show="activeTab === 'qa'">
            <AcQaPanel :course-id="courseId" />
          </div>

          <!-- 薄弱点分析 -->
          <div v-show="activeTab === 'weak'">
            <AcWeakPanel :course-id="courseId" />
          </div>

          <!-- 学习建议 -->
          <div v-show="activeTab === 'advice'">
            <AcAdvicePanel :course-id="courseId" />
          </div>

          <!-- 作文批改 -->
          <div v-show="activeTab === 'essay'">
            <AcEssayPanel />
          </div>

          <!-- 学习路径 -->
          <div v-show="activeTab === 'path'">
            <AcPathPanel :course-id="courseId" />
          </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'
import courseApi from '@/api/course'
import AcQaPanel from './components/AcQaPanel.vue'
import AcWeakPanel from './components/AcWeakPanel.vue'
import AcAdvicePanel from './components/AcAdvicePanel.vue'
import AcEssayPanel from './components/AcEssayPanel.vue'
import AcPathPanel from './components/AcPathPanel.vue'
import TabGroup from '@/components/ui/TabGroup.vue'
const AI_TABS = [
  { key: 'qa', label: '智能答疑' },
  { key: 'weak', label: '薄弱点分析' },
  { key: 'advice', label: '学习建议' },
  { key: 'essay', label: '作文批改' },
  { key: 'path', label: '学习路径' },
]

const courseLoading = ref(false)
const courseOptions = ref([])
const courseId = ref<string | number>('')
const loading = ref(false)
const activeTab = ref('qa')

/* ---------- 课程选择器 ---------- */
async function loadCourses() {
  courseLoading.value = true
  try {
    courseOptions.value = await courseApi.getAllMyCourses()
    if (courseOptions.value.length && !courseId.value) courseId.value = courseOptions.value[0].id
  } catch (e) {
    logger.error('加载课程列表失败', e)
  } finally {
    courseLoading.value = false
  }
}

/** 课程上下文：驾驶舱子路由（/teacher/courses/:id/ai）或统一路由 ?courseId 预选 */
const route = useRoute()
onMounted(async () => {
  const cid = route.params.id || route.query.courseId
  if (cid) courseId.value = Number(cid)
  await loadCourses()
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: var(--ac-spacing-lg);
  flex-wrap: wrap;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ttl-icon {
  color: var(--ac-primary);
}
.page-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 640;
  color: var(--ac-text-primary);
}
.page-title p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}

/* P4-MIG-07：原 :deep(.el-tabs__header) 间距规则平移到 TabGroup 根 */
.ai-tabs {
  margin-bottom: var(--ac-spacing-md);
}

@media (max-width: 1100px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
