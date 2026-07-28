<template>
  <div class="course-chapters fade-in">
    <div v-loading="loading">
      <!-- 头部 + 新增 -->
      <div class="section-bar">
        <div class="chart-title">
          <el-icon><Files /></el-icon> 章节结构
          <el-tag size="small" type="info" effect="light" round>{{ flatCount }} 章</el-tag>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate()">新增顶级章节</el-button>
      </div>

      <!-- 章节树 -->
      <div class="chart-card">
        <div v-if="tree.length" class="tree">
          <div v-for="node in tree" :key="node.id" class="tree-node">
            <!-- 父章节行 -->
            <div class="tree-row">
              <span class="tree-toggle" @click="toggle(node.id)">
                <el-icon :class="{ 'is-open': isOpen(node.id) }"><ArrowRight /></el-icon>
              </span>
              <span class="tree-title">{{ node.title }}</span>
              <span class="tree-meta">
                任务点 {{ node.taskCount || 0 }} · 提交 {{ node.submittedCount || 0 }} · 完成率
                {{ pct(node.completionRate) }}
              </span>
              <el-tag size="small" :type="node.isPublished ? 'success' : 'info'" effect="light">
                {{ node.isPublished ? '已发布' : '草稿' }}
              </el-tag>
              <span class="tree-actions">
                <el-button link type="primary" :icon="Edit" @click="openEdit(node)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="remove(node)">删除</el-button>
              </span>
            </div>

            <!-- 子章节 -->
            <div
              v-if="node.children && node.children.length && isOpen(node.id)"
              class="tree-children"
            >
              <div v-for="c in node.children" :key="c.id" class="tree-node tree-node--child">
                <div class="tree-row">
                  <span class="tree-title tree-title--child">{{ c.title }}</span>
                  <span class="tree-meta">
                    任务点 {{ c.taskCount || 0 }} · 完成率 {{ pct(c.completionRate) }}
                  </span>
                  <el-tag size="small" :type="c.isPublished ? 'success' : 'info'" effect="light">
                    {{ c.isPublished ? '已发布' : '草稿' }}
                  </el-tag>
                  <span class="tree-actions">
                    <el-button link type="primary" :icon="Edit" @click="openEdit(c)"
                      >编辑</el-button
                    >
                    <el-button link type="danger" :icon="Delete" @click="remove(c)">删除</el-button>
                  </span>
                </div>
                <div v-if="c.taskPoints && c.taskPoints.length" class="tree-tasks">
                  <span v-for="tp in c.taskPoints" :key="tp.id" class="task-chip">
                    {{ tp.title }} <em>{{ tp.taskType }}</em>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无章节，点击右上角新增" :image-size="70" />
      </div>
    </div>

    <!-- 新增 / 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增章节' : '编辑章节'"
      width="480px"
      @closed="resetForm"
    >
      <el-form :model="form" label-width="88px">
        <el-form-item label="标题">
          <el-input
            v-model="form.title"
            placeholder="请输入章节标题"
            maxlength="60"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            placeholder="章节简介 / 教学目标（可选）"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.orderIndex" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="form.duration" :min="0" :max="9999" :step="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{
          dialogMode === 'create' ? '创建' : '保存'
        }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import teachingApi from '@/api/teacher'
import chapterApi from '@/api/chapter'
import { Plus, Edit, Delete, ArrowRight, Files } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const saving = ref(false)
const tree = ref([])
const openSet = reactive(new Set())

const dialogVisible = ref(false)
const dialogMode = ref('create')
const editingId = ref(null)
const form = reactive({ title: '', content: '', orderIndex: 0, duration: 0 })

const flatCount = computed(() => {
  let n = 0
  tree.value.forEach((n1) => {
    n++
    ;(n1.children || []).forEach(() => n++)
  })
  return n
})

function pct(v) {
  if (v == null) return '--'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}
function isOpen(id) {
  return openSet.has(id)
}
function toggle(id) {
  openSet.has(id) ? openSet.delete(id) : openSet.add(id)
}

async function load() {
  loading.value = true
  try {
    const r = await teachingApi.getChapterTree(courseId)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    tree.value = list
    list.forEach((n) => openSet.add(n.id))
  } catch (e) {
    logger.error('加载章节树失败', e)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.content = ''
  form.orderIndex = 0
  form.duration = 0
  editingId.value = null
}
function openCreate() {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}
function openEdit(node) {
  dialogMode.value = 'edit'
  editingId.value = node.id
  form.title = node.title || ''
  form.content = node.content || ''
  form.orderIndex = node.orderIndex ?? 0
  form.duration = node.duration ?? 0
  dialogVisible.value = true
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写章节标题')
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await chapterApi.createChapter({
        courseId,
        title: form.title.trim(),
        content: form.content,
        orderIndex: form.orderIndex,
        duration: form.duration,
      })
      ElMessage.success('章节已创建')
    } else {
      await chapterApi.updateChapter(editingId.value, {
        title: form.title.trim(),
        content: form.content,
        orderIndex: form.orderIndex,
        duration: form.duration,
      })
      ElMessage.success('章节已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    logger.error('保存章节失败', e)
  } finally {
    saving.value = false
  }
}

async function remove(node) {
  try {
    await ElMessageBox.confirm(
      `确认删除章节「${node.title}」？其子章节与任务点将一并移除，且不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch {
    return
  }
  try {
    await chapterApi.deleteChapter(node.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    logger.error('删除章节失败', e)
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.section-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.chart-title .el-tag {
  margin-left: 2px;
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}

.tree {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.tree-node {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.tree-node--child {
  background: transparent;
  border-style: dashed;
  margin-left: 22px;
  margin-top: 8px;
}
.tree-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.tree-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  cursor: pointer;
  color: var(--ac-text-muted);
  transition: transform var(--ac-transition-fast);
}
.tree-toggle .el-icon {
  transition: transform var(--ac-transition-fast);
}
.tree-toggle .el-icon.is-open {
  transform: rotate(90deg);
}
.tree-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.tree-title--child {
  font-weight: 500;
  font-size: 13.5px;
}
.tree-meta {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.tree-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.tree-tasks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.task-chip {
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.task-chip em {
  font-style: normal;
  opacity: 0.7;
  margin-left: 4px;
}

.tree-children {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

@media (max-width: 1100px) {
  .tree-children {
    gap: 6px;
  }
}
</style>
