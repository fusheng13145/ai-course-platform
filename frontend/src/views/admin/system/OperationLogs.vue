<template>
  <div class="fade-in operation-logs">
    <PageHeader
      title="日志审计"
      subtitle="操作日志与登录日志统一审计，可回溯到人 / 时间 / 模块 / 结果"
      icon="list"
      icon-bg="accent"
    />

    <!-- P4-MIG-07：el-tabs→TabGroup；操作日志 pane 内容 v-show（等价 el-tabs 全挂载隐藏）、
         登录日志 pane 原标 lazy → v-if（首次激活才挂载，语义逐字对齐） -->
    <TabGroup
      :tabs="LOG_TABS"
      :active="activeTab"
      variant="line"
      class="log-tabs"
      @change="activeTab = $event"
    />
    <div v-show="activeTab === 'operation'">
      <!-- ================= 操作日志 ================= -->
      <OlOperationPanel @view-detail="openDetail" />
    </div>
    <div v-if="activeTab === 'login'">
      <!-- ================= 登录日志 ================= -->
      <OlLoginPanel />
    </div>

    <!-- 详情弹窗 -->
    <OlDetailDialog v-model="showDetail" :log="currentLog" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import OlOperationPanel from './components/OlOperationPanel.vue'
import OlLoginPanel from './components/OlLoginPanel.vue'
import OlDetailDialog from './components/OlDetailDialog.vue'
import TabGroup from '@/components/ui/TabGroup.vue'

const LOG_TABS = [
  { key: 'operation', label: '操作日志' },
  { key: 'login', label: '登录日志' },
]

const activeTab = ref('operation')

/* 详情 */
const showDetail = ref(false)
const currentLog = ref(null)
function openDetail(row) {
  currentLog.value = row
  showDetail.value = true
}
</script>

<style scoped>
.operation-logs {
  max-width: 1400px;
  margin: 0 auto;
}

/* P4-MIG-07：原 :deep(.el-tabs__header) 间距规则平移到 TabGroup 根（.log-tabs 即 TabGroup 根元素）；
   原 :deep(.el-tabs__content) overflow 规则随内容块外移而失效（面板已是 .log-tabs 的兄弟节点，
   默认 overflow visible，无裁剪祖先），按等价口径删除。 */
.log-tabs {
  margin-bottom: var(--ac-spacing-md);
}
</style>
