<template>
  <div class="fade-in version-manage">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">资源版本管理</h2>
        <p class="page-desc">管理课程资源的版本历史，支持创建新版本与回滚</p>
      </div>
    </div>

    <div class="content-card filter-card">
      <div class="filter-row">
        <el-select v-model="selectedResourceId" placeholder="选择资源" filterable style="width:320px" @change="loadVersions">
          <el-option v-for="r in resources" :key="r.id" :label="r.name || r.title" :value="r.id" />
        </el-select>
        <el-button type="primary" :disabled="!selectedResourceId" @click="openCreateVersion">
          <el-icon><Plus /></el-icon>新建版本
        </el-button>
        <el-button :disabled="!selectedResourceId" @click="loadVersions">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
    </div>

    <div class="content-card" v-if="selectedResourceId">
      <el-table v-loading="loading" :data="versions" style="width:100%" empty-text="暂无版本记录">
        <el-table-column prop="id" label="版本ID" width="80" />
        <el-table-column label="版本号" width="120">
          <template #default="{ row }">v{{ row.versionNumber || row.version || 1 }}</template>
        </el-table-column>
        <el-table-column label="是否为当前版本" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.id === activeVersionId" type="success" size="small">当前版本</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeLog" label="更新说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="操作人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button v-if="row.id !== activeVersionId" size="small" type="warning" link @click="handleRollback(row)">
              <el-icon><Refresh /></el-icon>回滚
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-else description="请先选择资源" :image-size="80" />

    <!-- 新建版本弹窗 -->
    <el-dialog v-model="dialogVisible" title="新建版本" width="520px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="资源">
          <el-input :model-value="selectedResourceName" disabled />
        </el-form-item>
        <el-form-item label="更新说明">
          <el-input v-model="form.changeLog" type="textarea" :rows="4" placeholder="描述本次更新内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreateVersion">创建</el-button>
      </template>
    </el-dialog>

    <!-- 回滚确认 -->
    <el-dialog v-model="rollbackVisible" title="确认回滚" width="400px" append-to-body>
      <p>确定回滚到 v{{ rollbackTarget?.versionNumber || '?' }}？</p>
      <p style="font-size:13px;color:var(--ac-text-muted)">当前版本将被替换为所选版本的内容。</p>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button type="warning" :loading="rollingBack" @click="confirmRollback">确认回滚</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import versionApi from '@/api/resourceVersion'
import resourceApi from '@/api/resource'

const loading = ref(false)
const saving = ref(false)
const rollingBack = ref(false)
const selectedResourceId = ref(null)
const resources = ref([])
const versions = ref([])
const activeVersionId = ref(null)
const dialogVisible = ref(false)
const rollbackVisible = ref(false)
const rollbackTarget = ref(null)
const form = ref({ changeLog: '' })

const selectedResourceName = computed(() => {
  const r = resources.value.find(x => x.id === selectedResourceId.value)
  return r ? r.name || r.title : ''
})

async function loadResources() {
  try {
    const res = await resourceApi.getResourceList?.({ size: 200 }) || await resourceApi.getList?.({ size: 200 }) || { data: [] }
    const data = res.data?.list || res.data?.records || res.data || []
    resources.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载资源列表失败:', e)
    resources.value = []
  }
}

async function loadVersions() {
  if (!selectedResourceId.value) return
  loading.value = true
  try {
    const [histRes, activeRes] = await Promise.allSettled([
      versionApi.getVersionHistory(selectedResourceId.value),
      versionApi.getActiveVersion(selectedResourceId.value),
    ])
    if (histRes.status === 'fulfilled') {
      const data = histRes.value.data?.list || histRes.value.data?.records || histRes.value.data || histRes.value || []
      versions.value = Array.isArray(data) ? data : []
    }
    if (activeRes.status === 'fulfilled') {
      activeVersionId.value = activeRes.value.data?.id || activeRes.value.id || null
    }
  } catch (e) {
    console.error('加载版本历史失败:', e)
    versions.value = []
  } finally {
    loading.value = false
  }
}

function openCreateVersion() {
  form.value = { changeLog: '' }
  dialogVisible.value = true
}

async function handleCreateVersion() {
  if (!form.value.changeLog?.trim()) {
    ElMessage.warning('请输入更新说明')
    return
  }
  saving.value = true
  try {
    await versionApi.createVersion({
      resourceId: selectedResourceId.value,
      changeLog: form.value.changeLog.trim(),
    })
    ElMessage.success('新版本已创建')
    dialogVisible.value = false
    await loadVersions()
  } catch (e) {
    ElMessage.error('创建版本失败')
  } finally {
    saving.value = false
  }
}

function handleRollback(row) {
  rollbackTarget.value = row
  rollbackVisible.value = true
}

async function confirmRollback() {
  if (!rollbackTarget.value) return
  rollingBack.value = true
  try {
    await versionApi.rollbackVersion(rollbackTarget.value.id)
    ElMessage.success('回滚成功')
    rollbackVisible.value = false
    await loadVersions()
  } catch (e) {
    ElMessage.error('回滚失败')
  } finally {
    rollingBack.value = false
  }
}

onMounted(loadResources)
</script>

<style scoped>
.version-manage { max-width: 1400px; margin: 0 auto; }
.page-header { margin-bottom: var(--ac-spacing-lg); }
.page-title { font-size: 22px; font-weight: 700; color: var(--ac-text-primary); }
.page-desc { font-size: 13px; color: var(--ac-text-muted); margin-top: 4px; }
.content-card { background: var(--ac-bg-elevated); border-radius: var(--ac-radius-md); border: 1px solid var(--ac-border); padding: 16px; }
.filter-card { margin-bottom: var(--ac-spacing-md); }
.filter-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
</style>
