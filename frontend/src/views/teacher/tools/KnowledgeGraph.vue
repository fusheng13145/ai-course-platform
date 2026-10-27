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
        <!-- 加载失败提示：见 useKnowledgeGraph.loadError 注释（失败不得渲染成「暂无知识点/暂无图谱数据」） -->
        <el-alert
          v-if="loadError"
          :title="loadError"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: var(--ac-spacing-md)"
        />

        <KgOverviewStats :overview="overview" :pct="pct" />

        <!-- 图谱引擎（Python 8001）：同步与子图。失败路各自独立持久提示，不渲染成「暂无」 -->
        <section class="engine-card">
          <div class="engine-head">
            <el-icon><Connection /></el-icon>
            <span class="engine-title">图谱引擎</span>
            <el-tag
              v-if="engineSyncMode"
              size="small"
              :type="engineModeType(engineSyncMode)"
              effect="light"
              round
            >
              {{ engineModeText(engineSyncMode) }}
            </el-tag>
            <div class="engine-actions">
              <el-button size="small" :loading="engineSyncing" @click="syncKnowledgeGraphEngine">
                <el-icon><Refresh /></el-icon>同步到图谱引擎
              </el-button>
              <el-button size="small" :loading="engineSubGraphLoading" @click="loadEngineSubGraph">
                <el-icon><DataLine /></el-icon>查看引擎子图
              </el-button>
            </div>
          </div>
          <el-alert
            v-if="engineSyncError"
            :title="engineSyncError"
            type="error"
            :closable="false"
            show-icon
            class="engine-alert"
          />
          <el-alert
            v-if="engineSubGraphError"
            :title="engineSubGraphError"
            type="error"
            :closable="false"
            show-icon
            class="engine-alert"
          />
          <p v-if="engineSyncNote" class="engine-note">{{ engineSyncNote }}</p>
          <p v-if="engineSyncStale" class="engine-hint">
            图谱数据已变更，建议重新「同步到图谱引擎」。
          </p>
          <div v-if="engineSubGraph" class="engine-subgraph">
            <span class="engine-metric">子图节点：{{ engineSubGraphNodeCount }}</span>
            <span class="engine-metric">子图关系：{{ engineSubGraphEdgeCount }}</span>
            <el-tag
              v-if="engineSubGraphMode"
              size="small"
              :type="engineModeType(engineSubGraphMode)"
              effect="plain"
            >
              来源：{{ engineModeText(engineSubGraphMode) }}
            </el-tag>
            <span v-if="engineSubGraphNote" class="engine-note-inline">{{
              engineSubGraphNote
            }}</span>
          </div>
        </section>

        <KgListGraph
          :points="points"
          :point-loading="pointLoading"
          :diff-type="diffType"
          :diff-text="diffText"
          :graph-loading="graphLoading"
          :graph-option="graphOption"
          :has-graph="hasGraph"
          :empty-text="loadError ? '知识点未能加载，请查看上方提示后重试' : '暂无知识点'"
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
import { Connection, DataLine, MagicStick, Refresh } from '@element-plus/icons-vue'
import { useKnowledgeGraph } from '@/composables/useKnowledgeGraph'
import PageHeader from '@/components/PageHeader.vue'
import KgOverviewStats from './components/KgOverviewStats.vue'
import KgListGraph from './components/KgListGraph.vue'
import KntPathPlanner from '@/components/knowledge/KntPathPlanner.vue'
import KgDialogs from './components/KgDialogs.vue'

/**
 * 知识图谱页面（教师）
 * <p>页面壳：课程选择 + AI 抽取入口 + 概览统计 + 知识点列表与力导向图谱
 * + 学习路径规划 + 新建编辑/AI 抽取对话框；全部数据与业务逻辑由 useKnowledgeGraph 承载。</p>
 */
const {
  courseLoading,
  courseOptions,
  courseId,
  loading,
  loadError,
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
  graphOption,
  hasGraph,
  loadGraph,
  tPathSource,
  tPathTarget,
  tPathHops,
  tPathPlanning,
  tPathPlan,
  tPathNotFound,
  tPathError,
  planPathTeacher,
  engineSyncing,
  engineSyncError,
  engineSyncMode,
  engineSyncNote,
  engineSyncStale,
  engineSubGraphLoading,
  engineSubGraphError,
  engineSubGraphMode,
  engineSubGraphNote,
  engineSubGraph,
  engineSubGraphNodeCount,
  engineSubGraphEdgeCount,
  engineModeText,
  engineModeType,
  syncKnowledgeGraphEngine,
  loadEngineSubGraph,
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
.engine-card {
  margin-bottom: var(--ac-spacing-md);
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.engine-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.engine-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.engine-alert {
  margin-top: var(--ac-spacing-sm);
}
.engine-note {
  margin: var(--ac-spacing-sm) 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--ac-text-muted);
}
.engine-hint {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--el-color-warning);
}
.engine-subgraph {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: var(--ac-spacing-sm);
  font-size: 13px;
  color: var(--ac-text-primary);
}
.engine-note-inline {
  font-size: 12.5px;
  color: var(--ac-text-muted);
}
</style>
