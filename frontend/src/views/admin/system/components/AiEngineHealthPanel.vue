<template>
  <div class="content-card engine-health">
    <div class="card-title">
      <el-icon><Monitor /></el-icon>AI 引擎健康状态
      <el-button class="refresh-btn" link :loading="loading" @click="load">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <!-- 失败可见：加载失败时给出持久告警，且各项回落为 "--"，绝不显示成"全部正常" -->
    <el-alert
      v-if="loadError"
      class="health-alert"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
    />
    <el-alert
      v-if="pythonNote"
      class="health-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="pythonNote"
    />

    <div v-loading="loading" class="health-grid">
      <div v-for="r in rows" :key="r.key" class="health-item">
        <div class="health-head">
          <span class="health-name">{{ r.label }}</span>
          <el-tag size="small" :type="tagType(r.tone)" effect="light">{{ r.status }}</el-tag>
        </div>
        <div class="health-detail">{{ r.detail || '—' }}</div>
      </div>
    </div>

    <p class="health-foot">
      未启用（DISABLED）表示该引擎开关关闭，并非故障；健康数据不可用时各项显示
      <code>--</code>，不代表正常。
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * AI 引擎健康状态面板
 * <p>展示数据库、Redis 与三个 Python 微服务（知识图谱 / RAG / 微调）的实时可用性。
 * 数据来自 GET /admin/system/health；逐项状态与失败可见逻辑集中在 useSystemHealth。</p>
 */
import { Monitor, Refresh } from '@element-plus/icons-vue'
import { useSystemHealth, type EngineHealthRow } from '@/composables/useSystemHealth'

const { loading, loadError, rows, pythonNote, load } = useSystemHealth()

/** 状态基调 → el-tag 类型。 */
function tagType(tone: EngineHealthRow['tone']): 'success' | 'info' | 'danger' {
  if (tone === 'ok') return 'success'
  if (tone === 'info') return 'info'
  return 'danger'
}
</script>

<style scoped>
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 16px;
}
.card-title .el-icon {
  color: var(--ac-primary, var(--info));
}
.refresh-btn {
  margin-left: auto;
}
.health-alert {
  margin-bottom: 12px;
}
.health-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.health-item {
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 10px 12px;
}
.health-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.health-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-primary);
}
.health-detail {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ac-text-muted);
  word-break: break-all;
}
.health-foot {
  margin: 14px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ac-text-muted);
}
.health-foot code {
  background: rgba(127, 127, 127, 0.12);
  padding: 1px 5px;
  border-radius: 4px;
}
</style>
