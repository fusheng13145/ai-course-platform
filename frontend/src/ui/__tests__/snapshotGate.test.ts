import { describe, expect, it } from 'vitest'
import { assertUpdateSnapshotsAllowed } from '../../../e2e/visual/global-setup'

/**
 * 旧套件作废时，快照闸门的源码级守卫（helpers.spec）一起丢了。
 * 这道门是「重录基线」的唯一机械阻力，这里给它补一条行为级用例：
 * 三种裸写法与带值写法都必须被拦，未带旗标放行。
 */
describe('视觉基线重录闸门', () => {
  it.each([
    ['--update-snapshots'],
    ['-u'],
    ['--update'],
    ['--update-snapshots=all'],
    ['--update-snapshots=changed'],
    ['--update-snapshots=missing'],
  ])('拦截 %s', (flag) => {
    expect(() => assertUpdateSnapshotsAllowed(['node', 'playwright', flag])).toThrow()
  })

  it('未带旗标时放行', () => {
    expect(() => assertUpdateSnapshotsAllowed(['node', 'playwright', 'anchors'])).not.toThrow()
  })
})
