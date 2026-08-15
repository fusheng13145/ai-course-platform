<template>
  <div class="fade-in knowledge-base">
    <PageHeader
      title="知识库管理"
      subtitle="按课程维护 RAG 知识库：导入文档语料、召回测试、智能问答联调，支撑「基于知识库的 AI 助教」"
      icon="layers"
      icon-bg="accent"
    />

    <!-- 模型状态横幅 -->
    <div class="model-banner" :class="bannerClass">
      <el-icon class="banner-icon"><component :is="bannerIcon" /></el-icon>
      <span>{{ bannerText }}</span>
    </div>

    <div class="stat-grid animate-in d1">
      <StatCard
        label="知识库文档数"
        :value="docs.length"
        icon="doc"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="当前课程"
        :value="currentCourseName || '未选择'"
        icon="building"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="可管理课程"
        :value="courseList.length"
        icon="list"
        tone="var(--violet-soft)"
        tone-color="var(--violet)"
      />
      <StatCard
        label="生成模型"
        :value="modelLabel"
        icon="info"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <!-- 工具栏 -->
    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico ico-accent">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
          </svg>
        </span>
        <div>
          <div class="l1-title">知识库文档</div>
          <div class="l1-sub">按课程检索与维护文档语料</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="content-card filter-card">
          <div class="filter-row">
            <el-select
              v-model="selectedCourseId"
              placeholder="选择课程"
              filterable
              class="w-260"
              @change="onCourseChange"
            >
              <el-option
                v-for="c in courseList"
                :key="c.id"
                :label="c.name || c.title"
                :value="c.id"
              />
            </el-select>
            <el-button type="primary" :disabled="!selectedCourseId" @click="openAdd">
              <el-icon><Plus /></el-icon>新建文档
            </el-button>
            <el-button :disabled="!selectedCourseId" :loading="indexing" @click="buildIndex">
              <el-icon><Refresh /></el-icon>重建索引
            </el-button>
            <el-button :disabled="!selectedCourseId" :loading="indexing" @click="clearIndex">
              <el-icon><Delete /></el-icon>清空索引
            </el-button>
            <div class="spacer"></div>
            <el-input
              v-model="keyword"
              placeholder="搜索文档名称"
              clearable
              class="w-200"
              @keyup.enter="loadDocs"
            />
          </div>
        </div>

        <!-- 文档表格 -->
        <div class="content-card">
          <el-table
            v-loading="loading"
            :data="filteredDocs"
            style="width: 100%"
            empty-text="请选择课程后查看知识库文档"
          >
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="文档名称" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="doc-name">
                  <el-icon class="doc-icon"><Document /></el-icon>
                  {{ row.name || '未命名' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120" align="center">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.type === 'DOCUMENT' ? 'primary' : 'info'"
                  effect="plain"
                >
                  {{ row.type === 'DOCUMENT' ? '文档' : '知识点' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="内容预览" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ preview(row.description) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <div class="action-row">
                  <el-button text type="primary" size="small" @click="openEdit(row)"
                    >编辑</el-button
                  >
                  <el-button text type="danger" size="small" @click="removeDoc(row)"
                    >删除</el-button
                  >
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="请选择课程后查看知识库文档">
                <template #image><div class="empty-deco">📚</div></template>
              </el-empty>
            </template>
          </el-table>
        </div>
      </div>
    </section>

    <!-- RAG 联调面板 -->
    <section class="l1-card animate-in d3">
      <div class="l1-head">
        <span class="l1-ico"
          ><svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M12 2a3 3 0 0 0-3 3v1a3 3 0 0 0-3 3 3 3 0 0 0 0 6 3 3 0 0 0 3 3v1a3 3 0 0 0 3 3 3 3 0 0 0 3-3v-1a3 3 0 0 0 3-3 3 3 0 0 0 0-6 3 3 0 0 0-3-3V5a3 3 0 0 0-3-3z"
            /></svg
        ></span>
        <div><h3 class="l1-title">RAG 联调测试</h3></div>
      </div>
      <div class="l1-body">
        <div class="playground">
          <div class="content-card playground-card">
            <div class="card-title">
              <el-icon><ChatDotRound /></el-icon><span>智能问答测试</span>
            </div>
            <p class="card-hint">
              基于所选课程知识库作答；未配置大模型 Key 时回退展示知识库原文（抑制幻觉）。
            </p>
            <div class="qa-input">
              <el-input
                v-model="askForm.question"
                placeholder="输入问题，如：什么是反向传播？"
                @keyup.enter="doAsk"
              />
              <el-input-number
                v-model="askForm.topK"
                :min="1"
                :max="10"
                controls-position="right"
                class="w-110"
              />
              <el-button
                type="primary"
                :loading="asking"
                :disabled="!selectedCourseId"
                @click="doAsk"
                >提问</el-button
              >
            </div>
            <div v-if="askResult" class="qa-result">
              <div class="qa-answer"><strong>回答：</strong>{{ askResult.answer }}</div>
              <div v-if="askResult.sources && askResult.sources.length" class="qa-sources">
                <strong>参考知识点：</strong>
                <span v-for="s in askResult.sources" :key="s.id" class="src-chip"
                  >{{ s.name }} <em>({{ s.score }})</em></span
                >
              </div>
              <div class="qa-model">
                模型：<el-tag size="small" effect="plain">{{ askResult.model }}</el-tag>
              </div>
            </div>
          </div>

          <div class="content-card playground-card">
            <div class="card-title">
              <el-icon><Search /></el-icon><span>语义召回测试</span>
            </div>
            <p class="card-hint">基于词频/IDF 加权的中文 bigram 余弦相似度召回 topK 知识点。</p>
            <div class="qa-input">
              <el-input
                v-model="retrieveForm.query"
                placeholder="输入查询词，如：梯度下降"
                @keyup.enter="doRetrieve"
              />
              <el-input-number
                v-model="retrieveForm.topK"
                :min="1"
                :max="10"
                controls-position="right"
                class="w-110"
              />
              <el-button
                type="primary"
                :loading="retrieving"
                :disabled="!selectedCourseId"
                @click="doRetrieve"
                >召回</el-button
              >
            </div>
            <div v-if="retrieveResult" class="retrieve-result">
              <div class="retrieve-count">命中 {{ retrieveResult.count }} 条</div>
              <div v-for="r in retrieveResult.results" :key="r.id" class="retrieve-item">
                <div class="retrieve-head">
                  <span class="retrieve-name">{{ r.name }}</span>
                  <span class="retrieve-score">相似度 {{ r.score }}</span>
                </div>
                <div class="retrieve-desc">{{ preview(r.description) }}</div>
              </div>
              <el-empty
                v-if="!retrieveResult.count"
                description="未召回相关知识点，建议补充知识库语料"
                :image-size="80"
              >
                <template #image><div class="empty-deco">🔍</div></template>
              </el-empty>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="showDialog"
      :title="dialogMode === 'add' ? '新建知识库文档' : '编辑文档内容'"
      width="560px"
      append-to-body
    >
      <el-form label-width="80px">
        <el-form-item label="所属课程">
          <el-input :model-value="currentCourseName" disabled />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'add'" label="文档名称">
          <el-input v-model="form.name" placeholder="如：第3章 神经网络基础" />
        </el-form-item>
        <el-form-item label="文档内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="粘贴或输入作为知识库语料的文本，将用于语义召回与问答生成"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDoc">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Refresh,
  Delete,
  Document,
  Search,
  ChatDotRound,
  Cpu,
  Warning,
  CircleCheck,
} from '@element-plus/icons-vue'
import ragApi from '@/api/rag'
import courseApi from '@/api/course'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
const indexing = ref(false)
const asking = ref(false)
const retrieving = ref(false)
const saving = ref(false)

const courseList = ref([])
const selectedCourseId = ref(null)
const docs = ref([])
const keyword = ref('')

const currentCourseName = computed(() => {
  const c = courseList.value.find((x) => x.id === selectedCourseId.value)
  return c ? c.name || c.title : ''
})

/* 模型状态检测 */
const modelLabel = ref('检测中…')
const llmReady = ref(null) // null=未知, true=已配置, false=检索态
const bannerClass = computed(() => {
  if (llmReady.value === false) return 'banner--warn'
  if (llmReady.value === true) return 'banner--ok'
  return 'banner--unknown'
})
const bannerIcon = computed(() =>
  llmReady.value === false ? Warning : llmReady.value === true ? CircleCheck : Cpu,
)
const bannerText = computed(() => {
  if (llmReady.value === true) return '大模型 API Key 已配置，问答将基于知识库融合生成。'
  if (llmReady.value === false)
    return '未检测到大模型 API Key：问答将回退为「知识库原文展示」（抑制幻觉，保证可用）。配置环境变量 DEEPSEEK_API_KEY / OPENAI_API_KEY / QWEN_API_KEY 并重启后端即可启用生成态。'
  return '正在检测大模型配置状态…'
})

const filteredDocs = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return docs.value
  return docs.value.filter(
    (d) =>
      (d.name || '').toLowerCase().includes(q) || (d.description || '').toLowerCase().includes(q),
  )
})

function preview(text) {
  if (!text) return '—'
  return text.length > 80 ? text.slice(0, 80) + '…' : text
}

/* 加载课程 */
async function loadCourses() {
  try {
    const res = await courseApi.getCourseList({ size: 500 })
    const raw = res.data?.records || res.data?.list || res.data || res
    courseList.value = Array.isArray(raw) ? raw : []
    if (courseList.value.length && !selectedCourseId.value) {
      selectedCourseId.value = courseList.value[0].id
      await loadDocs()
    }
  } catch (e) {
    logger.error('加载课程失败:', e)
    courseList.value = []
  }
}

/* 加载文档 */
async function loadDocs() {
  if (!selectedCourseId.value) {
    docs.value = []
    return
  }
  loading.value = true
  try {
    const res = await ragApi.ragListDocuments(selectedCourseId.value)
    if (res && res.status === 'error') {
      ElMessage.error(res.message || '加载知识库失败')
      docs.value = []
    } else {
      docs.value = Array.isArray(res?.documents) ? res.documents : []
    }
  } catch (e) {
    logger.error('加载知识库失败:', e)
    docs.value = []
    ElMessage.error('加载知识库失败')
  } finally {
    loading.value = false
  }
}

function onCourseChange() {
  loadDocs()
}

/* 新增 / 编辑 */
const showDialog = ref(false)
const dialogMode = ref('add')
const form = reactive({ id: null, name: '', content: '' })

function openAdd() {
  dialogMode.value = 'add'
  form.id = null
  form.name = ''
  form.content = ''
  showDialog.value = true
}
function openEdit(row) {
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name
  form.content = row.description || ''
  showDialog.value = true
}
async function saveDoc() {
  if (dialogMode.value === 'add') {
    if (!form.name.trim()) {
      ElMessage.warning('请输入文档名称')
      return
    }
    if (!form.content.trim()) {
      ElMessage.warning('请输入文档内容')
      return
    }
    saving.value = true
    try {
      const res = await ragApi.ragAddDocument(
        selectedCourseId.value,
        form.name.trim(),
        form.content.trim(),
      )
      if (res && res.status === 'error') {
        ElMessage.error(res.message || '新增失败')
        return
      }
      ElMessage.success('文档已加入知识库')
      showDialog.value = false
      await loadDocs()
    } catch (e) {
      ElMessage.error('新增失败')
    } finally {
      saving.value = false
    }
  } else {
    if (!form.content.trim()) {
      ElMessage.warning('请输入文档内容')
      return
    }
    saving.value = true
    try {
      const res = await ragApi.ragUpdateDocument(form.id, form.content.trim())
      if (res && res.status === 'error') {
        ElMessage.error(res.message || '更新失败')
        return
      }
      ElMessage.success('文档已更新')
      showDialog.value = false
      await loadDocs()
    } catch (e) {
      ElMessage.error('更新失败')
    } finally {
      saving.value = false
    }
  }
}
function removeDoc(row) {
  ElMessageBox.confirm(`确定从知识库删除「${row.name || '未命名'}」？`, '删除确认', {
    type: 'warning',
  })
    .then(async () => {
      try {
        const res = await ragApi.ragDeleteDocument(row.id)
        if (res && res.status === 'error') {
          ElMessage.error(res.message || '删除失败')
          return
        }
        ElMessage.success('已删除')
        await loadDocs()
      } catch (e) {
        ElMessage.error('删除失败')
      }
    })
    .catch(() => {})
}

/* 索引 */
async function buildIndex() {
  indexing.value = true
  try {
    const res = await ragApi.ragBuildIndex(selectedCourseId.value)
    ElMessage.success((res && res.note) || '索引已构建（知识库实时构建，无需离线处理）')
  } catch (e) {
    ElMessage.error('构建索引失败')
  } finally {
    indexing.value = false
  }
}
async function clearIndex() {
  indexing.value = true
  try {
    const res = await ragApi.ragClearIndex(selectedCourseId.value)
    ElMessage.success((res && res.note) || '索引已清理')
  } catch (e) {
    ElMessage.error('清理索引失败')
  } finally {
    indexing.value = false
  }
}

/* 问答 */
const askForm = reactive({ question: '', topK: 3 })
const askResult = ref(null)
async function doAsk() {
  if (!askForm.question.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  asking.value = true
  askResult.value = null
  try {
    const res = await ragApi.ragAsk({
      question: askForm.question.trim(),
      courseId: selectedCourseId.value,
      topK: askForm.topK,
    })
    askResult.value = res || {}
    if (res && res.model && res.model !== 'retrieval-only') llmReady.value = true
    else if (res) llmReady.value = false
  } catch (e) {
    ElMessage.error('提问失败')
  } finally {
    asking.value = false
  }
}

/* 召回 */
const retrieveForm = reactive({ query: '', topK: 5 })
const retrieveResult = ref(null)
async function doRetrieve() {
  if (!retrieveForm.query.trim()) {
    ElMessage.warning('请输入查询词')
    return
  }
  retrieving.value = true
  retrieveResult.value = null
  try {
    const res = await ragApi.ragRetrieve({
      query: retrieveForm.query.trim(),
      courseId: selectedCourseId.value,
      topK: retrieveForm.topK,
    })
    retrieveResult.value = res || {}
  } catch (e) {
    ElMessage.error('召回失败')
  } finally {
    retrieving.value = false
  }
}

onMounted(loadCourses)
</script>

<style scoped>
.knowledge-base {
  max-width: 1400px;
  margin: 0 auto;
}

.model-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--ac-radius-md);
  font-size: 13px;
  margin-bottom: var(--ac-spacing-lg);
  border: 1px solid var(--ac-border);
}
.model-banner .banner-icon {
  font-size: 16px;
}
.banner--warn {
  background: var(--warn-soft);
  border-color: var(--warn);
  color: var(--warn);
}
.banner--ok {
  background: var(--ok-soft);
  border-color: var(--ok);
  color: var(--ok);
}
.banner--unknown {
  background: var(--ac-bg-elevated);
  color: var(--ac-text-muted);
}

.ico-accent {
  background: var(--accent-soft);
  color: var(--accent);
}

.w-260 {
  width: 260px;
}
.w-200 {
  width: 200px;
}
.w-110 {
  width: 110px;
}

.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.filter-card {
  margin-bottom: var(--ac-spacing-md);
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}

.doc-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.doc-icon {
  color: var(--ac-text-muted);
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.playground {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ac-spacing-md);
  margin-top: var(--ac-spacing-md);
}
.playground-card {
  display: flex;
  flex-direction: column;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.card-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin: 6px 0 12px;
}
.qa-input {
  display: flex;
  gap: 8px;
  align-items: center;
}
.qa-result {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--ac-border);
}
.qa-answer {
  font-size: 14px;
  line-height: 1.7;
  color: var(--ac-text-primary);
  white-space: pre-wrap;
}
.qa-sources {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.src-chip {
  background: var(--ac-bg-subtle, #f1f5f9);
  border-radius: 6px;
  padding: 2px 8px;
  font-size: 12px;
  color: var(--ac-text-secondary);
}
.src-chip em {
  color: var(--ac-text-muted);
  font-style: normal;
}
.qa-model {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ac-text-muted);
}
.retrieve-result {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--ac-border);
  max-height: 320px;
  overflow-y: auto;
}
.retrieve-count {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-bottom: 8px;
}
.retrieve-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--ac-bg-subtle, #f8fafc);
  margin-bottom: 8px;
}
.retrieve-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.retrieve-name {
  font-weight: 600;
  font-size: 13px;
  color: var(--ac-text-primary);
}
.retrieve-score {
  font-size: 12px;
  color: var(--info);
}
.retrieve-desc {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 4px;
  line-height: 1.5;
}

/* 空状态装饰 */
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 900px) {
  .playground {
    grid-template-columns: 1fr;
  }
}
</style>
