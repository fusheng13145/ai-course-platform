/**
 * 一次性抽取器：从 demo/v2/02-record.html 生成 v2 前端资产。
 *
 * 产出（均入库，可逐字节复现）：
 *   src/v2/styles/record.css      原型 CSS 作用域到 body[data-app="v2"]，keyframes 加 v2- 前缀防与旧端同名
 *   src/v2/data/nav.json          导航、课程页签、样式元数据（壳层随路由懒加载）
 *   src/v2/data/demo.json         模块元数据、IA 样本、知识图谱节点（页面按需加载）
 *   src/v2/components/V2Sprite.vue 图标精灵
 *
 * 用法：node scripts/extract-v2-assets.cjs
 * 判据：同一份源文件重复运行，全部产物 sha1 不变。
 */
const fs = require('fs')
const path = require('path')
const postcss = require('postcss')

const ROOT = path.resolve(__dirname, '..')
const SOURCE = path.resolve(ROOT, '..', 'demo', 'v2', '02-record.html')
const OUT_CSS = path.join(ROOT, 'src', 'v2', 'styles', 'record.css')
const OUT_NAV_JSON = path.join(ROOT, 'src', 'v2', 'data', 'nav.json')
const OUT_DEMO_JSON = path.join(ROOT, 'src', 'v2', 'data', 'demo.json')
const OUT_SPRITE = path.join(ROOT, 'src', 'v2', 'components', 'V2Sprite.vue')

const NAV_KEYS = [
  '__NAV__',
  '__COURSE_TABS__',
  '__ADMIN_COURSE_TABS__',
  '__PAGE_LABELS__',
  '__STYLE__',
]
const DEMO_KEYS = ['__DB__', '__IA__', '__WEAK_NODES__', '__WEAK_EDGES__', '__PALETTE_ACTIONS__']

const lines = fs.readFileSync(SOURCE, 'utf8').split(/\r?\n/)

const styleStart = lines.findIndex((line) => line.trim() === '<style>')
const scriptStart = lines.findIndex((line) => line.trim() === '<script>')
const styleEnd = lines.findIndex((line, index) => index > styleStart && line.trim() === '</style>')
const cssText = lines.slice(styleStart + 1, styleEnd).join('\n')

/** 只在 v2 路由挂载时生效的选择器前缀 */
const V2 = 'body[data-app="v2"]'

function scopeSelector(selector) {
  const raw = selector.trim()
  if (!raw) return raw
  if (raw.startsWith(V2)) return raw
  // 这三个 body 属性只有 v2 壳层会写，旧端从不写，保持原样即可
  if (/^body\[data-(app|style|theme)/.test(raw)) return raw
  if (raw === ':root' || raw === 'html' || raw === 'body') return V2
  if (raw.startsWith('html ')) return `${V2} ${raw.slice(5)}`
  if (raw.startsWith('body')) return `${V2}${raw.slice(4)}`
  if (raw.startsWith('*')) return `${V2} ${raw}`
  return `${V2} ${raw}`
}

const root = postcss.parse(cssText)

const keyframes = new Set()
root.walkAtRules(/keyframes$/i, (rule) => {
  keyframes.add(rule.params.trim())
})
root.walkAtRules(/keyframes$/i, (rule) => {
  rule.params = `v2-${rule.params.trim()}`
})
root.walkDecls(/^animation(-name)?$/i, (decl) => {
  for (const name of keyframes) {
    decl.value = decl.value.replace(new RegExp(`\\b${name}\\b`, 'g'), `v2-${name}`)
  }
})
root.walkRules((rule) => {
  if (rule.parent && rule.parent.type === 'atrule' && /keyframes$/i.test(rule.parent.name)) return
  rule.selectors = rule.selectors.map(scopeSelector)
})

const header = `/* 由 scripts/extract-v2-assets.cjs 从 demo/v2/02-record.html 生成，勿手改。\n   作用域：${V2}，仅在 v2 壳层挂载并被设置该属性时生效。 */\n`
fs.mkdirSync(path.dirname(OUT_CSS), { recursive: true })
fs.writeFileSync(OUT_CSS, `${header}${root.toString()}\n`, 'utf8')

function readWindowData(keys) {
  const result = {}
  for (const key of keys) {
    const line = lines.find((item) => item.startsWith(`window.${key} =`))
    if (!line) throw new Error(`未找到 ${key}`)
    const payload = line.slice(`window.${key} =`.length).replace(/;\s*$/, '').trim()
    result[key.replace(/^__|__$/g, '').toLowerCase()] = JSON.parse(payload)
  }
  return result
}

const navData = readWindowData(NAV_KEYS)
const demoData = readWindowData(DEMO_KEYS)
fs.mkdirSync(path.dirname(OUT_NAV_JSON), { recursive: true })
fs.writeFileSync(OUT_NAV_JSON, `${JSON.stringify(navData)}\n`, 'utf8')
fs.writeFileSync(OUT_DEMO_JSON, `${JSON.stringify(demoData)}\n`, 'utf8')

const legacyJson = path.join(ROOT, 'src', 'v2', 'data', 'prototype.json')
if (fs.existsSync(legacyJson)) fs.unlinkSync(legacyJson)

const cssKb = (fs.statSync(OUT_CSS).size / 1024).toFixed(1)
const navKb = (fs.statSync(OUT_NAV_JSON).size / 1024).toFixed(1)
const demoKb = (fs.statSync(OUT_DEMO_JSON).size / 1024).toFixed(1)

const spriteLine = lines.find((line) => line.includes('<symbol id="i-dashboard"'))
if (!spriteLine) throw new Error('未找到图标精灵')
const spriteBody = spriteLine.replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>[\s\S]*$/, '')
fs.mkdirSync(path.dirname(OUT_SPRITE), { recursive: true })
fs.writeFileSync(
  OUT_SPRITE,
  `<template>\n  <svg class="v2-sprite" aria-hidden="true" focusable="false">${spriteBody}</svg>\n</template>\n`,
  'utf8',
)

console.log(
  `[v2] record.css ${cssKb} KB / nav.json ${navKb} KB / demo.json ${demoKb} KB / sprite ${spriteBody.length} B`,
)
console.log(`[v2] keyframes renamed: ${[...keyframes].map((n) => `v2-${n}`).join(', ')}`)
console.log(`[v2] source script line: ${scriptStart + 1}`)
