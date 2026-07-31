import { describe, it, expect } from 'vitest'
import { sanitizeHtml, sanitizeObject } from '@/utils/sanitize'

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
          blocks: [
            { text: '<p>安全</p><script>bad</script>' },
            { text: '<strong>重要</strong>' },
          ],
        },
      }
      const result = sanitizeObject(input)
      expect(result.article.blocks[0].text).toContain('<p>安全</p>')
      expect(result.article.blocks[0].text).not.toContain('<script>')
      expect(result.article.blocks[1].text).toContain('<strong>重要</strong>')
    })
  })
})
