<template>
  <div class="fade-in file-upload-manage">
    <PageHeader
      title="分片上传管理"
      subtitle="监控大文件分片上传会话进度，支持取消与管理操作"
      icon="upload"
      icon-bg="accent"
    >
      <el-button type="primary" @click="refreshList">
        <el-icon><Refresh /></el-icon>刷新列表
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="总会话数" :value="sessionTotal" icon="list" tone="accent" />
      <StatCard label="上传中" :value="uploadingCount" icon="play" tone="warn" />
      <StatCard label="已合并" :value="mergedCount" icon="check" tone="ok" />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" /></svg></span>
        <div><h3 class="l1-title">上传会话列表</h3></div>
      </div>
      <div class="l1-body">
    <div class="content-card">
      <el-table v-loading="loading" :data="sessions" style="width:100%" empty-text="暂无上传会话">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="文件名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="file-name"><el-icon><Document /></el-icon> {{ row.fileName || row.filename || '未命名' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="文件大小" width="120">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="进度" width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :status="row.status === 'MERGED' ? 'success' : undefined" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'UPLOADING'" size="small" type="warning" @click="handleMerge(row)">合并</el-button>
            <el-button v-if="row.status === 'UPLOADING'" size="small" type="danger" @click="handleCancel(row)">
              <el-icon><Close /></el-icon>取消
            </el-button>
            <el-button v-else size="small" @click="viewProgress(row)">查看进度</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无上传会话">
            <template #image><div class="empty-deco">📤</div></template>
          </el-empty>
        </template>
      </el-table>
    </div>
    </div>
    </section>

    <!-- 进度弹窗 -->
    <el-dialog v-model="progressVisible" title="上传进度" width="520px" append-to-body>
      <div v-if="currentSession" class="progress-detail">
        <p><strong>文件：</strong>{{ currentSession.fileName || '未知' }}</p>
        <p><strong>大小：</strong>{{ formatSize(currentSession.fileSize) }}</p>
        <p><strong>状态：</strong>
          <el-tag :type="statusType(currentSession.status)" size="small">{{ statusLabel(currentSession.status) }}</el-tag>
        </p>
        <el-progress :percentage="currentSession.progress || 0" :stroke-width="20" />
        <p class="progress-hint">分片上传进度实时更新</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Document, Close } from '@element-plus/icons-vue'
import uploadApi from '@/api/fileUpload'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
const sessions = ref([])
const sessionTotal = computed(() => sessions.value.length)
const uploadingCount = computed(() => sessions.value.filter(s => s.status === 'UPLOADING').length)
const mergedCount = computed(() => sessions.value.filter(s => s.status === 'MERGED' || s.status === 'COMPLETED').length)
const progressVisible = ref(false)
const currentSession = ref(null)

function statusType(s) {
  if (s === 'MERGED' || s === 'COMPLETED') return 'success'
  if (s === 'UPLOADING') return 'warning'
  if (s === 'CANCELLED' || s === 'FAILED') return 'danger'
  return 'info'
}
function statusLabel(s) {
  const map = { UPLOADING: '上传中', MERGED: '已合并', COMPLETED: '已完成', CANCELLED: '已取消', FAILED: '失败', PENDING: '待上传' }
  return map[s] || s
}
function formatSize(bytes) {
  if (!bytes) return '--'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}

async function refreshList() {
  loading.value = true
  try {
    // 直接通过通用查询获取列表（后端可能没有 dedicated 列表接口，使用 progress 兜底）
    sessions.value = []
    ElMessage.info('上传会话列表功能需要后端支持列表查询，当前仅支持单个会话进度查看')
  } catch (e) {
    console.error('加载上传会话失败:', e)
  } finally {
    loading.value = false
  }
}

async function handleMerge(row) {
  try {
    await uploadApi.mergeChunks(row.id)
    ElMessage.success('合并成功')
    await refreshList()
  } catch (e) {
    ElMessage.error('合并失败')
  }
}
function handleCancel(row) {
  ElMessageBox.confirm('确定取消该上传任务？已上传的分片将被清除。', '取消确认', { type: 'warning' })
    .then(async () => {
      try {
        await uploadApi.cancelUpload(row.id)
        ElMessage.success('已取消')
        await refreshList()
      } catch (e) {
        ElMessage.error('取消失败')
      }
    })
    .catch(() => {})
}
async function viewProgress(row) {
  currentSession.value = row
  progressVisible.value = true
  try {
    const res = await uploadApi.getProgress(row.id)
    if (res && res.data) currentSession.value.progress = res.data.progress
  } catch (e) { /* ignore */ }
}

onMounted(refreshList)
</script>

<style scoped>
.file-upload-manage { max-width: 1400px; margin: 0 auto; }
.page-header { margin-bottom: var(--ac-spacing-lg); }
.page-title { font-size: 22px; font-weight: 700; color: var(--ac-text-primary); letter-spacing: -0.3px; }
.page-desc { font-size: 13px; color: var(--ac-text-muted); margin-top: 4px; }
.content-card { background: var(--ac-bg-elevated); border-radius: var(--ac-radius-md); border: 1px solid var(--ac-border); padding: 16px; }
.filter-card { margin-bottom: var(--ac-spacing-md); }
.filter-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.file-name { display: inline-flex; align-items: center; gap: 6px; }
.progress-detail p { margin: 8px 0; }
.progress-hint { font-size: 12px; color: var(--ac-text-muted); margin-top: 6px; }

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(.2);
}
</style>
