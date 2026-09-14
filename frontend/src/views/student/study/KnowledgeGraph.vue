<template>
  <div class="fade-in">
    <PageHeader
      title="知识图谱"
      subtitle="可视化课程知识点关联，定位学习路径"
      icon="brain"
      icon-bg="accent"
    />

    <!-- 概览统计 -->
    <div class="stat-grid animate-in d1">
      <StatCard
        label="知识点总数"
        :value="nodeCount"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="已掌握"
        :value="masteredCount"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="平均掌握度"
        :value="avgMasteryText"
        icon="target"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <!-- 图谱区 -->
    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">知识图谱</div>
          <div class="l1-sub">点击节点查看详情，按难度与关键词筛选</div>
        </div>
      </div>
      <div class="l1-body">
        <!-- 工具栏 -->
        <div class="kg-toolbar">
          <div class="toolbar-left">
            <el-select
              v-model="selectedCourseId"
              placeholder="选择课程"
              class="course-select"
              @change="loadGraphData"
            >
              <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索知识点..."
              clearable
              :prefix-icon="Search"
              class="search-input"
              @input="filterNodes"
            />
          </div>
          <div class="toolbar-right">
            <el-radio-group v-model="difficultyFilter" size="small" @change="filterNodes">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="EASY">简单</el-radio-button>
              <el-radio-button value="MEDIUM">中等</el-radio-button>
              <el-radio-button value="HARD">困难</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- 图谱 + 详情面板 -->
        <div class="kg-main">
          <SkgGraphPanel
            :graph-loading="graphLoading"
            :graph-option="graphOption"
            :filtered-node-count="filteredNodeCount"
            :selected-node="selectedNode"
            :prerequisite-nodes="prerequisiteNodes"
            :subsequent-nodes="subsequentNodes"
            :get-difficulty-tag-type="getDifficultyTagType"
            :get-difficulty-label="getDifficultyLabel"
            :get-mastery-color="getMasteryColor"
            @node-click="handleNodeClick"
            @select="selectNodeById"
            @learn="startLearningNode"
          />
        </div>
      </div>
    </section>

    <!-- 学习路径规划 -->
    <section class="l1-card animate-in d3">
      <div class="l1-head">
        <span class="l1-ico">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M4 19V5m0 14h16M4 5l8 5 4-3 4 3" />
          </svg>
        </span>
        <div>
          <div class="l1-title">学习路径规划</div>
          <div class="l1-sub">
            选择起点与终点知识点，生成最优学习路径（基于知识点关系 BFS 多跳）
          </div>
        </div>
      </div>
      <div class="l1-body">
        <KntPathPlanner
          :points="allPointOptions"
          :path-source="pathSource"
          :path-target="pathTarget"
          :path-max-hops="pathMaxHops"
          :path-planning="pathPlanning"
          :path-plan="pathPlan"
          :path-not-found="pathNotFound"
          :path-error="pathError"
          @plan="planPath"
          @update:source="pathSource = $event"
          @update:target="pathTarget = $event"
          @update:hops="pathMaxHops = $event"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { useStudentKnowledgeGraph } from '@/composables/useStudentKnowledgeGraph'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import SkgGraphPanel from './components/SkgGraphPanel.vue'
import KntPathPlanner from '@/components/knowledge/KntPathPlanner.vue'

/**
 * 知识图谱页面（学生）
 * <p>页面壳：概览统计 + 图谱区（工具栏/图谱/详情面板）+ 学习路径规划区；
 * 全部数据与业务逻辑由 useStudentKnowledgeGraph 承载。</p>
 */
const {
  courseList,
  selectedCourseId,
  searchKeyword,
  difficultyFilter,
  graphLoading,
  selectedNode,
  pathSource,
  pathTarget,
  pathMaxHops,
  pathPlanning,
  pathPlan,
  pathNotFound,
  pathError,
  allPointOptions,
  filteredNodeCount,
  nodeCount,
  masteredCount,
  avgMasteryText,
  prerequisiteNodes,
  subsequentNodes,
  graphOption,
  loadCourseList,
  loadGraphData,
  filterNodes,
  selectNodeById,
  startLearningNode,
  handleNodeClick,
  planPath,
  getDifficultyTagType,
  getDifficultyLabel,
  getMasteryColor,
} = useStudentKnowledgeGraph()

onMounted(loadCourseList)
</script>

<style scoped>
.kg-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-md);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
}
.course-select {
  width: 220px;
}
.search-input {
  width: 200px;
}
.kg-main {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: var(--ac-spacing-md);
  min-height: 600px;
}

@media (max-width: 1024px) {
  .kg-main {
    grid-template-columns: 1fr;
  }
}
</style>
