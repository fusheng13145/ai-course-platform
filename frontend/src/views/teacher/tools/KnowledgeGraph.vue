<template>
  <div class="kg-page fade-in">
    <!-- 顶部课程选择器 -->
    <PageHeader title="知识图谱" subtitle="管理课程知识点、AI 智能抽取并可视化知识关系" icon="link">
      <el-button @click="openExtractDialog">
        <el-icon><MagicStick /></el-icon>AI 抽取知识点
      </el-button>
      <el-select
        v-model="courseId"
        placeholder="请选择课程"
        filterable
        clearable
        class="course-select"
        :loading="courseLoading"
        @change="onCourseChange"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </PageHeader>

    <div v-loading="loading">
      <el-alert v-if="!courseId" title="请选择课程" type="info" :closable="false" show-icon>
        <template #default>选择一门课程后，即可查看知识点概览、管理知识点并生成图谱。</template>
      </el-alert>

      <template v-else>
        <!-- 概览统计 -->
        <div class="stat-grid animate-in">
          <StatCard
            label="知识点总数"
            :value="overview.totalPoints || 0"
            icon="tag"
            tone="var(--accent-soft)"
            tone-color="var(--accent)"
          />
          <StatCard
            label="关系总数"
            :value="overview.totalRelations || 0"
            icon="link"
            tone="var(--ok-soft)"
            tone-color="var(--ok)"
          />
          <StatCard
            label="核心知识点"
            :value="overview.corePoints || 0"
            icon="star"
            tone="var(--warn-soft)"
            tone-color="var(--warn)"
          />
          <StatCard
            label="覆盖度"
            :value="overview.coverageRate ? pct(overview.coverageRate) : '0%'"
            icon="target"
            tone="var(--info-soft)"
            tone-color="var(--info)"
          />
        </div>

        <div class="kg-grid">
          <!-- 知识点列表 -->
          <div class="chart-card">
            <div class="chart-title">
              <el-icon><Files /></el-icon>知识点
              <el-tag size="small" type="info" effect="light" round>{{ points.length }}</el-tag>
              <el-button text type="primary" size="small" class="ml-auto" @click="openForm()">
                <el-icon><Plus /></el-icon>新建
              </el-button>
            </div>
            <div v-loading="pointLoading" class="kp-list">
              <div v-for="p in points" :key="p.id" class="kp-item">
                <div class="kp-head">
                  <span class="kp-name">{{ p.name }}</span>
                  <el-tag size="small" :type="diffType(p.difficulty)" effect="light">{{
                    diffText(p.difficulty)
                  }}</el-tag>
                </div>
                <p class="kp-desc">{{ p.description || '暂无描述' }}</p>
                <div class="kp-foot">
                  <el-rate v-model="p.importance" disabled :max="3" size="small" />
                  <div class="kp-actions">
                    <el-button text type="primary" size="small" @click="openForm(p)"
                      >编辑</el-button
                    >
                    <el-button text type="danger" size="small" @click="removePoint(p)"
                      >删除</el-button
                    >
                  </div>
                </div>
              </div>
              <el-empty
                v-if="!pointLoading && points.length === 0"
                description="暂无知识点"
                :image-size="64"
              />
            </div>
          </div>

          <!-- 图谱可视化 -->
          <div class="chart-card">
            <div class="chart-title">
              <el-icon><Share /></el-icon>图谱可视化
              <el-button
                text
                type="primary"
                size="small"
                class="ml-auto"
                :loading="graphLoading"
                @click="loadGraph"
              >
                <el-icon><Refresh /></el-icon>刷新
              </el-button>
            </div>
            <div v-loading="graphLoading" class="graph-wrap">
              <svg
                v-if="graphNodes.length"
                :viewBox="`0 0 ${graphSize} ${graphSize}`"
                class="kg-svg"
              >
                <line
                  v-for="(l, i) in graphLinks"
                  :key="'l' + i"
                  :x1="nodePos[l.source].x"
                  :y1="nodePos[l.source].y"
                  :x2="nodePos[l.target].x"
                  :y2="nodePos[l.target].y"
                  class="kg-link"
                />
                <g v-for="n in graphNodes" :key="n.id">
                  <circle
                    :cx="nodePos[n.id].x"
                    :cy="nodePos[n.id].y"
                    :r="circleR"
                    :fill="n.core ? 'var(--ac-primary)' : 'var(--ac-primary-light)'"
                    class="kg-node"
                  />
                  <text :x="nodePos[n.id].x" :y="nodePos[n.id].y - circleR - 6" class="kg-label">
                    {{ n.name }}
                  </text>
                </g>
              </svg>
              <el-empty v-else description="暂无图谱数据" :image-size="64" />
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 新建/编辑知识点 -->
    <el-dialog
      v-model="formDialog"
      :title="isEdit ? '编辑知识点' : '新建知识点'"
      width="520px"
      append-to-body
    >
      <el-form :model="kpForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="kpForm.name" placeholder="知识点名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="kpForm.description"
            type="textarea"
            :rows="3"
            placeholder="知识点描述"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="kpForm.difficulty" class="w-100">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="重要度">
          <el-rate v-model="kpForm.importance" :max="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 抽取 -->
    <el-dialog v-model="extractDialog" title="AI 抽取知识点" width="620px" append-to-body>
      <el-input
        v-model="extractText"
        type="textarea"
        :rows="7"
        placeholder="粘贴课程内容文本，AI 将自动抽取知识点与关系…"
      />
      <div class="extract-actions">
        <el-button type="primary" :loading="extractLoading" @click="runExtract">
          <el-icon><MagicStick /></el-icon>开始抽取
        </el-button>
      </div>
      <div v-if="extractResult" v-loading="extractLoading" class="extract-result">
        <div class="er-stats">
          <div class="er-stat">
            <b>{{ extractResult.nodeCount ?? (extractResult.nodes?.length || 0) }}</b
            ><span>知识点</span>
          </div>
          <div class="er-stat">
            <b>{{ extractResult.relationCount ?? (extractResult.relations?.length || 0) }}</b
            ><span>关系</span>
          </div>
        </div>
        <div v-if="extractNodes.length" class="er-section">
          <div class="er-sec-title">抽取出的知识点</div>
          <div class="er-chips">
            <el-tag
              v-for="(n, i) in extractNodes"
              :key="i"
              class="er-chip"
              type="primary"
              effect="light"
              >{{ nodeName(n) }}</el-tag
            >
          </div>
        </div>
        <div v-if="extractRelations.length" class="er-section">
          <div class="er-sec-title">抽取出的关系</div>
          <ul class="er-rels">
            <li v-for="(r, i) in extractRelations" :key="i">{{ relText(r) }}</li>
          </ul>
        </div>
        <div v-if="extractRaw" class="er-json">
          <div class="er-sec-title">JSON 概览</div>
          <pre>{{ extractRaw }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import courseApi from '@/api/course'
import knowledgeApi from '@/api/knowledge'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { Share, Files, Plus, Refresh, MagicStick } from '@element-plus/icons-vue'

const courseLoading = ref(false)
const courseOptions = ref([])
const courseId = ref('')
const loading = ref(false)

/* ---------- 课程选择器 ---------- */
async function loadCourses() {
  courseLoading.value = true
  try {
    const r = await courseApi.getMyCourses()
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    courseOptions.value = Array.isArray(list) ? list : []
    if (courseOptions.value.length && !courseId.value) courseId.value = courseOptions.value[0].id
  } catch (e) {
    logger.error('加载课程列表失败', e)
  } finally {
    courseLoading.value = false
  }
}

function onCourseChange() {
  overview.value = {}
  points.value = []
  graphNodes.value = []
  graphLinks.value = []
  if (courseId.value) {
    loadOverview()
    loadPoints()
    loadGraph()
  }
}

function pct(v) {
  if (v == null) return '--'
  if (typeof v === 'number' && v <= 1.0001 && v >= 0) return Math.round(v * 100) + '%'
  return v + '%'
}
function diffType(d) {
  return d === 'HARD' ? 'danger' : d === 'MEDIUM' ? 'warning' : 'success'
}
function diffText(d) {
  return d === 'HARD' ? '困难' : d === 'MEDIUM' ? '中等' : '简单'
}

/* ---------- 概览 ---------- */
const overview = ref({})
async function loadOverview() {
  if (!courseId.value) return
  try {
    const r = await knowledgeApi.getKnowledgeOverview({ courseId: courseId.value })
    const body = unwrap(r)
    overview.value = unwrap(body) || {}
  } catch (e) {
    logger.error('加载知识概览失败', e)
  }
}

/* ---------- 知识点列表 ---------- */
const pointLoading = ref(false)
const points = ref([])
async function loadPoints() {
  if (!courseId.value) return
  pointLoading.value = true
  try {
    const r = await knowledgeApi.getKnowledgePoints(courseId.value)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    points.value = (Array.isArray(list) ? list : []).map((k) => ({
      ...k,
      importance: k.importance ?? k.weight ?? 1,
    }))
  } catch (e) {
    logger.error('加载知识点失败', e)
  } finally {
    pointLoading.value = false
  }
}

/* 新建/编辑 */
const formDialog = ref(false)
const isEdit = ref(false)
const formSubmitting = ref(false)
const editingId = ref(null)
const kpForm = reactive({ name: '', description: '', difficulty: 'MEDIUM', importance: 2 })

function openForm(row) {
  if (row) {
    isEdit.value = true
    editingId.value = row.id
    kpForm.name = row.name
    kpForm.description = row.description || ''
    kpForm.difficulty = row.difficulty || 'MEDIUM'
    kpForm.importance = row.importance ?? 2
  } else {
    isEdit.value = false
    editingId.value = null
    kpForm.name = ''
    kpForm.description = ''
    kpForm.difficulty = 'MEDIUM'
    kpForm.importance = 2
  }
  formDialog.value = true
}

async function submitForm() {
  if (!kpForm.name.trim()) return ElMessage.warning('请输入名称')
  if (!courseId.value) return ElMessage.warning('请先选择课程')
  formSubmitting.value = true
  try {
    const payload = {
      name: kpForm.name.trim(),
      description: kpForm.description.trim(),
      difficulty: kpForm.difficulty,
      importance: kpForm.importance,
      courseId: courseId.value,
    }
    if (isEdit.value) await knowledgeApi.updateKnowledgePoint(editingId.value, payload)
    else await knowledgeApi.createKnowledgePoint(payload)
    ElMessage.success(isEdit.value ? '已更新' : '已创建')
    formDialog.value = false
    loadPoints()
    loadOverview()
  } catch (e) {
    logger.error('保存知识点失败', e)
  } finally {
    formSubmitting.value = false
  }
}

async function removePoint(row) {
  try {
    await ElMessageBox.confirm(`确认删除知识点「${row.name}」？`, '提示', { type: 'warning' })
    await knowledgeApi.deleteKnowledgePoint(row.id)
    ElMessage.success('已删除')
    loadPoints()
    loadOverview()
    loadGraph()
  } catch (e) {
    if (e !== 'cancel' && e?.type !== 'cancel') logger.error('删除知识点失败', e)
  }
}

/* ---------- AI 抽取 ---------- */
const extractDialog = ref(false)
const extractText = ref('')
const extractLoading = ref(false)
const extractResult = ref(null)
const extractRaw = ref('')

const extractNodes = computed(() => {
  const r = extractResult.value
  if (!r) return []
  return r.nodes || r.points || []
})
const extractRelations = computed(() => {
  const r = extractResult.value
  if (!r) return []
  return r.relations || r.links || []
})
function nodeName(n) {
  return n?.name || n?.label || n?.text || String(n)
}
function relText(r) {
  if (typeof r === 'string') return r
  return `${r.sourceName || r.source || ''} —${r.type || '关联'}→ ${r.targetName || r.target || ''}`
}

function openExtractDialog() {
  if (!courseId.value) return ElMessage.warning('请先选择课程')
  extractText.value = ''
  extractResult.value = null
  extractRaw.value = ''
  extractDialog.value = true
}

async function runExtract() {
  if (!extractText.value.trim()) return ElMessage.warning('请粘贴课程内容文本')
  if (!courseId.value) return
  extractLoading.value = true
  try {
    const r = await knowledgeApi.extractKnowledgePoints({
      courseId: courseId.value,
      content: extractText.value.trim(),
    })
    const body = unwrap(r)
    // 兼容多种返回结构
    const res = unwrap(body) || {}
    extractResult.value = res
    extractRaw.value = JSON.stringify(
      {
        nodeCount: res.nodeCount ?? (res.nodes || res.points || []).length,
        relationCount: res.relationCount ?? (res.relations || res.links || []).length,
      },
      null,
      2,
    )
    ElMessage.success('抽取完成')
  } catch (e) {
    logger.error('AI 抽取失败', e)
  } finally {
    extractLoading.value = false
  }
}

/* ---------- 图谱 SVG ---------- */
const graphLoading = ref(false)
const graphSize = 420
const circleR = 26
const graphNodes = ref([])
const graphLinks = ref([])

const nodePos = computed(() => {
  const map = {}
  const n = graphNodes.value.length
  const cx = graphSize / 2
  const cy = graphSize / 2
  const radius = graphSize / 2 - 56
  graphNodes.value.forEach((node, i) => {
    const angle = (Math.PI * 2 * i) / Math.max(n, 1) - Math.PI / 2
    map[node.id] = { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) }
  })
  return map
})

async function loadGraph() {
  if (!courseId.value) return
  graphLoading.value = true
  try {
    const r = await knowledgeApi.getRelationGraphData(courseId.value)
    const body = unwrap(r)
    const g = unwrap(body) || {}
    const nodes = g.nodes || []
    const links = g.links || g.relations || []
    graphNodes.value = nodes
    graphLinks.value = links
      .map((l) => ({
        source: l.source ?? l.from,
        target: l.target ?? l.to,
      }))
      .filter((l) => nodePos.value[l.source] && nodePos.value[l.target])
  } catch (e) {
    // 端点不可用则忽略可视化，仅保留列表
    logger.warn('图谱端点不可用，已忽略可视化', e)
    graphNodes.value = []
    graphLinks.value = []
  } finally {
    graphLoading.value = false
  }
}

onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.course-select {
  width: 240px;
}
.ml-auto {
  margin-left: auto;
}
.w-100 {
  width: 100%;
}

.kg-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ac-spacing-md);
}
.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.kp-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 460px;
  overflow-y: auto;
}
.kp-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.kp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.kp-name {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.kp-desc {
  margin: 6px 0 10px;
  font-size: 12.5px;
  color: var(--ac-text-muted);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kp-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kp-actions {
  display: flex;
  gap: 4px;
}

.graph-wrap {
  width: 100%;
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kg-svg {
  width: 100%;
  max-width: 420px;
  height: auto;
}
.kg-link {
  stroke: var(--ac-border-strong);
  stroke-width: 1.5;
}
.kg-node {
  stroke: var(--ac-on-primary);
  stroke-width: 2;
  transition: fill 0.2s ease;
}
.kg-label {
  font-size: 11px;
  fill: var(--ac-text-secondary);
  text-anchor: middle;
}

.extract-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.extract-result {
  margin-top: 14px;
}
.er-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.er-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px;
  border-radius: var(--ac-radius-md);
  background: var(--ac-primary-bg);
}
.er-stat b {
  font-size: 22px;
  color: var(--ac-primary);
}
.er-stat span {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}
.er-section {
  margin-bottom: 12px;
}
.er-sec-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}
.er-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.er-chip {
  margin: 0;
}
.er-rels {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.9;
}
.er-json pre {
  background: var(--ac-surface-2);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 12px;
  font-size: 12px;
  color: var(--ac-text-secondary);
  overflow-x: auto;
}

@media (max-width: 1100px) {
  .kg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
