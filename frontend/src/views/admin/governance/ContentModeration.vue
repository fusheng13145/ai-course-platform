<template>
  <div>
    <PageHeader
      title="内容审核"
      subtitle="讨论回复 · 平台全部资源 · 违规处置（隐藏 / 删除 / 封禁作者）"
      icon="shield"
    />

    <!-- 三条口径常驻声明：不得被误读（尤其"删除不可恢复""封禁=无法登录"） -->
    <el-alert
      class="note"
      type="info"
      :closable="false"
      show-icon
      title="处置口径：隐藏可恢复（学生/教师视角立即不可见，管理员仍可见）；删除为物理删、不可恢复（讨论回复会连同其楼中楼一并删除）；封禁作者等于停用该账号，立即无法登录（不是「仅禁止发帖」）。处置动作由操作日志自动留痕。"
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

    <div class="mod-tabs" role="group" aria-label="审核对象">
      <button
        v-for="t in tabs"
        :key="t.key"
        type="button"
        class="mod-tab"
        :class="{ active: t.key === tab }"
        @click="onTab(t.key)"
      >
        {{ t.label }}
      </button>
    </div>

    <section class="l1-card animate-in d1">
      <div class="l1-body">
        <CollapsePanel :title="panelTitle" icon="list" :open="true">
          <div v-if="tab !== 'banned'" class="filter-row">
            <el-select v-model="statusFilter" class="filter-status" placeholder="全部状态">
              <el-option label="全部状态" value="" />
              <el-option label="正常" value="NORMAL" />
              <el-option label="已隐藏" value="HIDDEN" />
            </el-select>
            <el-input
              v-model="keyword"
              class="filter-keyword"
              maxlength="100"
              placeholder="关键词（讨论正文 / 资源文件名）"
            />
            <el-button :loading="loading" @click="search">查询</el-button>
          </div>

          <div v-loading="loading" class="content-card">
            <!-- 讨论回复 -->
            <div v-if="tab === 'posts'" class="tbl-wrap">
              <table v-if="posts.length" class="tbl">
                <thead>
                  <tr>
                    <th>作者</th>
                    <th>内容</th>
                    <th>状态</th>
                    <th>发表时间</th>
                    <th class="col-op">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in posts" :key="p.id">
                    <td class="cell-main">{{ p.authorName || `#${p.authorId}` }}</td>
                    <td class="cell-text">{{ p.content || '—' }}</td>
                    <td>
                      <span class="tag" :class="statusCls(p.moderationStatus)">{{
                        statusLabel(p.moderationStatus)
                      }}</span>
                    </td>
                    <td class="muted small">{{ fmtTime(p.createdAt) }}</td>
                    <td class="col-op">
                      <el-button
                        v-if="p.moderationStatus !== 'HIDDEN'"
                        link
                        type="warning"
                        :disabled="busy"
                        @click="onAction('posts', p, 'HIDE')"
                      >
                        隐藏
                      </el-button>
                      <el-button
                        v-else
                        link
                        type="primary"
                        :disabled="busy"
                        @click="onAction('posts', p, 'RESTORE')"
                      >
                        恢复
                      </el-button>
                      <el-button
                        link
                        type="danger"
                        :disabled="busy"
                        @click="onAction('posts', p, 'DELETE')"
                      >
                        删除
                      </el-button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <el-empty v-else description="没有符合条件的讨论回复" :image-size="80" />
            </div>

            <!-- 资源 -->
            <div v-else-if="tab === 'resources'" class="tbl-wrap">
              <table v-if="resources.length" class="tbl">
                <thead>
                  <tr>
                    <th>文件名</th>
                    <th>上传者</th>
                    <th>状态</th>
                    <th>上传时间</th>
                    <th class="col-op">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in resources" :key="r.id">
                    <td class="cell-main">{{ r.fileName || '—' }}</td>
                    <td class="muted small">{{ r.uploaderName || `#${r.uploaderId}` }}</td>
                    <td>
                      <span class="tag" :class="statusCls(r.moderationStatus)">{{
                        statusLabel(r.moderationStatus)
                      }}</span>
                    </td>
                    <td class="muted small">{{ fmtTime(r.uploadTime) }}</td>
                    <td class="col-op">
                      <el-button
                        v-if="r.moderationStatus !== 'HIDDEN'"
                        link
                        type="warning"
                        :disabled="busy"
                        @click="onAction('resources', r, 'HIDE')"
                      >
                        隐藏
                      </el-button>
                      <el-button
                        v-else
                        link
                        type="primary"
                        :disabled="busy"
                        @click="onAction('resources', r, 'RESTORE')"
                      >
                        恢复
                      </el-button>
                      <el-button
                        link
                        type="danger"
                        :disabled="busy"
                        @click="onAction('resources', r, 'DELETE')"
                      >
                        删除
                      </el-button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <el-empty v-else description="没有符合条件的资源" :image-size="80" />
            </div>

            <!-- 被封禁作者 -->
            <div v-else class="tbl-wrap">
              <table v-if="bannedAuthors.length" class="tbl">
                <thead>
                  <tr>
                    <th>账号</th>
                    <th>姓名</th>
                    <th>封禁原因</th>
                    <th>封禁时间</th>
                    <th class="col-op">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="b in bannedAuthors" :key="b.userId">
                    <td class="cell-main">{{ b.username || `#${b.userId}` }}</td>
                    <td class="muted small">{{ b.fullName || '—' }}</td>
                    <td class="muted small">{{ b.banReason || '—' }}</td>
                    <td class="muted small">{{ fmtTime(b.bannedAt) }}</td>
                    <td class="col-op">
                      <el-button link type="primary" :disabled="busy" @click="onUnban(b)">
                        解除封禁
                      </el-button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <el-empty v-else description="没有经审核封禁的账号" :image-size="80" />
            </div>
          </div>
        </CollapsePanel>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import {
  useModeration,
  type BannedAuthorRow,
  type ModerationPostRow,
  type ModerationResourceRow,
  type ModerationTarget,
} from '@/composables/useModeration'

const {
  tab,
  loading,
  loadError,
  actionError,
  busy,
  statusFilter,
  keyword,
  posts,
  resources,
  bannedAuthors,
  setTab,
  search,
  applyAction,
  unban,
} = useModeration()

const tabs: { key: 'posts' | 'resources' | 'banned'; label: string }[] = [
  { key: 'posts', label: '讨论回复' },
  { key: 'resources', label: '教学资源' },
  { key: 'banned', label: '被封禁作者' },
]

const panelTitle = computed(() => {
  if (tab.value === 'resources') return '资源审核'
  if (tab.value === 'banned') return '被封禁作者（经内容审核封禁）'
  return '讨论回复审核'
})

const STATUS_LABELS: Record<string, string> = { NORMAL: '正常', HIDDEN: '已隐藏' }
const STATUS_CLS: Record<string, string> = { NORMAL: 'green', HIDDEN: 'amber' }

function statusLabel(status?: string) {
  return (status && STATUS_LABELS[status]) || status || '—'
}
function statusCls(status?: string) {
  return (status && STATUS_CLS[status]) || ''
}
function fmtTime(v?: string | null) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}
function onTab(next: 'posts' | 'resources' | 'banned') {
  setTab(next)
}

type ActionKind = 'HIDE' | 'RESTORE' | 'DELETE'

function rowLabel(row: ModerationPostRow | ModerationResourceRow) {
  const anyRow = row as { authorName?: string | null; uploaderName?: string | null; id?: number }
  return anyRow.authorName || anyRow.uploaderName || `#${anyRow.id}`
}

/**
 * 处置两步确认：先问原因（ElMessageBox.prompt，居中弹层，复用既有能力不做自绘对话框），
 * 再（HIDE/DELETE 时）问是否同时封禁作者。
 */
async function onAction(
  surface: 'posts' | 'resources',
  row: ModerationPostRow | ModerationResourceRow,
  action: ActionKind,
) {
  if (row.id === undefined || row.id === null) return
  const target: ModerationTarget = surface === 'posts' ? 'discussion-posts' : 'resources'

  const verb = action === 'DELETE' ? '删除' : action === 'HIDE' ? '隐藏' : '恢复'
  const warn =
    action === 'DELETE'
      ? '删除为物理删、不可恢复（讨论回复会连同楼中楼一并删除）。'
      : action === 'HIDE'
        ? '隐藏后学生/教师视角立即不可见，可在本页恢复。'
        : '恢复后内容重新对师生可见。'

  let reason = ''
  try {
    const res = await ElMessageBox.prompt(
      `${warn}\n处置对象：${rowLabel(row)}`,
      `确认${verb}`,
      {
        confirmButtonText: `确认${verb}`,
        cancelButtonText: '取消',
        inputPlaceholder: '处置原因（可选，建议填写）',
        inputValue: '',
        type: action === 'DELETE' ? 'warning' : 'info',
      },
    )
    reason = String(res.value ?? '')
  } catch {
    return // 用户取消
  }

  let banAuthor = false
  if (action !== 'RESTORE') {
    try {
      await ElMessageBox.confirm(
        '是否同时封禁该内容的作者？封禁等于停用其账号，该用户将立即无法登录。',
        '封禁作者',
        { confirmButtonText: '同时封禁作者', cancelButtonText: '仅处置内容', type: 'warning' },
      )
      banAuthor = true
    } catch {
      banAuthor = false // 用户选择"仅处置内容"
    }
  }

  if (await applyAction(target, row.id, action, reason, banAuthor)) {
    ElMessage.success(action === 'DELETE' ? '已删除' : `已${verb}`)
  }
}

async function onUnban(row: BannedAuthorRow) {
  if (row.userId === undefined || row.userId === null) return
  try {
    await ElMessageBox.confirm(
      `确定解除「${row.fullName || row.username || row.userId}」的封禁吗？解除后该账号可立即登录。`,
      '解除封禁确认',
      { confirmButtonText: '确定解除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  if (await unban(row.userId)) ElMessage.success('已解除封禁')
}
</script>

<style scoped>
.note {
  margin-bottom: 12px;
}
/* 页签：原生按钮组（与 AI 治理页同口径，避免 JS 组件在 vue-tsc 下的 props 型报错） */
.mod-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  margin-bottom: 12px;
  background: var(--surface-3);
  border-radius: 8px;
}
.mod-tab {
  padding: 5px 14px;
  font-size: 12.5px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: var(--ink-3);
  cursor: pointer;
  white-space: nowrap;
}
.mod-tab.active {
  background: var(--surface);
  color: var(--ink);
  font-weight: 600;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.filter-status {
  max-width: 150px;
}
.filter-keyword {
  max-width: 320px;
}
.content-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-l2);
  overflow: hidden;
}
/* 讨论正文可能很长：限宽并两行截断，避免撑破表格 */
.cell-text {
  max-width: 520px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
