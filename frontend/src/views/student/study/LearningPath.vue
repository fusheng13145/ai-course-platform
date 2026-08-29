<template>
  <div class="fade-in">
    <PageHeader
      title="学习路径"
      subtitle="AI 生成的个性化学习路线，循序渐进掌握知识"
      icon="compass"
      icon-bg="accent"
    >
      <el-button type="primary" :loading="generating" @click="handleGeneratePath">
        <el-icon><MagicStick /></el-icon> AI 生成路径
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="路径总数"
        :value="paths.length"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="已完成节点"
        :value="completedNodeCount"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="总节点数"
        :value="pathNodes.length"
        icon="list"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="整体进度"
        :value="overallProgressText"
        icon="target"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
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
            <path d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM16 8l-2 6-6 2 2-6z" />
          </svg>
        </span>
        <div>
          <div class="l1-title">学习路径详情</div>
          <div class="l1-sub">选择路径查看节点进度与每日任务</div>
        </div>
      </div>
      <div class="l1-body">
        <!-- 主内容区：路径列表 + 路径详情 -->
        <div v-if="paths.length > 0" class="lp-main">
          <!-- 左侧：推荐路径选择 -->
          <LpPathSelector
            :paths="paths"
            :selected-path-id="selectedPathId"
            :calculate-path-progress="calculatePathProgress"
            @select="selectPath"
          />

          <!-- 右侧：当前路径详情（Timeline） -->
          <div class="path-detail">
            <!-- 路径概览卡片 -->
            <LpOverviewCard
              :current-path="currentPath"
              :completed-node-count="completedNodeCount"
              :total-node-count="pathNodes.length"
              :overall-progress="overallProgress"
            />

            <!-- Timeline 学习节点 -->
            <LpTimelineSection
              :path-nodes="pathNodes"
              :get-node-size="getNodeSize"
              :get-node-type="getNodeType"
              @node-click="handleNodeClick"
              @start="startLearningNode"
              @review="reviewNode"
            />

            <!-- 每日任务面板 -->
            <LpDailyTasksPanel :daily-tasks="dailyTasks" @toggle="toggleTask" />
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <el-empty description="暂无学习路径">
            <template #image><div class="empty-deco">🧭</div></template>
            <el-button type="primary" @click="handleGeneratePath">
              <el-icon><MagicStick /></el-icon> AI 为我生成学习路径
            </el-button>
          </el-empty>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { MagicStick } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useLearningPath } from '@/composables/useLearningPath'
import LpPathSelector from './components/LpPathSelector.vue'
import LpOverviewCard from './components/LpOverviewCard.vue'
import LpTimelineSection from './components/LpTimelineSection.vue'
import LpDailyTasksPanel from './components/LpDailyTasksPanel.vue'

/**
 * AI 学习路径页（页面壳）
 * <p>统计卡 + 双栏编排（路径选择/Timeline 详情/每日任务）+ 空态，业务逻辑委托 useLearningPath。</p>
 */
const {
  paths,
  pathNodes,
  dailyTasks,
  selectedPathId,
  currentPath,
  generating,
  completedNodeCount,
  overallProgress,
  overallProgressText,
  selectPath,
  calculatePathProgress,
  getNodeType,
  getNodeSize,
  handleNodeClick,
  startLearningNode,
  reviewNode,
  toggleTask,
  handleGeneratePath,
} = useLearningPath()
</script>

<style scoped>
.lp-main {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--ac-spacing-md);
  min-height: 500px;
}

.path-detail {
  min-width: 0;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 900px) {
  .lp-main {
    grid-template-columns: 1fr;
  }
}
</style>
