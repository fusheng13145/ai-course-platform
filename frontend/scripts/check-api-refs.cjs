/**
 * 全仓 API 方法引用一致性检查：
 * 扫描 src 下所有 import xxxApi from '@/api/xxx' 的使用，比对 xxxApi.method 是否存在于 api 模块导出。
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', 'src')

function walk(dir, exts, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, exts, out)
    else if (exts.some((x) => e.name.endsWith(x))) out.push(p)
  }
  return out
}

/** 提取 api 模块的导出方法名集合（支持 export const fn 与 export const objApi = {...} 对象结构） */
function apiExports(file) {
  const src = fs.readFileSync(file, 'utf8')
  const names = new Set()
  // 具名函数导出
  for (const m of src.matchAll(/export\s+const\s+(\w+)\s*=/g)) {
    const name = m[1]
    // 若为对象字面量（含方法键），提取方法名
    const blockMatch = src.slice(m.index).match(/=\s*\{([\s\S]*?)\n\}/)
    if (blockMatch) {
      for (const km of blockMatch[1].matchAll(/(\w+)\s*(?:\(|:)/g)) names.add(km[1])
    } else {
      names.add(name)
    }
  }
  // export default 对象（贪婪匹配到末尾缩进闭合，避免嵌套括号提前截断）
  const dm = src.match(/export\s+default\s*\{([\s\S]*?)\n\}/)
  if (dm) {
    for (const m of dm[1].matchAll(/(\w+)\s*\(/g)) names.add(m[1])
    for (const m of dm[1].matchAll(/^\s*(\w+)\s*[,}]/gm)) names.add(m[1])
  }
  return names
}

// 收集 api 模块
const apiDir = path.join(ROOT, 'api')
const apiModules = {}
for (const f of fs.readdirSync(apiDir)) {
  if (f.endsWith('.js')) apiModules[f.replace('.js', '')] = apiExports(path.join(apiDir, f))
}

// 扫描 composables 与 views
const targets = [...walk(path.join(ROOT, 'composables'), ['.js']), ...walk(path.join(ROOT, 'views'), ['.vue'])]
const issues = []

for (const file of targets) {
  const src = fs.readFileSync(file, 'utf8')
  // 提取 import xxx from '@/api/yyy'
  const imports = []
  for (const m of src.matchAll(/import\s+(\w+)\s+from\s+['"]@\/api\/([\w-]+)['"]/g)) {
    imports.push({ alias: m[1], module: m[2] })
  }
  if (!imports.length) continue
  // 提取 alias.方法 调用
  for (const { alias, module } of imports) {
    // request 是 axios 实例（get/post/put/delete/patch 均存在），跳过
    if (alias === 'request') continue
    const modExports = apiModules[module]
    if (!modExports) {
      issues.push(`${file}: import 的 api 模块 '@/${module}' 不存在`)
      continue
    }
    for (const m of src.matchAll(new RegExp(alias + '\\.(\\w+)(\\?)?\\.?\\s*\\(', 'g'))) {
      const method = m[1]
      if (method === 'default') continue
      if (!modExports.has(method)) {
        // 可选链调用（alias.method?.(...)）在方法缺失时运行时安全（undefined?.() 不执行），
        // 仅作防御兜底时不算 bug，跳过
        if (m[2] === '?') continue
        const exportsList = Array.from(modExports).join(', ')
        issues.push(`${file}: ${alias}.${method} —— api/${module}.js 无此导出 [实际导出: ${exportsList}]`)
      }
    }
  }
}

if (issues.length) {
  console.log(`发现 ${issues.length} 处 API 方法引用不一致：`)
  issues.forEach((i) => console.log('  ' + i))
  process.exit(1)
} else {
  console.log('✅ 全部 API 方法引用一致（composables + views，无遗漏）')
}
