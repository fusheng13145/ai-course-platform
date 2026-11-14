<template>
  <section class="moderation-body">
    <div class="tabs page-tabs" role="tablist" aria-label="审核对象类型">
      <button
        class="btn btn-sm"
        :class="{ 'is-active': queue === 'posts' }"
        @click="switchQueue('posts')"
      >
        讨论回复
      </button>
      <button
        class="btn btn-sm"
        :class="{ 'is-active': queue === 'resources' }"
        @click="switchQueue('resources')"
      >
        教学资源
      </button>
    </div>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            {{ queue === 'posts' ? '讨论回复' : '教学资源' }}
            <span class="chip chip-mute">{{ total }} 条</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <select
            v-model="status"
            class="input filter-select"
            aria-label="按状态筛选"
            @change="load(true)"
          >
            <option value="">全部状态</option>
            <option value="PENDING">待审核</option>
            <option value="APPROVED">已通过</option>
            <option value="HIDDEN">已隐藏</option>
            <option value="DELETED">已删除</option>
          </select>
          <button class="btn btn-sm" :disabled="loading" @click="load(true)">
            {{ loading ? '查询中…' : '查询' }}
          </button>
        </div>
      </header>

      <div v-if="rows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ queue === 'posts' ? '回复内容' : '资源名称' }}</th>
              <th class="w-140">作者</th>
              <th class="w-96">状态</th>
              <th class="w-140 th-actions">处置</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="String(row.id)">
              <td>
                <b>{{ queue === 'posts' ? brief(row.content) : row.name || '—' }}</b>
                <small v-if="queue !== 'posts'" class="cell-sub">{{ row.url || '' }}</small>
              </td>
              <td>{{ authorLabel(row) }}</td>
              <td>
                <span class="chip" :class="statusClass(row.status)">{{
                  statusLabel(row.status)
                }}</span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="row.status !== 'APPROVED'"
                    class="text-btn"
                    :disabled="acting === String(row.id)"
                    @click="act(row, 'RESTORE')"
                  >
                    恢复
                  </button>
                  <button
                    v-if="row.status !== 'HIDDEN'"
                    class="text-btn"
                    :disabled="acting === String(row.id)"
                    @click="act(row, 'HIDE')"
                  >
                    隐藏
                  </button>
                  <button
                    v-if="row.status !== 'DELETED'"
                    class="text-btn danger"
                    :disabled="acting === String(row.id)"
                    @click="act(row, 'DELETE')"
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载审核队列…</b></div>
      <div v-else class="empty-state compact">
        <b>暂无符合条件的内容</b>
        <p>全部内容处于正常状态。</p>
      </div>

      <footer v-if="total > pageSize" class="card-foot">
        <span>第 {{ page }} 页 · 共 {{ total }} 条</span>
        <div class="pager">
          <button class="btn btn-sm" :disabled="page <= 1 || loading" @click="turnPage(page - 1)">
            上一页
          </button>
          <button
            class="btn btn-sm"
            :disabled="page * pageSize >= total || loading"
            @click="turnPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </footer>
    </section>

    <section class="card table-card">
      <header class="card-head">
        <div>
          <h2>
            经审核封禁的作者 <span class="chip chip-mute">{{ banned.length }} 人</span>
          </h2>
        </div>
        <div class="filter-row class-tools">
          <button class="btn btn-sm" :disabled="bannedLoading" @click="loadBanned">
            {{ bannedLoading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>
      <div v-if="banned.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>作者</th>
              <th class="w-140">封禁原因</th>
              <th class="w-140 th-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in banned" :key="String(row.userId ?? row.id)">
              <td>
                <b>{{ row.fullName || row.username || `#${row.userId}` }}</b>
              </td>
              <td>{{ row.reason || '—' }}</td>
              <td>
                <button class="text-btn" @click="unban(row)">解除封禁</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state compact"><b>暂无封禁作者</b></div>
      <p class="hint">
        隐藏/删除会留痕（V11 审核状态与封禁留痕列）；封禁作者 =
        停用账号，立即无法登录，可在用户与账号页恢复。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useUi } from '../../composables/ui'
import {
  applyModerationAction,
  getBannedAuthors,
  getModerationPosts,
  getModerationResources,
  unbanAuthor,
} from '@/api/admin'
import { pickList, unwrap } from '@/utils/response'

interface ModRow {
  id?: number | string
  content?: string
  name?: string
  url?: string
  status?: string
  authorName?: string
  username?: string
  authorId?: number | string
}

interface BannedRow {
  userId?: number | string
  id?: number | string
  fullName?: string
  username?: string
  reason?: string
}

const { toast } = useUi()
const queue = ref<'posts' | 'resources'>('posts')
const rows = ref<ModRow[]>([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const status = ref('')
const acting = ref('')
const banned = ref<BannedRow[]>([])
const bannedLoading = ref(false)

const isPosts = computed(() => queue.value === 'posts')

function errorText(error: unknown, fallback: string): string {
  const err = error as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || fallback
}

function brief(text: unknown): string {
  const raw = String(text ?? '')
  return raw.length > 60 ? raw.slice(0, 60) + '…' : raw
}

function authorLabel(row: ModRow): string {
  return row.authorName || row.username || (row.authorId ? `#${row.authorId}` : '—')
}

function statusLabel(status: unknown): string {
  return (
    (
      { PENDING: '待审核', APPROVED: '已通过', HIDDEN: '已隐藏', DELETED: '已删除' } as Record<
        string,
        string
      >
    )[String(status ?? '')] || String(status ?? '—')
  )
}

function statusClass(status: unknown): string {
  return status === 'APPROVED'
    ? 'chip-ok'
    : status === 'HIDDEN' || status === 'DELETED'
      ? 'chip-mute'
      : 'chip-warn'
}

async function load(resetPage = false) {
  if (resetPage) page.value = 1
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize, status: status.value || undefined }
    const payload = unwrap(
      isPosts.value ? await getModerationPosts(params) : await getModerationResources(params),
    )
    const envelope = payload as { records?: ModRow[]; total?: number }
    rows.value = (envelope?.records || pickList<ModRow>(payload)) as ModRow[]
    total.value = Number(envelope?.total ?? rows.value.length)
  } catch (error) {
    toast(errorText(error, '审核队列加载失败'), 'danger')
  } finally {
    loading.value = false
  }
}

function switchQueue(next: 'posts' | 'resources') {
  queue.value = next
  status.value = ''
  page.value = 1
  void load(true)
}

function turnPage(next: number) {
  page.value = next
  void load()
}

async function act(row: ModRow, action: 'RESTORE' | 'HIDE' | 'DELETE') {
  if (row.id === undefined || row.id === null) return
  acting.value = String(row.id)
  try {
    await applyModerationAction(isPosts.value ? 'discussion-posts' : 'resources', row.id, {
      action,
    })
    toast(`已${{ RESTORE: '恢复', HIDE: '隐藏', DELETE: '删除' }[action]}`, 'ok')
    await load()
  } catch (error) {
    toast(errorText(error, '处置失败，请稍后重试'), 'danger')
  } finally {
    acting.value = ''
  }
}

async function loadBanned() {
  bannedLoading.value = true
  try {
    banned.value = pickList<BannedRow>(unwrap(await getBannedAuthors()))
  } catch (error) {
    toast(errorText(error, '封禁列表加载失败'), 'danger')
  } finally {
    bannedLoading.value = false
  }
}

async function unban(row: BannedRow) {
  const id = row.userId ?? row.id
  if (id === undefined || id === null) return
  try {
    await unbanAuthor(id)
    toast('已解除封禁', 'ok')
    await loadBanned()
  } catch (error) {
    toast(errorText(error, '解除失败，请稍后重试'), 'danger')
  }
}

onMounted(() => {
  void load()
  void loadBanned()
})
</script>

<style scoped>
.moderation-body {
  display: grid;
  gap: 14px;
}
.class-tools {
  width: auto;
  justify-content: flex-end;
}
.filter-select {
  width: 130px;
  height: 30px;
}
</style>
