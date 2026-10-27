import { createHash } from 'node:crypto'
import { describe, it, expect } from 'vitest'
import { md5Hex } from '../md5'

/**
 * `md5Hex` 的正确性不靠自证：一律与 Node 内置 `crypto.createHash('md5')` 逐字节对拍。
 * 选对拍而非只写固定向量，是因为本工具唯一的用途就是「与后端 `MessageDigest("MD5")`
 * 声明值比对」，参照物必须是另一份独立实现（RFC 1321 的测试向量只有 17 条，
 * 覆盖不到分块边界、偏移切片与大输入的进位路径）。
 */
function randomBytes(n: number, seed: number) {
  // 不依赖 Math.random：用例必须可复跑（失败时能重贴出同一串字节）
  let s = seed
  const out = new Uint8Array(n)
  for (let i = 0; i < n; i++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    out[i] = s & 0xff
  }
  return out
}

describe('md5Hex', () => {
  it('命中 RFC 1321 附录的已知向量', () => {
    expect(md5Hex(new TextEncoder().encode(''))).toBe('d41d8cd98f00b204e9800998ecf8427e')
    expect(md5Hex(new TextEncoder().encode('abc'))).toBe('900150983cd24fb0d6963f7d28e17f72')
    expect(md5Hex(new TextEncoder().encode('message digest'))).toBe(
      'f96b697d7cb7938d525a2f31aaf161d0',
    )
    expect(md5Hex(new TextEncoder().encode('abcdefghijklmnopqrstuvwxyz'))).toBe(
      'c3fcd3d76192e4007dfb496cca67e13b',
    )
  })

  it('与 node:crypto 在 0..200 字节长度上逐位一致（覆盖分块与填充边界）', () => {
    for (let len = 0; len <= 200; len++) {
      const bytes = randomBytes(len, len + 1)
      expect(md5Hex(bytes)).toBe(createHash('md5').update(bytes).digest('hex'))
    }
  })

  it('分片规模的输入（1MB / 5MB−1）与 node:crypto 一致', () => {
    for (const len of [1024 * 1024, 5 * 1024 * 1024 - 1]) {
      const bytes = randomBytes(len, 20261017)
      expect(md5Hex(bytes)).toBe(createHash('md5').update(bytes).digest('hex'))
    }
  })

  it('接受带 byteOffset 的 Uint8Array 视图，且只摘要视图区间', () => {
    const whole = randomBytes(1000, 7)
    const view = whole.subarray(313, 313 + 517)
    expect(md5Hex(view)).toBe(createHash('md5').update(view).digest('hex'))
    expect(md5Hex(view)).not.toBe(md5Hex(whole))
  })

  it('接受 ArrayBuffer（File.slice 的产物形态）', () => {
    const bytes = randomBytes(4096, 11)
    expect(md5Hex(bytes.buffer as ArrayBuffer)).toBe(createHash('md5').update(bytes).digest('hex'))
  })

  it('输出固定 32 位小写十六进制', () => {
    const hex = md5Hex(new TextEncoder().encode('aicourse'))
    expect(hex).toMatch(/^[0-9a-f]{32}$/)
  })
})
