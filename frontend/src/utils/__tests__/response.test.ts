import { describe, it, expect } from 'vitest'
import {
  decodeHtml,
  firstNonEmptyArray,
  hasNonEmptyArray,
  isPageEnvelope,
  pickList,
  readTotal,
  unwrap,
} from '@/utils/response'

/**
 * 响应信封解包工具的边界测试。
 *
 * 重点是 P1-10 的修复：仓库里大量代码写成
 * `res.data?.list || res.data?.records || []` 来「兼容多种后端结构」，
 * 但 **空数组 [] 是 truthy**，`||` 会在 list=[] 时短路，
 * 导致后端真正放在 records 里的数据被永久吞掉（界面空列表、无任何报错）。
 */
describe('firstNonEmptyArray - 按优先级取第一个非空数组', () => {
  it('list 为空数组、records 有数据时，必须取到 records（旧 `||` 写法的致命缺陷）', () => {
    const data = { list: [], records: [{ id: 1 }, { id: 2 }] }
    expect(firstNonEmptyArray(data.list, data.records)).toEqual([{ id: 1 }, { id: 2 }])
  })

  it('list 与 records 都有数据时按参数顺序取 list（优先级明确）', () => {
    expect(firstNonEmptyArray([1], [2])).toEqual([1])
  })

  it('list 为 undefined 时正确回落到 records', () => {
    expect(firstNonEmptyArray(undefined, [{ id: 3 }])).toEqual([{ id: 3 }])
  })

  it('全部候选都是空数组时返回空数组，而不是 undefined 或 false', () => {
    const out = firstNonEmptyArray([], [], undefined, null)
    expect(out).toEqual([])
    expect(Array.isArray(out)).toBe(true)
  })

  it('非数组候选（对象/字符串/null）一律跳过，不误返回', () => {
    expect(firstNonEmptyArray({ a: 1 }, 'abc', null, undefined, [7])).toEqual([7])
  })

  it('字符串虽 length>0 但不是数组，必须跳过', () => {
    expect(firstNonEmptyArray('oops', [])).toEqual([])
  })

  it('候选为 0 / 空串等 falsy 值时不抛异常', () => {
    expect(firstNonEmptyArray(0, '', null)).toEqual([])
  })

  it('保留引用：返回的就是命中的那个数组实例', () => {
    const records = [{ id: 1 }]
    expect(firstNonEmptyArray([], records)).toBe(records)
  })
})

describe('hasNonEmptyArray - 分支判定与 firstNonEmptyArray 同口径', () => {
  it('存在任一非空数组返回 true', () => {
    expect(hasNonEmptyArray([], [{ id: 1 }])).toBe(true)
  })

  it('全为空数组/非数组返回 false', () => {
    expect(hasNonEmptyArray([], [])).toBe(false)
    expect(hasNonEmptyArray(undefined, null)).toBe(false)
    expect(hasNonEmptyArray('abc', {})).toBe(false)
  })
})

describe('unwrap - 基础信封解包', () => {
  it('有 data 字段时解包 data', () => {
    expect(unwrap({ code: 200, data: { id: 1 } })).toEqual({ id: 1 })
  })

  it('data 显式为 null 时返回 null（后端确实无内容）', () => {
    expect(unwrap({ code: 200, data: null })).toBeNull()
  })

  it('无 data 字段时原样返回（兼容裸响应）', () => {
    expect(unwrap({ id: 1 })).toEqual({ id: 1 })
  })

  it('null/undefined 输入原样返回，不抛异常', () => {
    expect(unwrap(null)).toBeNull()
    expect(unwrap(undefined)).toBeUndefined()
  })

  it('data 为空数组时返回空数组（是合法业务数据，不能当成无 data）', () => {
    expect(unwrap({ code: 200, data: [] })).toEqual([])
  })
})

describe('pickList - 分页信封取列表', () => {
  it('records / list / items 三个字段按优先级取第一个非空数组', () => {
    expect(pickList({ records: [{ id: 1 }], list: [] })).toEqual([{ id: 1 }])
    expect(pickList({ records: [], list: [{ id: 2 }] })).toEqual([{ id: 2 }])
    expect(pickList({ records: [], list: [], items: [{ id: 3 }] })).toEqual([{ id: 3 }])
  })

  it('裸数组负载原样返回（后端不分页的接口）', () => {
    expect(pickList([{ id: 4 }])).toEqual([{ id: 4 }])
  })

  it('空信封 / null / 无列表字段一律得到空数组', () => {
    expect(pickList({})).toEqual([])
    expect(pickList(null)).toEqual([])
    expect(pickList(undefined)).toEqual([])
    expect(pickList({ unexpected: 'shape' })).toEqual([])
  })

  it('records=[] 且无任何列表字段时不会被包成单元素数组（幽灵数据源）', () => {
    expect(pickList({ records: [], total: 0 })).toEqual([])
  })
})

describe('readTotal - 后端总数优先', () => {
  it('total / count / totalCount 任一存在即取后端总数', () => {
    expect(readTotal({ total: 138 }, 2)).toBe(138)
    expect(readTotal({ count: 57 }, 3)).toBe(57)
    expect(readTotal({ totalCount: 9 }, 3)).toBe(9)
  })

  it('后端确实没有总数字段时才使用回退值', () => {
    expect(readTotal({ records: [1, 2] }, 2)).toBe(2)
    expect(readTotal(null, 7)).toBe(7)
    expect(readTotal({ total: 'abc' }, 7)).toBe(7)
    expect(readTotal({ total: -1 }, 7)).toBe(7)
  })

  it('total=0 是合法值，不得被回退值覆盖', () => {
    expect(readTotal({ total: 0 }, 5)).toBe(0)
  })

  it('数字字符串（后端 Long 序列化成 string 的形态）可以转换', () => {
    expect(readTotal({ total: '120' }, 0)).toBe(120)
  })
})

describe('isPageEnvelope - 区分空分页信封与单个实体', () => {
  it('含 list/records/items/total 的对象判为分页信封', () => {
    expect(isPageEnvelope({ list: [], records: [] })).toBe(true)
    expect(isPageEnvelope({ total: 0 })).toBe(true)
  })

  it('裸数组、null、标量与无分页字段的实体判为非信封', () => {
    expect(isPageEnvelope([{ id: 1 }])).toBe(false)
    expect(isPageEnvelope(null)).toBe(false)
    expect(isPageEnvelope('x')).toBe(false)
    expect(isPageEnvelope({ id: 1, name: '知识点' })).toBe(false)
  })
})

describe('decodeHtml - 还原 AIController 的展示层转义', () => {
  it('还原后端 answer/sources 实际下发的实体', () => {
    expect(decodeHtml('SYN=1, seq=x &amp; ACK=1')).toBe('SYN=1, seq=x & ACK=1')
    expect(decodeHtml('&lt;TIME_WAIT&gt;')).toBe('<TIME_WAIT>')
    expect(decodeHtml('r&quot;seq&quot;')).toBe('r"seq"')
    expect(decodeHtml('it&#39;s')).toBe("it's")
    expect(decodeHtml('it&apos;s')).toBe("it's")
  })

  it('&amp; 最后还原：二次转义不会被打平成可解析标签', () => {
    // 后端只转义一次；若把 &amp; 先还原，&amp;lt; 会变成 &lt; 再变成 <，等于多解一层
    expect(decodeHtml('&amp;lt;p&amp;gt;')).toBe('&lt;p&gt;')
  })

  it('非字符串（缺字段 / null / 数字）一律归空串，不把 undefined 播进气泡', () => {
    expect(decodeHtml(undefined)).toBe('')
    expect(decodeHtml(null)).toBe('')
    expect(decodeHtml(42)).toBe('')
    expect(decodeHtml({})).toBe('')
  })

  it('普通中文文本原样返回', () => {
    expect(decodeHtml('三次握手用于同步初始序号')).toBe('三次握手用于同步初始序号')
  })
})
