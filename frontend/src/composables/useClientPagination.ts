import { ref, computed, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'

/**
 * 大列表客户端分页原语（D2-5）
 * ============================
 * 面向「后端一次性返回全量、前端整表渲染」的无界列表（课程学生 / 成绩 /
 * 班级成员 / 互动记录等）：数据量超过一页时，渲染层只渲染当前页切片，
 * 降低首屏渲染成本与滚动卡顿。
 *
 * 与 `useListFeedback`（D2-1）、`useFormSubmit`（D2-4）同为「先固化原语、
 * 再逐批接入」的治理模式：接入方保持原有数据抓取不变，仅在渲染层切片。
 *
 * 语义约定：
 * - `paged`：当前页数据切片（表格 `:data` 绑定它，而非全量）；
 * - `showPager`：仅当 `total > pageSize` 时展示分页器（小列表视觉零变化，
 *   接入对既有页面是「大列表才出现分页」的渐进增强）；
 * - **页码收敛**：数据收缩（删除 / 筛选 / 刷新）导致当前页越界时自动回退到
 *   最后一个有效页，避免出现「渲染空白页」的伪空态；
 * - `reset()`：筛选条件变化时回到第 1 页（避免停留在越界页）。
 *
 * 用法：
 * ```ts
 * const { page, pageSize, total, paged, showPager, onPageChange } =
 *   useClientPagination(() => props.students)
 * // 模板：<el-table :data="paged"> + <el-pagination v-if="showPager" ...>
 * ```
 */
export interface UseClientPaginationReturn<T> {
  /** 当前页码（1 起） */
  page: Ref<number>
  /** 每页行数 */
  pageSize: Ref<number>
  /** 数据总量 */
  total: ComputedRef<number>
  /** 当前页数据切片（绑定到表格 :data） */
  paged: ComputedRef<T[]>
  /** 是否需要分页器（total > pageSize） */
  showPager: ComputedRef<boolean>
  /** 分页器翻页回调 */
  onPageChange: (p: number) => void
  /** 回到第 1 页（筛选/刷新时调用） */
  reset: () => void
}

export function useClientPagination<T>(
  getSource: () => readonly T[],
  options: { pageSize?: number } = {},
): UseClientPaginationReturn<T> {
  const page = ref(1)
  const pageSize = ref(options.pageSize ?? 20)

  const total = computed(() => getSource().length)
  const paged = computed<T[]>(() => {
    const start = (page.value - 1) * pageSize.value
    return getSource().slice(start, start + pageSize.value) as T[]
  })
  const showPager = computed(() => total.value > pageSize.value)

  // 页码收敛：数据收缩导致当前页越界时回退到最后一个有效页
  watch(total, () => {
    const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (page.value > maxPage) page.value = maxPage
  })

  function onPageChange(p: number): void {
    page.value = p
  }

  function reset(): void {
    page.value = 1
  }

  return { page, pageSize, total, paged, showPager, onPageChange, reset }
}
