<template>
  <div>
    <PageHeader
      title="RAG 知识库管理"
      subtitle="知识点文档上传 / 语义召回测试 / 索引实时构建"
      icon="archive"
    >
      <button class="btn btn-primary btn-sm" @click="showUpload = true">
        <svg
          viewBox="0 0 24 24"
          width="14"
          height="14"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M17 8l-5-5-5 5M12 3v12" />
        </svg>
        上传文档
      </button>
    </PageHeader>

    <section class="l1-card animate-in d1">
      <div class="l1-body">
        <CollapsePanel title="知识库文档" icon="archive" :open="true">
          <div class="content-card">
            <div v-if="loading" class="empty-state"><div class="e-title">加载中…</div></div>
            <div v-else-if="docs.length === 0" class="empty-state">
              <div class="e-title">暂无文档</div>
              <div class="e-desc">点击右上角「上传文档」添加知识点内容</div>
            </div>
            <div v-else class="tbl-wrap">
              <table class="tbl">
                <thead>
                  <tr>
                    <th>文档名</th>
                    <th>类型</th>
                    <th>描述</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="d in docs" :key="d.id">
                    <td class="cell-main">{{ d.name }}</td>
                    <td>
                      <span class="tag outline">{{ d.type || '知识点' }}</span>
                    </td>
                    <td class="small muted desc-ellipsis">{{ d.description || '—' }}</td>
                    <td>
                      <div class="row-ops">
                        <span class="op-link" @click="retrieve(d)">测试检索</span>
                        <span class="op-link danger" @click="removeDoc(d)">删除</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </CollapsePanel>

        <CollapsePanel title="智能检索测试" icon="search" :open="false">
          <div class="flex gap-8 mb-8">
            <input
              v-model="query"
              class="input query-input"
              placeholder="输入检索关键词…"
              @keyup.enter="doRetrieve"
            />
            <button class="btn btn-primary" :disabled="retrieving" @click="doRetrieve">
              {{ retrieving ? '检索中…' : '检索' }}
            </button>
          </div>
          <div v-if="results.length" class="l3-block">
            <div class="l3-title">检索结果（{{ results.length }} 条）</div>
            <div v-for="(r, i) in results" :key="i" class="result-item">
              <div class="strong small">{{ r.name || '文档片段 ' + (i + 1) }}</div>
              <div class="small muted result-desc">{{ (r.description || '').slice(0, 200) }}</div>
              <div class="small txt-accent mt-8">相似度：{{ (r.score * 100).toFixed(1) }}%</div>
            </div>
          </div>
          <div v-else class="muted small mt-8">输入关键词点击检索，测试 RAG 知识库召回效果</div>
        </CollapsePanel>
      </div>
    </section>

    <!-- 上传文档抽屉 -->
    <Drawer v-if="showUpload" @close="showUpload = false">
      <template #title>上传知识库文档</template>
      <template #sub>以文本内容入库，索引由系统实时构建</template>
      <div class="form-grid">
        <div class="field span-2">
          <label>文档名称<span class="req">*</span></label
          ><input v-model="docForm.name" class="input" placeholder="如：TCP/IP 协议栈要点" />
        </div>
        <div class="field span-2">
          <label>关联课程<span class="req">*</span></label>
          <select v-model="docForm.courseId" class="select">
            <option v-if="courses.length === 0" value="">暂无课程</option>
            <option v-for="c in courses" :key="c.id" :value="c.id">
              {{ c.name }}（#{{ c.id }}）
            </option>
          </select>
        </div>
        <div class="field span-2">
          <label>文档内容<span class="req">*</span></label>
          <textarea
            v-model="docForm.content"
            class="textarea content-textarea"
            placeholder="粘贴知识点文本内容，将用于语义检索…"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="showUpload = false">取消</button>
        <button class="btn btn-primary" :disabled="uploading" @click="doUpload()">
          {{ uploading ? '上传中…' : '上传文档' }}
        </button>
      </template>
    </Drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import CollapsePanel from '@/components/ui/CollapsePanel.vue'
import Drawer from '@/components/ui/Drawer.vue'
import { toast } from '@/composables/useToast'
import ragApi from '@/api/rag'
import courseApi from '@/api/course'

const loading = ref(true)
const docs = ref([])
const courses = ref([])

const showUpload = ref(false)
const uploading = ref(false)
const docForm = ref({ name: '', courseId: '', content: '' })

const query = ref('')
const retrieving = ref(false)
const results = ref([])

async function loadDocs() {
  loading.value = true
  try {
    const res = await ragApi.ragListDocuments()
    const d = res?.data || res || {}
    docs.value = d.documents || []
  } catch {
    toast.show('文档加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadCourses() {
  try {
    courses.value = await courseApi.getAllCourses()
  } catch {
    courses.value = []
  }
}

async function doUpload() {
  const f = docForm.value
  if (!f.name.trim()) {
    toast.show('请填写文档名称', 'warning')
    return
  }
  if (!f.courseId) {
    toast.show('请选择关联课程', 'warning')
    return
  }
  if (!f.content.trim()) {
    toast.show('请填写文档内容', 'warning')
    return
  }
  uploading.value = true
  try {
    const res = await ragApi.ragAddDocument(f.courseId, f.name.trim(), f.content.trim())
    const d = res?.data || res || {}
    if (d.status === 'ok' || d.id) {
      toast.show('文档已上传并入库', 'ok')
      showUpload.value = false
      docForm.value = { name: '', courseId: '', content: '' }
      loadDocs()
    } else {
      toast.show('上传失败', 'error')
    }
  } catch {
    toast.show('上传失败，请检查课程与内容', 'error')
  } finally {
    uploading.value = false
  }
}

async function removeDoc(d) {
  try {
    const res = await ragApi.ragDeleteDocument(d.id)
    const dd = res?.data || res || {}
    if (dd.status === 'ok' || dd.deleted) {
      docs.value = docs.value.filter((x) => x.id !== d.id)
      toast.show('文档已删除', 'ok')
    } else {
      toast.show('删除失败', 'error')
    }
  } catch {
    toast.show('删除失败', 'error')
  }
}

function retrieve(d) {
  query.value = d.name
  doRetrieve()
}

async function doRetrieve() {
  if (!query.value.trim()) return
  retrieving.value = true
  try {
    const res = await ragApi.ragRetrieve({ query: query.value.trim(), topK: 5 })
    const d = res?.data || res || {}
    results.value = d.results || []
  } catch {
    toast.show('检索失败', 'error')
  } finally {
    retrieving.value = false
  }
}

onMounted(() => {
  loadDocs()
  loadCourses()
})
</script>

<style scoped>
.content-card {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-l2);
  overflow: hidden;
}

.desc-ellipsis {
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.query-input {
  flex: 1;
}

.result-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--line);
}

.result-item:last-child {
  border-bottom: none;
}

.result-desc {
  line-height: 1.7;
  margin-top: 4px;
}

.content-textarea {
  min-height: 180px;
}
</style>
