<template>
  <div class="fade-in reminder-manage">
    <PageHeader
      title="提醒管理"
      subtitle="管理系统待办提醒和到期通知"
      icon="clock"
      icon-bg="accent"
    >
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新建提醒
      </el-button>
      <el-button @click="handleSendDue">
        <el-icon><AlarmClock /></el-icon>触发到期提醒
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="总提醒" :value="totalCount" icon="list" tone="accent" />
      <StatCard label="待处理" :value="pendingCount" icon="clock" tone="warn" />
      <StatCard label="已完成" :value="completedCount" icon="check" tone="ok" />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" /></svg></span>
        <div><h3 class="l1-title">提醒列表</h3></div>
      </div>
      <div class="l1-body">
    <div class="content-card">
      <el-table v-loading="loading" :data="todos" style="width:100%" empty-text="暂无待办提醒">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title }}</template>
        </el-table-column>
        <el-table-column label="内容" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="提醒时间" width="160">
          <template #default="{ row }">{{ row.remindAt || row.dueTime || row.scheduledAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.status === 'COMPLETED' ? '已完成' : row.status === 'PENDING' ? '待处理' : row.status || '--' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除?" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待办提醒">
            <template #image><div class="empty-deco">🔔</div></template>
          </el-empty>
        </template>
      </el-table>
    </div>
    </div>
    </section>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑提醒' : '新建提醒'" width="520px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="提醒标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="提醒详细内容" />
        </el-form-item>
        <el-form-item label="提醒时间">
          <el-date-picker v-model="form.remindAt" type="datetime" placeholder="选择提醒时间" style="width:100%" />
        </el-form-item>
        <el-form-item label="接收人">
          <el-input v-model="form.targetUserId" placeholder="用户ID（可选，留空为全体）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, AlarmClock } from '@element-plus/icons-vue'
import reminderApi from '@/api/reminder'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
const saving = ref(false)
const todos = ref([])
const totalCount = computed(() => todos.value.length)
const pendingCount = computed(() => todos.value.filter(t => t.status === 'PENDING').length)
const completedCount = computed(() => todos.value.filter(t => t.status === 'COMPLETED').length)
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({ title: '', content: '', remindAt: null, targetUserId: '' })

async function loadTodos() {
  loading.value = true
  try {
    const res = await reminderApi.getTodos()
    const data = res.data?.list || res.data?.records || res.data || []
    todos.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载提醒失败:', e)
    todos.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { title: '', content: '', remindAt: null, targetUserId: '' }
  dialogVisible.value = true
}
function openEdit(row) {
  isEdit.value = true
  form.value = { ...row, remindAt: row.remindAt || row.dueTime || null }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.title?.trim()) {
    ElMessage.warning('请输入提醒标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.value.title.trim(),
      content: form.value.content?.trim() || '',
      remindAt: form.value.remindAt,
      targetUserId: form.value.targetUserId || undefined,
    }
    if (isEdit.value) {
      await reminderApi.updateReminder(form.value.id, payload)
      ElMessage.success('提醒已更新')
    } else {
      await reminderApi.createReminder(payload)
      ElMessage.success('提醒已创建')
    }
    dialogVisible.value = false
    await loadTodos()
  } catch (e) {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  } finally {
    saving.value = false
  }
}

function handleDelete(row) {
  reminderApi.updateReminder(row.id, { status: 'CANCELLED' })
    .then(() => {
      ElMessage.success('已删除')
      loadTodos()
    })
    .catch(() => ElMessage.error('删除失败'))
}

async function handleSendDue() {
  try {
    await reminderApi.sendDue()
    ElMessage.success('到期提醒已触发')
  } catch (e) {
    ElMessage.error('触发失败')
  }
}

onMounted(loadTodos)
</script>

<style scoped>
.reminder-manage { max-width: 1400px; margin: 0 auto; }
.page-header { margin-bottom: var(--ac-spacing-lg); display: flex; justify-content: space-between; align-items: flex-start; }
.page-title { font-size: 22px; font-weight: 700; color: var(--ac-text-primary); }
.page-desc { font-size: 13px; color: var(--ac-text-muted); margin-top: 4px; }
.content-card { background: var(--ac-bg-elevated); border-radius: var(--ac-radius-md); border: 1px solid var(--ac-border); padding: 16px; }

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(.2);
}
</style>
