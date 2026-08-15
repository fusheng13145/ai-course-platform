<template>
  <div class="fade-in ai-supplier">
    <PageHeader
      title="AI 供应商配置"
      subtitle="管理大模型供应商、密钥与调用额度，配置持久化于服务端系统配置表"
      icon="brain"
    >
      <el-button type="primary" :loading="saving" @click="handleSave">
        <el-icon><Check /></el-icon>保存配置
      </el-button>
      <el-button type="success" :loading="switching" @click="handleSwitch">
        <el-icon><Switch /></el-icon>应用并热切换
      </el-button>
    </PageHeader>

    <div v-loading="loading">
      <!-- 说明横幅 -->
      <div class="banner animate-in d1">
        <el-icon class="banner-icon"><InfoFilled /></el-icon>
        <div class="banner-text">
          <strong>配置已支持服务端持久化 + 运行时热切换</strong>
          <span
            >所有项通过系统通用配置接口（upsert）写入 <code>system_configs</code> 表（分组
            <code>AI</code>）。
            点击「应用并热切换」即可<b>即时切换底层大模型，无需重启</b>；当前运行供应商：
            <el-tag v-if="currentProvider" size="small" type="success" effect="dark">{{
              currentProvider
            }}</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">{{ currentLlm || '—' }}</el-tag>
          </span>
        </div>
      </div>

      <!-- 基础设置 -->
      <div class="content-card animate-in d2">
        <div class="card-title">
          <el-icon><Setting /></el-icon>基础设置
        </div>
        <div class="form-grid">
          <div class="form-item">
            <label>当前启用供应商</label>
            <el-select v-model="form['ai.provider']" style="width: 100%">
              <el-option label="DeepSeek" value="deepseek" />
              <el-option label="OpenAI" value="openai" />
              <el-option label="通义千问 Qwen" value="qwen" />
              <el-option label="自定义 OpenAI 兼容" value="custom" />
            </el-select>
          </div>
          <div class="form-item">
            <label>连接超时（秒）</label>
            <el-input v-model="form['ai.connect-timeout']" type="number" />
          </div>
          <div class="form-item">
            <label>读取超时（秒）</label>
            <el-input v-model="form['ai.read-timeout']" type="number" />
          </div>
          <div class="form-item">
            <label>最大重试次数</label>
            <el-input v-model="form['ai.max-retries']" type="number" />
          </div>
          <div class="form-item">
            <label>每日调用额度（次）</label>
            <el-input v-model="form['ai.quota.daily']" type="number" />
          </div>
        </div>
      </div>

      <!-- 供应商凭据 -->
      <div class="provider-cards animate-in d3">
        <div v-for="p in providers" :key="p.key" class="content-card provider-card">
          <div class="provider-head">
            <span class="provider-dot" :style="{ background: p.color }"></span>
            <span class="provider-name">{{ p.name }}</span>
            <el-tag
              size="small"
              :type="form['ai.provider'] === p.key ? 'success' : 'info'"
              effect="light"
            >
              {{ form['ai.provider'] === p.key ? '启用中' : '未启用' }}
            </el-tag>
          </div>
          <div class="form-grid compact">
            <div class="form-item">
              <label>API Key</label>
              <el-input
                v-model="form[`ai.${p.key}.api-key`]"
                type="password"
                show-password
                placeholder="sk-..."
              />
            </div>
            <div class="form-item">
              <label>Base URL</label>
              <el-input v-model="form[`ai.${p.key}.base-url`]" placeholder="https://..." />
            </div>
            <div class="form-item">
              <label>模型</label>
              <el-input v-model="form[`ai.${p.key}.model`]" placeholder="model-name" />
            </div>
          </div>
          <el-button class="test-btn" plain :loading="testing" @click="handleTest(p.key)">
            <el-icon><Connection /></el-icon>测试连通性
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, InfoFilled, Setting, Connection, Switch } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import aiSupplierApi from '@/api/aiSupplier'

const saving = ref(false)
const switching = ref(false)
const testing = ref(false)
const loading = ref(false)
const currentProvider = ref('')
const currentLlm = ref('')

const providers = [
  { key: 'deepseek', name: 'DeepSeek', color: 'var(--violet)' },
  { key: 'openai', name: 'OpenAI', color: 'var(--ok)' },
  { key: 'qwen', name: '通义千问 Qwen', color: 'var(--info)' },
]

const SCHEMA = [
  'ai.provider',
  'ai.connect-timeout',
  'ai.read-timeout',
  'ai.max-retries',
  'ai.quota.daily',
  'ai.deepseek.api-key',
  'ai.deepseek.base-url',
  'ai.deepseek.model',
  'ai.openai.api-key',
  'ai.openai.base-url',
  'ai.openai.model',
  'ai.qwen.api-key',
  'ai.qwen.base-url',
  'ai.qwen.model',
]

const DEFAULTS = {
  'ai.provider': 'deepseek',
  'ai.connect-timeout': '5',
  'ai.read-timeout': '60',
  'ai.max-retries': '2',
  'ai.quota.daily': '1000',
  'ai.deepseek.api-key': '',
  'ai.deepseek.base-url': 'https://api.deepseek.com',
  'ai.deepseek.model': 'deepseek-chat',
  'ai.openai.api-key': '',
  'ai.openai.base-url': 'https://api.openai.com/v1',
  'ai.openai.model': 'gpt-4o-mini',
  'ai.qwen.api-key': '',
  'ai.qwen.base-url': 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  'ai.qwen.model': 'qwen-plus',
}

const form = reactive({})

function resetForm() {
  SCHEMA.forEach((k) => {
    form[k] = DEFAULTS[k]
  })
}

async function loadConfigs() {
  try {
    const res = await aiSupplierApi.getConfigs()
    const list = res.data || []
    const map = {}
    list.forEach((c) => {
      map[c.configKey || c.key] = c.configValue ?? c.value
    })
    SCHEMA.forEach((k) => {
      if (map[k] !== undefined && map[k] !== null) form[k] = String(map[k])
    })
  } catch (e) {
    logger.error('加载 AI 配置失败:', e)
  }
}

async function handleSave() {
  saving.value = true
  try {
    const tasks = SCHEMA.map((k) => aiSupplierApi.saveConfig(k, String(form[k] ?? '')))
    await Promise.all(tasks)
    ElMessage.success('AI 供应商配置已保存至服务端')
    await loadCurrent()
  } catch (e) {
    logger.error(e)
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 运行时热切换：将当前所选供应商即时生效，无需重启
async function handleSwitch() {
  switching.value = true
  try {
    const res = await aiSupplierApi.switchProvider(form['ai.provider']).catch((err) => {
      throw new Error(err?.response?.data?.message || '切换失败')
    })
    const p = res?.data?.provider
    ElMessage.success('已切换至「' + (p || form['ai.provider']) + '」，即时生效')
    await loadCurrent()
  } catch (e) {
    logger.error(e)
    ElMessage.error('热切换失败：' + (e.message || '未知错误'))
  } finally {
    switching.value = false
  }
}

// 连通性测试：用当前供应商做一次最小补全
async function handleTest(key) {
  testing.value = true
  try {
    const res = await aiSupplierApi.testConnection().catch((err) => {
      throw new Error(err?.response?.data?.message || '测试失败')
    })
    const d = res?.data || {}
    if (d.ok) {
      ElMessage.success('「' + (key || d.provider || '当前供应商') + '」连通正常')
    } else {
      ElMessage.warning(d.message || '连通性测试未通过')
    }
  } catch (e) {
    logger.error(e)
    ElMessage.error('测试失败：' + (e.message || '未知错误'))
  } finally {
    testing.value = false
  }
}

async function loadCurrent() {
  try {
    const res = await aiSupplierApi.getCurrent().catch(() => null)
    const d = res?.data
    if (d) {
      currentProvider.value = d.provider || ''
      currentLlm.value = d.llmName || ''
    }
  } catch (e) {
    logger.error('读取当前供应商失败:', e)
  }
}

onMounted(() => {
  resetForm()
  loading.value = true
  Promise.all([loadConfigs(), loadCurrent()]).finally(() => {
    loading.value = false
  })
})
</script>

<style scoped>
.ai-supplier {
  max-width: 1400px;
  margin: 0 auto;
}

.banner {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: color-mix(in srgb, var(--ac-primary, #3b82f6) 8%, var(--ac-bg-elevated));
  border: 1px solid color-mix(in srgb, var(--ac-primary, #3b82f6) 25%, transparent);
  border-radius: var(--ac-radius-md);
  padding: 14px 16px;
  margin-bottom: var(--ac-spacing-lg);
}
.banner-icon {
  color: var(--ac-primary, #3b82f6);
  font-size: 18px;
  margin-top: 2px;
}
.banner-text {
  font-size: 13px;
  color: var(--ac-text-secondary, #475569);
  line-height: 1.6;
}
.banner-text strong {
  color: var(--ac-text-primary);
}
.banner-text code {
  background: rgba(127, 127, 127, 0.12);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}

.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px;
  margin-bottom: var(--ac-spacing-lg);
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 16px;
}
.card-title .el-icon {
  color: var(--ac-primary, #3b82f6);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.form-grid.compact {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-item label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.provider-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: var(--ac-spacing-lg);
}
.provider-card {
  margin-bottom: 0;
}
.provider-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.provider-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.provider-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  flex: 1;
}
.test-btn {
  width: 100%;
  margin-top: 4px;
}

@media (max-width: 768px) {
  .provider-cards {
    grid-template-columns: 1fr;
  }
}
</style>
