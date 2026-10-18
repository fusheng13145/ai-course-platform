/**
 * MD5 摘要（RFC 1321），只用于**分片完整性校验**，不用于任何安全场景。
 *
 * 为什么不是 `crypto.subtle`：Web Crypto 只提供 SHA 家族，不提供 MD5；而后端
 * `FileUploadSessionServiceImpl` 的逐片校验用的是 `MessageDigest.getInstance("MD5")`，
 * 声明值必须是 MD5 才比得上。也不引入 `spark-md5`：那会改动 lockfile 并重新过
 * `npm audit` 门禁（当前还挂着 3 条 moderate，见手册 D1-8）。
 *
 * 正确性由 `__tests__/md5.test.ts` 与 Node `crypto.createHash('md5')` 逐长度对拍保证，
 * 真机闭环由 e2e 的分片摘要分支保证（摘要不符时后端丢该片并回 400）。
 * 若本实现算错，表现是**分片被服务端拒绝**（响亮失败），不会被静默接受。
 */

const SHIFT = Uint8Array.from([
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9,
  14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21,
  6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
])

/** T[i] = floor(2^32 × |sin(i+1)|)；double 精度足以精确到整数位。 */
const K = Uint32Array.from(
  { length: 64 },
  (_, i) => Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296) >>> 0,
)

const HEX = Array.from({ length: 256 }, (_, i) => i.toString(16).padStart(2, '0'))

function rotl(x: number, c: number): number {
  return ((x << c) | (x >>> (32 - c))) >>> 0
}

/**
 * 计算字节序列的 MD5，返回 32 位小写十六进制（与后端 `HexFormat.of().formatHex` 同口径，
 * 后端比对时忽略大小写）。
 */
export function md5Hex(data: ArrayBuffer | Uint8Array): string {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data)
  const len = bytes.length
  const bits = len * 8

  const padded = new Uint8Array((((len + 8) >> 6) + 1) << 6)
  padded.set(bytes)
  padded[len] = 0x80
  const view = new DataView(padded.buffer)
  view.setUint32(padded.length - 8, bits % 4294967296, true)
  view.setUint32(padded.length - 4, Math.floor(bits / 4294967296), true)

  let a0 = 0x67452301
  let b0 = 0xefcdab89
  let c0 = 0x98badcfe
  let d0 = 0x10325476
  const m = new Uint32Array(16)

  for (let offset = 0; offset < padded.length; offset += 64) {
    for (let i = 0; i < 16; i++) {
      m[i] = view.getUint32(offset + i * 4, true)
    }
    let a = a0
    let b = b0
    let c = c0
    let d = d0

    for (let i = 0; i < 64; i++) {
      let f: number
      let g: number
      if (i < 16) {
        f = (b & c) | (~b & d)
        g = i
      } else if (i < 32) {
        f = (d & b) | (~d & c)
        g = (5 * i + 1) & 15
      } else if (i < 48) {
        f = b ^ c ^ d
        g = (3 * i + 5) & 15
      } else {
        f = c ^ (b | ~d)
        g = (7 * i) & 15
      }
      const sum = (a + f + K[i] + m[g]) >>> 0
      a = d
      d = c
      c = b
      b = (b + rotl(sum, SHIFT[i])) >>> 0
    }

    a0 = (a0 + a) >>> 0
    b0 = (b0 + b) >>> 0
    c0 = (c0 + c) >>> 0
    d0 = (d0 + d) >>> 0
  }

  const out = new DataView(new ArrayBuffer(16))
  out.setUint32(0, a0, true)
  out.setUint32(4, b0, true)
  out.setUint32(8, c0, true)
  out.setUint32(12, d0, true)
  let hex = ''
  for (let i = 0; i < 16; i++) {
    hex += HEX[out.getUint8(i)]
  }
  return hex
}

export default md5Hex
