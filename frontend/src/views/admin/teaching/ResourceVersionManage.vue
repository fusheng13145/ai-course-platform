<template>
  <div class="fade-in version-manage">
    <PageHeader
      title="资源版本管理"
      subtitle="管理课程资源的版本历史，支持创建新版本与回滚"
      icon="layers"
      icon-bg="accent"
    >
      <el-button type="primary" :disabled="!selectedResourceId" @click="openCreateVersion">
        <el-icon><Plus /></el-icon>新建版本
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="版本总数" :value="versionCount" icon="list" tone="accent" />
      <StatCard label="当前版本" :value="currentVersion" icon="check" tone="ok" />
    </div>

    <!-- 加载失败提示：见 loadError 注释（失败不得静默渲染成「暂无版本记录」与 0 值读数） -->
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: var(--ac-spacing-md)"
    />

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"
          ><svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7"
            /></svg
        ></span>
        <div>
          <h3 class="l1-title">版本历史</h3>
        </div>
      </div>
      <div class="l1-body">
        <div class="content-card filter-card">
          <div class="filter-row">
            <el-select
              v-model="selectedResourceId"
              placeholder="选择资源"
              filterable
              style="width: 320px"
              @change="loadVersions"
            >
              <el-option
                v-for="r in resources"
                :key="r.id"
                :label="r.name || r.title"
                :value="r.id"
              />
            </el-select>
            <el-button type="primary" :disabled="!selectedResourceId" @click="openCreateVersion">
              <el-icon><Plus /></el-icon>新建版本
            </el-button>
            <el-button :disabled="!selectedResourceId" @click="loadVersions">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </div>

        <div v-if="selectedResourceId" class="content-card">
          <el-table
            v-loading="loading"
            :data="versions"
            style="width: 100%"
            :empty-text="versionsError ? '版本记录未能加载，请查看上方提示后重试' : '暂无版本记录'"
          >
            <el-table-column prop="id" label="版本ID" width="80" />
            <el-table-column label="版本号" width="120">
              <template #default="{ row }">v{{ row.versionNumber || row.version || 1 }}</template>
            </el-table-column>
            <el-table-column label="是否为当前版本" width="130" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.id === activeVersionId" type="success" size="small"
                  >当前版本</el-tag
                >
              </template>
            </el-table-column>
            <el-table-column
              prop="changeLog"
              label="更新说明"
              min-width="260"
              show-overflow-tooltip
            />
            <el-table-column prop="createdBy" label="操作人" width="120" />
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.id !== activeVersionId"
                  size="small"
                  type="warning"
                  link
                  @click="handleRollback(row)"
                >
                  <el-icon><Refresh /></el-icon>回滚
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState
                :title="versionsError ? '版本记录未能加载，请查看上方提示后重试' : '暂无版本记录'"
                icon="layers"
              />
            </template>
          </el-table>
        </div>
        <EmptyState v-else title="请先选择资源" desc="在上方资源下拉框选择后查看版本" icon="folder" />
      </div>
    </section>

    <!-- 新建版本弹窗 -->
    <el-dialog ref="dialogRef1" v-model="dialogVisible" title="新建版本" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" scroll-to-error>
        <el-form-item label="资源">
          <el-input :model-value="selectedResourceName" disabled />
        </el-form-item>
        <el-form-item label="更新说明" prop="changeLog">
          <el-input
            v-model="form.changeLog"
            type="textarea"
            :rows="4"
            placeholder="描述本次更新内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreateVersion">创建</el-button>
      </template>
    </el-dialog>

    <!-- 回滚确认 -->
    <el-dialog ref="dialogRef2" v-model="rollbackVisible" title="确认回滚" width="400px" append-to-body>
      <p>确定回滚到 v{{ rollbackTarget?.versionNumber || '?' }}？</p>
      <p style="font-size: 13px; color: var(--ac-text-muted)">当前版本将被替换为所选版本的内容。</p>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button type="warning" :loading="rollingBack" @click="confirmRollback"
          >确认回滚</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import versionApi from '@/api/resourceVersion'
import resourceApi from '@/api/resource'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { validateFormFields, type ElFormLike } from '@/composables/useFormSubmit'
import { useDialogFocus } from '@/composables/useDialogFocus'

const loading = ref(false)
const saving = ref(false)
const rollingBack = ref(false)
const selectedResourceId = ref(null)
const resources = ref([])
const versions = ref([])
const activeVersionId = ref(null)
const dialogVisible = ref(false)
const dialogRef1 = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef1, dialogVisible)
const rollbackVisible = ref(false)
const dialogRef2 = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef2, rollbackVisible)
const rollbackTarget = ref(null)
const form = ref({ changeLog: '' })
const formRef = ref<ElFormLike | null>(null)
// D2-4：必填规则声明（原为手写 if 校验），blur 内联错误 + 提交时首错滚动定位
const rules = {
  changeLog: [{ required: true, message: '请输入更新说明', trigger: 'blur' }],
}

/**
 * 两路独立加载错误（失败可见）：
 * - resourcesError：资源下拉列表加载失败（原实现仅 console.error 后静默清空，
 *   管理员会误判平台无任何资源）；
 * - versionsError：版本历史/当前版本加载失败（原实现 allSettled 后不检测 rejected，
 *   任一接口失败都被静默忽略，表格渲染成「暂无版本记录」）。
 * 两路互不复位，由 loadError 聚合展示，避免一路成功清掉另一路的失败提示。
 */
const resourcesError = ref('')
const versionsError = ref('')
const loadError = computed(() =>
  [resourcesError.value, versionsError.value].filter(Boolean).join('；'),
)

const selectedResourceName = computed(() => {
  const r = resources.value.find((x) => x.id === selectedResourceId.value)
  return r ? r.name || r.title : ''
})

/** 版本总数：加载失败时不得谎报 0，改显 '--' */
const versionCount = computed(() => (versionsError.value ? '--' : versions.value.length))
const currentVersion = computed(() => {
  if (versionsError.value) return '--'
  const v = versions.value.find((x) => x.id === activeVersionId.value)
  return v ? v.versionNumber || v.version || '—' : '—'
})

async function loadResources() {
  resourcesError.value = ''
  try {
    // 缺陷修复（2026-10-04）：原写法 `resourceApi.getResourceList?.({ size: 200 })` 有两重失效——
    // ① getResourceList 的首参是 courseId，传对象会拼出 `/resources/course/[object Object]`；
    // ② 兜底的 `resourceApi.getList` 在 api/resource.ts 里根本不存在，可选链使这半句永远静默跳过。
    // 本页为管理端跨课程视图，改用 /resources/search（keyword 传空即全量，仅 ADMIN 可跨课程）。
    const res = await resourceApi.searchResources({ keyword: '', page: 1, size: 200 })
    const data = res.data?.records || res.data?.list || res.data || []
    resources.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载资源列表失败:', e)
    resources.value = []
    resourcesError.value = '资源列表加载失败，请检查网络后重试'
  }
}

async function loadVersions() {
  if (!selectedResourceId.value) return
  loading.value = true
  versionsError.value = ''
  try {
    const [histRes, activeRes] = await Promise.allSettled([
      versionApi.getVersionHistory(selectedResourceId.value),
      versionApi.getActiveVersion(selectedResourceId.value),
    ])
    // 失败可见：allSettled 不抛错，必须逐路检测 rejected，否则失败被静默吞掉
    const notices: string[] = []
    if (histRes.status === 'fulfilled') {
      const data =
        histRes.value.data?.list ||
        histRes.value.data?.records ||
        histRes.value.data ||
        histRes.value ||
        []
      versions.value = Array.isArray(data) ? data : []
    } else {
      console.error('加载版本历史失败:', histRes.reason)
      versions.value = []
      notices.push('版本历史加载失败')
    }
    if (activeRes.status === 'fulfilled') {
      activeVersionId.value = activeRes.value.data?.id || activeRes.value.id || null
    } else {
      console.error('加载当前版本失败:', activeRes.reason)
      activeVersionId.value = null
      notices.push('当前版本加载失败')
    }
    versionsError.value = notices.length ? `${notices.join('、')}，请检查网络后重试` : ''
  } catch (e) {
    console.error('加载版本历史失败:', e)
    versions.value = []
    activeVersionId.value = null
    versionsError.value = '版本信息加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function openCreateVersion() {
  form.value = { changeLog: '' }
  dialogVisible.value = true
}

async function handleCreateVersion() {
  // validate 先行（真环境内联错误 + scroll-to-error）；桩/无表单环境由快检兜底
  if (!(await validateFormFields(formRef, ['changeLog']))) return
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
  } catch {
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
  } catch {
    ElMessage.error('回滚失败')
  } finally {
    rollingBack.value = false
  }
}

onMounted(loadResources)
</script>

<style scoped>
.version-manage {
  max-width: 1400px;
  margin: 0 auto;
}
.page-header {
  margin-bottom: var(--ac-spacing-lg);
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
}
.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.filter-card {
  margin-bottom: var(--ac-spacing-md);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

</style>
