<template>
  <div>
    <PageHeader
      title="学期管理"
      subtitle="创建学期 · 归档过期学期（治理维度）"
      icon="calendar"
    />

    <!-- 口径声明：必须常驻，避免把"归档"误读成"冻结课程" -->
    <el-alert
      class="note"
      type="info"
      :closable="false"
      show-icon
      title="学期表与课程归属尚未绑定：courses.semester 仍是自由文本列，归档只改变学期自身的状态与列表分组，不会冻结或改动任何课程。"
    />

    <el-alert v-if="loadError" class="note" type="error" :closable="false" show-icon :title="loadError" />
    <el-alert
      v-if="actionError"
      class="note"
      type="error"
      :closable="false"
      show-icon
      :title="actionError"
    />

    <div v-loading="loading">
      <div class="stat-grid animate-in d1">
        <StatCard label="学期总数" :value="semesters.length" icon="calendar" />
        <StatCard label="在册" :value="activeCount" icon="checks" />
        <StatCard
          label="已归档"
          :value="archivedCount"
          icon="archive"
          tone="var(--warn-soft)"
          tone-color="var(--warn)"
        />
      </div>

      <section class="l1-card animate-in d2">
        <div class="l1-body">
          <CollapsePanel title="新增学期" icon="plus" :open="true">
            <div class="create-row">
              <el-input
                v-model="form.name"
                class="sem-name"
                maxlength="64"
                placeholder="学期名称，如 2026-2027-1"
              />
              <el-date-picker
                v-model="form.startDate"
                class="sem-date"
                type="date"
                placeholder="开始日期"
                value-format="YYYY-MM-DD"
              />
              <el-date-picker
                v-model="form.endDate"
                class="sem-date"
                type="date"
                placeholder="结束日期"
                value-format="YYYY-MM-DD"
              />
              <el-button type="primary" :loading="submitting" @click="onCreate">创建学期</el-button>
            </div>
          </CollapsePanel>

          <CollapsePanel title="学期列表" icon="list" :open="true">
            <div class="content-card">
              <div class="tbl-ops">
                <el-button :loading="submitting" @click="onArchiveExpired">
                  归档所有过期学期
                </el-button>
              </div>
              <div v-if="semesters.length" class="tbl-wrap">
                <table class="tbl">
                  <thead>
                    <tr>
                      <th>学期名称</th>
                      <th>开始</th>
                      <th>结束</th>
                      <th>状态</th>
                      <th>归档时间</th>
                      <th class="col-op">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in semesters" :key="row.id">
                      <td class="cell-main">{{ row.name || '—' }}</td>
                      <td class="muted small">{{ fmtDate(row.startDate) }}</td>
                      <td class="muted small">{{ fmtDate(row.endDate) }}</td>
                      <td>
                        <span class="tag" :class="statusCls(row.status)">{{
                          statusLabel(row.status)
                        }}</span>
                      </td>
                      <td class="muted small">{{ fmtTime(row.archivedAt) }}</td>
                      <td class="col-op">
                        <el-button
                          link
                          type="primary"
                          :disabled="row.status === 'ARCHIVED'"
                          @click="onArchive(row)"
                        >
                          归档
                        </el-button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <el-empty v-else description="尚未注册任何学期" :image-size="80" />
            </div>
          </CollapsePanel>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import { useSemesters, type SemesterRow } from '@/composables/useSemesters'

const {
  semesters,
  loading,
  loadError,
  actionError,
  submitting,
  activeCount,
  archivedCount,
  create,
  archive,
  archiveExpired,
} = useSemesters()

const form = ref<{ name: string; startDate: string; endDate: string }>({
  name: '',
  startDate: '',
  endDate: '',
})

const STATUS_LABELS: Record<string, string> = { ACTIVE: '在册', ARCHIVED: '已归档' }
const STATUS_CLS: Record<string, string> = { ACTIVE: 'green', ARCHIVED: 'amber' }

function statusLabel(status?: string) {
  return (status && STATUS_LABELS[status]) || status || '—'
}
function statusCls(status?: string) {
  return (status && STATUS_CLS[status]) || ''
}
function fmtDate(value?: string) {
  return value ? String(value).slice(0, 10) : '—'
}
function fmtTime(value?: string | null) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

async function onCreate() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写学期名称')
    return
  }
  if (!form.value.startDate || !form.value.endDate) {
    ElMessage.warning('请选择学期的开始与结束日期')
    return
  }
  const ok = await create({
    name: form.value.name.trim(),
    startDate: form.value.startDate,
    endDate: form.value.endDate,
  })
  if (ok) {
    ElMessage.success('学期创建成功')
    form.value = { name: '', startDate: '', endDate: '' }
  }
}

async function onArchive(row: SemesterRow) {
  if (row.id === undefined || row.id === null) return
  try {
    await ElMessageBox.confirm(
      `确定归档学期「${row.name || row.id}」吗？归档后仅改变其治理状态，不影响任何课程。`,
      '归档确认',
      { confirmButtonText: '确定归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  if (await archive(row.id)) {
    ElMessage.success('已归档')
  }
}

async function onArchiveExpired() {
  try {
    await ElMessageBox.confirm(
      '将归档所有「状态为在册且结束日期早于今天」的学期。归档只改变学期状态，不影响任何课程。',
      '批量归档确认',
      { confirmButtonText: '确定归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  const count = await archiveExpired()
  if (count >= 0) {
    ElMessage.success(count > 0 ? `本次归档 ${count} 个过期学期` : '没有需要归档的过期学期')
  }
}
</script>

<style scoped>
.note {
  margin-bottom: 12px;
}
.create-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.sem-name {
  max-width: 260px;
}
.sem-date {
  max-width: 180px;
}
.tbl-ops {
  display: flex;
  justify-content: flex-end;
  padding: 10px 12px;
}
</style>
