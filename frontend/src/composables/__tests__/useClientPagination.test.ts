import { describe, it, expect } from 'vitest'
import { ref, computed, nextTick } from 'vue'
import { useClientPagination } from '@/composables/useClientPagination'

/**
 * useClientPagination 原语测试（D2-5）
 *
 * <p>锁定四类语义：①切片正确性（首/末/中间页）；②showPager 阈值
 * （等于 pageSize 不显示、超过才显示）；③页码收敛（数据收缩时回退，
 * 消灭「空白页」伪空态）；④reset 回第 1 页（筛选场景）。</p>
 */
function makeRows(n: number) {
  return Array.from({ length: n }, (_, i) => ({ id: i + 1 }))
}

describe('useClientPagination — 大列表客户端分页原语', () => {
  it('切片正确：25 行 / 每页 20 → 首页 20、次页 5', async () => {
    const rows = ref(makeRows(25))
    const { page, paged, total, onPageChange } = useClientPagination(() => rows.value)

    expect(total.value).toBe(25)
    expect(paged.value).toHaveLength(20)
    expect(paged.value[0].id).toBe(1)

    onPageChange(2)
    await nextTick()
    expect(page.value).toBe(2)
    expect(paged.value).toHaveLength(5)
    expect(paged.value[0].id).toBe(21)
  })

  it('showPager 阈值：恰好等于 pageSize 不显示，超过才显示', async () => {
    const rows = ref(makeRows(20))
    const { showPager } = useClientPagination(() => rows.value, { pageSize: 20 })
    expect(showPager.value).toBe(false)

    rows.value = makeRows(21)
    await nextTick()
    expect(showPager.value).toBe(true)
  })

  it('页码收敛：越界时自动回退到最后一个有效页（消灭空白页）', async () => {
    const rows = ref(makeRows(45)) // 3 页
    const { page, paged, onPageChange } = useClientPagination(() => rows.value, {
      pageSize: 20,
    })

    onPageChange(3)
    await nextTick()
    expect(paged.value).toHaveLength(5)

    // 数据收缩到 25 行（2 页）→ 当前页 3 越界，应收敛到第 2 页而非空白
    rows.value = makeRows(25)
    await nextTick()
    expect(page.value).toBe(2)
    expect(paged.value).toHaveLength(5)
  })

  it('数据清空：页码收敛到 1 且切片为空（配合空态组件展示）', async () => {
    const rows = ref(makeRows(45))
    const { page, paged, total, onPageChange } = useClientPagination(() => rows.value, {
      pageSize: 20,
    })
    onPageChange(3)
    await nextTick()

    rows.value = []
    await nextTick()
    expect(page.value).toBe(1)
    expect(total.value).toBe(0)
    expect(paged.value).toHaveLength(0)
  })

  it('reset：筛选条件变化时回到第 1 页', async () => {
    const rows = ref(makeRows(45))
    const { page, onPageChange, reset } = useClientPagination(() => rows.value, {
      pageSize: 20,
    })
    onPageChange(3)
    await nextTick()
    reset()
    await nextTick()
    expect(page.value).toBe(1)
  })

  it('支持 computed 数据源（筛选后的列表）', async () => {
    const rows = ref(makeRows(30))
    const filtered = computed(() => rows.value.filter((r) => r.id % 2 === 0))
    const { total, showPager, paged } = useClientPagination(() => filtered.value, {
      pageSize: 10,
    })
    expect(total.value).toBe(15)
    expect(showPager.value).toBe(true)
    expect(paged.value).toHaveLength(10)
  })
})
