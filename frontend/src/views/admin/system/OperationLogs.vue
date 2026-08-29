<template>
  <div class="fade-in operation-logs">
    <PageHeader
      title="日志审计"
      subtitle="操作日志与登录日志统一审计，可回溯到人 / 时间 / 模块 / 结果"
      icon="list"
      icon-bg="accent"
    />

    <el-tabs v-model="activeTab" class="log-tabs">
      <!-- ================= 操作日志 ================= -->
      <el-tab-pane label="操作日志" name="operation">
        <OlOperationPanel @view-detail="openDetail" />
      </el-tab-pane>

      <!-- ================= 登录日志 ================= -->
      <el-tab-pane label="登录日志" name="login" lazy>
        <OlLoginPanel />
      </el-tab-pane>
    </el-tabs>

    <!-- 详情弹窗 -->
    <OlDetailDialog v-model="showDetail" :log="currentLog" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import OlOperationPanel from './components/OlOperationPanel.vue'
import OlLoginPanel from './components/OlLoginPanel.vue'
import OlDetailDialog from './components/OlDetailDialog.vue'

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

.log-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--ac-spacing-md);
}
.log-tabs :deep(.el-tabs__content) {
  overflow: visible;
}
</style>
