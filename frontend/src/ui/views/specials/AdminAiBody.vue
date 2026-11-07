<template>
  <section class="admin-ai-body">
    <div class="card caveat-card">
      <p class="card-kicker">SCOPE / 口径边界</p>
      <p class="scope-line">
        当前供应商由 <code>system_configs</code> 的 <code>ai.provider</code> 决定，
        后端每次 LLM 调用实时读取——切换<b>即时生效</b>，无需重启。
      </p>
      <p class="scope-note">
        密钥等启动级配置不在本页：供应商 API Key 由环境变量注入（见部署文档），本页只做供应商选择与连通性探测。
      </p>
    </div>

    <div v-if="loadError" class="callout" role="alert">
      <span>ERROR / 当前供应商</span>
      <p>{{ loadError }}</p>
      <button class="btn btn-sm" :disabled="loading" @click="load()">
        {{ loading ? '重试中…' : '重试' }}
      </button>
    </div>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">AI PROVIDER / CURRENT</p>
          <h2>当前供应商</h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm btn-ghost" :disabled="loading" @click="load()">
            {{ loading ? '刷新中…' : '刷新' }}
          </button>
        </div>
      </header>
      <div class="current-grid">
        <div class="current-item">
          <span class="current-label">供应商</span>
          <b class="current-value">{{ current.provider || '—' }}</b>
          <small class="cell-sub">{{ current.llmName || '' }}</small>
        </div>
        <div class="current-item">
          <span class="current-label">配置状态</span>
          <span class="chip" :class="current.configured ? 'chip-ok' : 'chip-warn'">
            {{ current.configured ? '已配置' : '未配置（调用将返回 503）' }}
          </span>
        </div>
      </div>
      <p class="hint">
        「未配置」只代表当前供应商缺少 API Key / 端点，不代表平台不可用——切换到已配置的供应商即可恢复。
      </p>
    </section>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">AI PROVIDER / SWITCH</p>
          <h2>切换供应商</h2>
        </div>
      </header>
      <div class="provider-grid" role="group" aria-label="选择 LLM 供应商">
        <button
          v-for="option in PROVIDER_OPTIONS"
          :key="option.id"
          class="provider-btn"
          :class="{ active: current.provider === option.id }"
          :disabled="switching || loading"
          :aria-pressed="current.provider === option.id"
          @click="switchTo(option.id)"
        >
          <b>{{ option.label }}</b>
          <small>{{ option.desc }}</small>
        </button>
      </div>
      <p class="hint">
        切换写入 <code>ai.provider</code> 配置键；切换成功后下方可发一次最小补全验证连通性。
      </p>
    </section>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">AI PROVIDER / TEST</p>
          <h2>连通性探测</h2>
        </div>
        <div class="filter-row">
          <button class="btn btn-sm btn-primary" :disabled="testing || loading" @click="runTest()">
            {{ testing ? '探测中…' : '发送测试补全' }}
          </button>
        </div>
      </header>
      <div v-if="testResult" class="test-result" :class="testResult.ok ? 'ok' : 'bad'" role="status">
        <b>{{ testResult.ok ? '探测成功' : '探测失败' }}</b>
        <p>{{ testResult.message || (testResult.ok ? '当前供应商返回了有效补全。' : '无详细信息。') }}</p>
        <small>供应商：{{ testResult.provider || current.provider || '—' }}</small>
      </div>
      <p v-else class="hint">
        探测会向当前供应商发送一条 <code>ping</code> 消息（temperature=0），成功即代表密钥与端点可用。
      </p>
    </section>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminAiCurrent, switchAiProvider, testAiProvider } from '@/api/admin'
import { unwrap } from '@/utils/response'
import { useUi } from '../../composables/ui'

interface AdminAiCurrent {
  provider?: string | null
  llmName?: string | null
  configured?: boolean | null
}

interface AdminAiTest {
  ok?: boolean | null
  provider?: string | null
  configured?: boolean | null
  message?: string | null
}

const PROVIDER_OPTIONS = [
  { id: 'deepseek', label: 'DeepSeek', desc: 'api.deepseek.com · deepseek-chat' },
  { id: 'openai', label: 'OpenAI', desc: 'api.openai.com · gpt-4o-mini' },
  { id: 'qwen', label: '通义千问', desc: 'dashscope · qwen-plus' },
  { id: 'ollama', label: 'Ollama', desc: '本地推理 · 无需 API Key' },
] as const

const { toast } = useUi()

const current = ref<AdminAiCurrent>({})
const loading = ref(false)
const loadError = ref('')
const switching = ref(false)
const testing = ref(false)
const testResult = ref<AdminAiTest | null>(null)

function errMsg(error: unknown, fallback: string): string {
  const resp = (error as { response?: { data?: { message?: string } } })?.response
  return resp?.data?.message || fallback
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = unwrap<AdminAiCurrent>(await getAdminAiCurrent())
    if (!data || typeof data !== 'object') throw new Error('响应格式无效')
    current.value = data
  } catch (error) {
    loadError.value = errMsg(error, '当前供应商读取失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function switchTo(provider: string) {
  if (switching.value) return
  switching.value = true
  try {
    const data = unwrap<AdminAiCurrent>(await switchAiProvider(provider))
    if (data && typeof data === 'object') current.value = { ...current.value, ...data }
    testResult.value = null
    toast(`已切换到 ${data?.provider || provider}`, 'ok')
  } catch (error) {
    toast(errMsg(error, '切换失败，请稍后重试'), 'danger')
  } finally {
    switching.value = false
  }
}

async function runTest() {
  if (testing.value) return
  testing.value = true
  try {
    const data = unwrap<AdminAiTest>(await testAiProvider())
    if (!data || typeof data !== 'object') throw new Error('响应格式无效')
    testResult.value = data
  } catch (error) {
    testResult.value = { ok: false, message: errMsg(error, '探测请求失败，请稍后重试') }
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.admin-ai-body {
  display: grid;
  gap: 14px;
}
.caveat-card {
  display: grid;
  gap: 6px;
}
.scope-line {
  margin: 0;
  color: var(--text-2);
}
.scope-note {
  margin: 0;
  color: var(--text-3);
  font-size: 13px;
}
.current-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  padding: 4px 0 8px;
}
.current-item {
  display: grid;
  gap: 4px;
}
.current-label {
  font-size: 12px;
  color: var(--text-3);
}
.current-value {
  font-size: 20px;
  letter-spacing: -0.02em;
}
.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
  padding: 4px 0 8px;
}
.provider-btn {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.provider-btn small {
  color: var(--text-3);
  font-size: 12px;
}
.provider-btn:hover:not(:disabled) {
  border-color: var(--accent);
}
.provider-btn.active {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
}
.test-result {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
}
.test-result.ok {
  border-color: color-mix(in srgb, var(--ok, #16a34a) 45%, var(--line));
}
.test-result.bad {
  border-color: color-mix(in srgb, var(--danger, #dc2626) 45%, var(--line));
}
.test-result p {
  margin: 0;
  color: var(--text-2);
}
.test-result small {
  color: var(--text-3);
}
</style>
