/**
 * 一次性抽取：把旧端用户协议/隐私政策的正文复制到 v2 资产。
 * 旧页面仍保留；v2 页面只读这份静态快照，样式由 v2 排版层负责。
 * 用法：node scripts/extract-v2-legal.cjs
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const OUT = path.join(ROOT, 'src', 'v2', 'data', 'legal.json')

function extract(file) {
  const source = fs.readFileSync(path.join(ROOT, 'src', 'views', 'auth', file), 'utf8')
  const template = source.slice(source.indexOf('<template>'), source.indexOf('</template>'))
  const title = template.match(/<h1>([\s\S]*?)<\/h1>/)?.[1]?.trim() || ''
  const subtitle = template.match(/<p class="doc-subtitle">([\s\S]*?)<\/p>/)?.[1]?.trim() || ''
  const body = template.match(/<div class="doc-body">([\s\S]*?)<\/div>/)?.[1]?.trim() || ''
  return { title, subtitle, html: body }
}

const payload = {
  agreement: extract('Agreement.vue'),
  privacy: extract('Privacy.vue'),
}
fs.mkdirSync(path.dirname(OUT), { recursive: true })
fs.writeFileSync(OUT, `${JSON.stringify(payload)}\n`, 'utf8')
console.log(
  `[v2] legal.json ${(fs.statSync(OUT).size / 1024).toFixed(1)} KB · ` +
    `agreement ${payload.agreement.html.length} B · privacy ${payload.privacy.html.length} B`,
)
