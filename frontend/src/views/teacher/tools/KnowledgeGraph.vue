<template>
  <div class="kg-page fade-in">
    <!-- 顶部课程选择器 -->
    <PageHeader title="知识图谱" subtitle="管理课程知识点、AI 智能抽取并可视化知识关系" icon="link">
      <el-button @click="openExtractDialog">
        <el-icon><MagicStick /></el-icon>AI 抽取知识点
      </el-button>
      <el-select
        v-model="courseId"
        placeholder="请选择课程"
        filterable
        clearable
        class="course-select"
        :loading="courseLoading"
        @change="onCourseChange"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </PageHeader>

    <div v-loading="loading">
      <el-alert v-if="!courseId" title="请选择课程" type="info" :closable="false" show-icon>
        <template #default>选择一门课程后，即可查看知识点概览、管理知识点并生成图谱。</template>
      </el-alert>

      <template v-else>
        <KgOverviewStats :overview="overview" :pct="pct" />

        <KgListGraph
          :points="points"
          :point-loading="pointLoading"
          :diff-type="diffType"
          :diff-text="diffText"
          :graph-loading="graphLoading"
          :graph-size="graphSize"
          :graph-nodes="graphNodes"
          :graph-links="graphLinks"
          :node-pos="nodePos"
          @create="openForm()"
          @edit="openForm"
          @remove="removePoint"
          @refresh="loadGraph"
        />

        <KntPathPlanner
          title="学习路径规划"
          :points="points"
          :path-source="tPathSource"
          :path-target="tPathTarget"
          :path-max-hops="tPathHops"
          :path-planning="tPathPlanning"
          :path-plan="tPathPlan"
          :path-not-found="tPathNotFound"
          :path-error="tPathError"
          @plan="planPathTeacher"
          @update:source="tPathSource = $event"
          @update:target="tPathTarget = $event"
          @update:hops="tPathHops = $event"
        />
      </template>
    </div>

    <!-- 新建/编辑 + AI 抽取对话框 -->
    <KgDialogs
      :form-dialog="formDialog"
      :is-edit="isEdit"
      :form-submitting="formSubmitting"
      :kp-form="kpForm"
      :extract-dialog="extractDialog"
      :extract-text="extractText"
      :extract-loading="extractLoading"
      :extract-result="extractResult"
      :extract-raw="extractRaw"
      :extract-nodes="extractNodes"
      :extract-relations="extractRelations"
      :node-name="nodeName"
      :rel-text="relText"
      @update:form-dialog="formDialog = $event"
      @update:extract-dialog="extractDialog = $event"
      @update:extract-text="extractText = $event"
      @submit="submitForm"
      @extract="runExtract"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'
import { useKnowledgeGraph } from '@/composables/useKnowledgeGraph'
import PageHeader from '@/components/PageHeader.vue'
import KgOverviewStats from './components/KgOverviewStats.vue'
import KgListGraph from './components/KgListGraph.vue'
import KntPathPlanner from '@/components/knowledge/KntPathPlanner.vue'
import KgDialogs from './components/KgDialogs.vue'

/**
 * 知识图谱页面（教师）
 * <p>页面壳：课程选择 + AI 抽取入口 + 概览统计 + 知识点列表与 SVG 图谱
 * + 学习路径规划 + 新建编辑/AI 抽取对话框；全部数据与业务逻辑由 useKnowledgeGraph 承载。</p>
 */
const {
  courseLoading,
  courseOptions,
  courseId,
  loading,
  loadCourses,
  onCourseChange,
  pct,
  diffType,
  diffText,
  overview,
  pointLoading,
  points,
  formDialog,
  isEdit,
  formSubmitting,
  kpForm,
  openForm,
  submitForm,
  removePoint,
  extractDialog,
  extractText,
  extractLoading,
  extractResult,
  extractRaw,
  extractNodes,
  extractRelations,
  nodeName,
  relText,
  openExtractDialog,
  runExtract,
  graphLoading,
  graphSize,
  graphNodes,
  graphLinks,
  nodePos,
  loadGraph,
  tPathSource,
  tPathTarget,
  tPathHops,
  tPathPlanning,
  tPathPlan,
  tPathNotFound,
  tPathError,
  planPathTeacher,
} = useKnowledgeGraph()

const route = useRoute()

onMounted(async () => {
  // 课程驾驶舱路由 /teacher/courses/:id/knowledge 直入：预选本课程并加载
  const cid = route.params.id || route.query.courseId
  if (cid) courseId.value = Number(cid)
  await loadCourses()
  if (cid) onCourseChange()
})
</script>

<style scoped>
.course-select {
  width: 240px;
}
.kg-page {
  max-width: 1440px;
  margin: 0 auto;
}
</style>
