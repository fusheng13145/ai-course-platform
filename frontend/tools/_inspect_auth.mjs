import { readFileSync } from 'node:fs'
for (const n of ['student', 'teacher', 'admin']) {
  const s = JSON.parse(readFileSync(`e2e/visual/.auth/${n}.json`, 'utf8'))
  console.log('===', n, '===')
  console.log('cookies:', (s.cookies || []).map((c) => `${c.name}@${c.domain} exp=${c.expires}`))
  for (const o of s.origins || []) {
    console.log('origin:', o.origin)
    for (const k of o.localStorage || []) {
      console.log('  ', k.name, '=', String(k.value).slice(0, 120))
    }
  }
}
