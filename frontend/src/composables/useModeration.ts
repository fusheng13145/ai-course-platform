import { ref, onMounted } from 'vue'
import logger from '@/utils/logger'
import adminApi from '@/api/admin'

/** 讨论回复审核行（与后端 ModerationPostVO 对齐）。 */
export interface ModerationPostRow {
  id?: number
  topicId?: number
  authorId?: number
  authorName?: string | null
  content?: string
  moderationStatus?: string
  moderationReason?: string | null
  moderatedAt?: string | null
  createdAt?: string | null
}

/** 资源审核行（与后端 ModerationResourceVO 对齐）。 */
export interface ModerationResourceRow {
  id?: number
  fileName?: string
  fileType?: string
  courseId?: number | null
  uploaderId?: number
  uploaderName?: string | null
  moderationStatus?: string
  moderationReason?: string | null
  moderatedAt?: string | null
  uploadTime?: string | null
}

/** 被封禁作者行（与后端 BannedAuthorVO 对齐）。 */
export interface BannedAuthorRow {
  userId?: number
  username?: string
  fullName?: string
  role?: string
  banReason?: string | null
  bannedAt?: string | null
  bannedBy?: number | null
}

export type ModerationTarget = 'discussion-posts' | 'resources'

/** 从分页响应里取 {list,total}（兼容 data 信封与裸数据）。 */
function unwrapPage(res: unknown): { list: unknown[]; total: number } {
  const holder = (res ?? {}) as { data?: unknown }
  const inner = (holder.data !== undefined && holder.data !== null ? holder.data : res) as {
    list?: unknown
    total?: unknown
  } | null
  const list = inner && Array.isArray(inner.list) ? inner.list : []
  const total = inner && typeof inner.total === 'number' ? inner.total : 0
  return { list, total }
}

/** 从响应里取数组。 */
function unwrapList<T>(res: unknown): T[] {
  const holder = (res ?? {}) as { data?: unknown }
  const inner = holder.data !== undefined && holder.data !== null ? holder.data : res
  return Array.isArray(inner) ? (inner as T[]) : []
}

function errorText(e: unknown, fallback: string): string {
  const err = e as { response?: { data?: { message?: string } }; message?: string } | null
  return err?.response?.data?.message || err?.message || fallback
}

/**
 * 内容审核与违规处理（角色矩阵 ③：讨论回复 + 平台全部资源）。
 *
 * <p><b>诚实口径（与页面文案、后端注释一致）</b>：
 * ① 隐藏是**可恢复**的（读路径过滤，管理员仍可见）；**删除是物理删、不可恢复**
 * （讨论回复会级联删掉楼中楼）；
 * ② 封禁作者 = 复用账号停用开关（`users.is_active=0`），被封禁账号**立即无法登录**——
 * 不是"仅禁止发帖"；
 * ③ 解除封禁只对**经审核封禁**的账号有效（后端以 banned_at 区分），其它原因停用的账号不在此列。</p>
 */
export function useModeration() {
  /** 当前页签 */
  const tab = ref<'posts' | 'resources' | 'banned'>('posts')

  const loading = ref(false)
  /** 列表加载失败文案（非空即表示当前页签列表不可信） */
  const loadError = ref('')
  /** 处置失败文案 */
  const actionError = ref('')
  const busy = ref(false)

  /** 审核状态过滤：'' = 全部；NORMAL / HIDDEN */
  const statusFilter = ref('')
  const keyword = ref('')
  const pageNo = ref(1)
  const pageSize = ref(20)
  const total = ref(0)

  const posts = ref<ModerationPostRow[]>([])
  const resources = ref<ModerationResourceRow[]>([])
  const bannedAuthors = ref<BannedAuthorRow[]>([])

  async function load() {
    loading.value = true
    loadError.value = ''
    try {
      if (tab.value === 'banned') {
        bannedAuthors.value = unwrapList<BannedAuthorRow>(await adminApi.getBannedAuthors())
      } else if (tab.value === 'resources') {
        const res = await adminApi.getModerationResources(params())
        const { list, total: t } = unwrapPage(res)
        resources.value = list as ModerationResourceRow[]
        total.value = t
      } else {
        const res = await adminApi.getModerationPosts(params())
        const { list, total: t } = unwrapPage(res)
        posts.value = list as ModerationPostRow[]
        total.value = t
      }
    } catch (e) {
      logger.error('加载内容审核列表失败', e)
      // 失败时清空当前页签列表并如实提示，避免"空列表"被误读为"没有违规内容"
      if (tab.value === 'banned') bannedAuthors.value = []
      else if (tab.value === 'resources') resources.value = []
      else posts.value = []
      loadError.value = '内容审核列表加载失败，请检查网络后重试'
    } finally {
      loading.value = false
    }
  }

  function params() {
    return {
      page: pageNo.value,
      size: pageSize.value,
      status: statusFilter.value || undefined,
      keyword: keyword.value.trim() || undefined,
    }
  }

  function setTab(next: 'posts' | 'resources' | 'banned') {
    tab.value = next
    pageNo.value = 1
    load()
  }

  function search() {
    pageNo.value = 1
    load()
  }

  /**
   * 执行处置。成功返回 true 并刷新当前列表。
   * @param banAuthor 是否同时封禁作者（HIDE / DELETE 时有意义）
   */
  async function applyAction(
    target: ModerationTarget,
    id: number,
    action: 'HIDE' | 'RESTORE' | 'DELETE',
    reason: string,
    banAuthor = false,
  ): Promise<boolean> {
    busy.value = true
    actionError.value = ''
    try {
      await adminApi.applyModerationAction(target, id, {
        action,
        reason: reason.trim() || undefined,
        banAuthor,
      })
      await load()
      return true
    } catch (e) {
      logger.error('内容处置失败', e)
      actionError.value = errorText(e, '处置失败，请稍后重试')
      return false
    } finally {
      busy.value = false
    }
  }

  /** 封禁作者（= 停用账号，立即无法登录）。 */
  async function ban(userId: number, reason: string): Promise<boolean> {
    busy.value = true
    actionError.value = ''
    try {
      await adminApi.banAuthor(userId, { reason: reason.trim() || undefined })
      await load()
      return true
    } catch (e) {
      logger.error('封禁作者失败', e)
      actionError.value = errorText(e, '封禁失败，请稍后重试')
      return false
    } finally {
      busy.value = false
    }
  }

  /** 解除封禁（只对经审核封禁的账号有效，非此类账号后端返回 404）。 */
  async function unban(userId: number): Promise<boolean> {
    busy.value = true
    actionError.value = ''
    try {
      await adminApi.unbanAuthor(userId)
      await load()
      return true
    } catch (e) {
      logger.error('解除封禁失败', e)
      actionError.value = errorText(e, '解除封禁失败，请稍后重试')
      return false
    } finally {
      busy.value = false
    }
  }

  onMounted(() => {
    load()
  })

  return {
    tab,
    loading,
    loadError,
    actionError,
    busy,
    statusFilter,
    keyword,
    pageNo,
    pageSize,
    total,
    posts,
    resources,
    bannedAuthors,
    load,
    setTab,
    search,
    applyAction,
    ban,
    unban,
  }
}
