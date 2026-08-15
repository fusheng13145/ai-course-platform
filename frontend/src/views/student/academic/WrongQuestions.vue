<template>
  <div class="fade-in">
    <PageHeader
      title="我的错题"
      subtitle="集中收录答错题目，针对性复习巩固"
      icon="warn"
      icon-bg="warn"
    >
      <el-button
        type="primary"
        size="small"
        :disabled="filteredQuestions.length === 0"
        @click="generatePracticePaper"
      >
        <el-icon><EditPen /></el-icon> 生成练习卷
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="错题总数"
        :value="stats.total"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="待复习"
        :value="stats.total - stats.mastered"
        icon="clock"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="已掌握"
        :value="stats.mastered"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--warn-soft); color: var(--warn)">
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
            <path
              d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">错题列表</div>
          <div class="l1-sub">按知识点筛选，点击卡片展开查看解析</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="wq-toolbar">
          <div class="toolbar-left">
            <!-- 知识点分类筛选标签 -->
            <div class="filter-tags">
              <span
                v-for="kp in knowledgePointTags"
                :key="kp.id"
                class="filter-tag"
                :class="{ active: selectedKpId === kp.id }"
                @click="selectedKpId = selectedKpId === kp.id ? null : kp.id"
              >
                {{ kp.name }} ({{ kp.count }})
              </span>
              <span v-if="knowledgePointTags.length === 0" class="filter-tag-hint"
                >暂无分类数据</span
              >
            </div>
          </div>
        </div>

        <!-- 错题列表 -->
        <div v-loading="loading" class="wrong-questions-list">
          <transition-group name="list-fade" tag="div">
            <div
              v-for="q in filteredQuestions"
              :key="q.id"
              class="question-card"
              :class="{ expanded: expandedId === q.id }"
            >
              <!-- 题目头部 -->
              <div class="q-header" @click="toggleExpand(q.id)">
                <div class="q-header-left">
                  <el-tag :type="q.mastered ? 'success' : 'warning'" size="small">
                    {{ q.mastered ? '已掌握' : '待复习' }}
                  </el-tag>
                  <span v-if="q.knowledgePointName" class="q-kp-tag">{{
                    q.knowledgePointName
                  }}</span>
                </div>
                <div class="q-header-right">
                  <el-button v-if="!q.mastered" text size="small" @click.stop="handleRelated(q)">
                    <el-icon><Link /></el-icon> 相关推荐
                  </el-button>
                  <el-button v-if="!q.mastered" text size="small" @click.stop="handlePractice(q)">
                    <el-icon><RefreshRight /></el-icon> 再次练习
                  </el-button>
                  <el-icon class="expand-icon" :class="{ expanded: expandedId === q.id }"
                    ><ArrowDown
                  /></el-icon>
                </div>
              </div>

              <!-- 题目内容（始终显示） -->
              <div class="q-content">
                <p class="q-text">{{ q.questionContent || q.content || '未知题目内容' }}</p>
                <div class="q-answer-row">
                  <span class="answer-label">你的答案：</span>
                  <span class="answer-value wrong">{{
                    q.wrongAnswer || q.studentAnswer || '未记录'
                  }}</span>
                </div>
              </div>

              <!-- 展开区域：正确答案 + 解析 -->
              <transition name="expand-fade">
                <div v-if="expandedId === q.id" class="q-detail">
                  <div class="detail-section">
                    <span class="detail-label correct">正确答案：</span>
                    <span class="detail-value correct">{{
                      q.correctAnswer || q.answer || '--'
                    }}</span>
                  </div>
                  <div v-if="q.analysis || q.explanation" class="detail-section">
                    <span class="detail-label">解析：</span>
                    <p class="analysis-text">{{ q.analysis || q.explanation }}</p>
                  </div>
                  <div class="detail-actions">
                    <el-button
                      v-if="!q.mastered"
                      type="success"
                      size="small"
                      @click="markMastered(q)"
                    >
                      <el-icon><Select /></el-icon> 标记为已掌握
                    </el-button>
                    <el-button v-else type="info" size="small" plain @click="unmarkMastered(q)">
                      取消掌握标记
                    </el-button>
                  </div>
                </div>
              </transition>
            </div>
          </transition-group>

          <el-empty
            v-if="!loading && filteredQuestions.length === 0"
            description="暂无错题记录，继续保持！"
            :image-size="100"
          >
            <template #image><div class="empty-deco">📕</div></template>
          </el-empty>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Select, EditPen, Link, RefreshRight, ArrowDown } from '@element-plus/icons-vue'
import wrongQuestionApi from '@/api/wrongQuestion'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

/* ---- 数据状态 ---- */
const loading = ref(false)
const stats = ref({ total: 0, mastered: 0 })
const questions = ref([])
const expandedId = ref(null)
const selectedKpId = ref(null)

/* ---- 计算属性：过滤后的题目 ---- */
const filteredQuestions = computed(() => {
  let list = questions.value
  if (selectedKpId.value) {
    list = list.filter((q) => q.knowledgePointId === selectedKpId.value)
  }
  return list
})

/* ---- 知识点分类标签 ---- */
const knowledgePointTags = computed(() => {
  const map = {}
  questions.value.forEach((q) => {
    if (q.knowledgePointName) {
      const key = q.knowledgePointName
      if (!map[key]) map[key] = { id: q.knowledgePointId || key, name: key, count: 0 }
      map[key].count++
    }
  })
  return Object.values(map)
    .sort((a, b) => b.count - a.count)
    .slice(0, 8)
})

/* ---- 加载数据 ---- */
async function loadData() {
  loading.value = true
  try {
    const [statsRes, listRes, kpRes] = await Promise.allSettled([
      wrongQuestionApi.getWrongQuestionStats(),
      wrongQuestionApi.getWrongQuestions(),
      wrongQuestionApi.getByKnowledgePoints(),
    ])

    // 统计数据
    if (statsRes.status === 'fulfilled') {
      const statsData = statsRes.value.data || statsRes.value || {}
      stats.value = {
        total: statsData.total || 0,
        mastered: statsData.mastered || 0,
      }
    }

    // 错题列表
    if (listRes.status === 'fulfilled') {
      const list = listRes.value.data || listRes.value || []
      questions.value = Array.isArray(list)
        ? list.map((q) => ({
            ...q,
            mastered: !!q.mastered || q.reviewStatus === 'MASTERED',
            questionContent: q.questionContent || q.content,
            wrongAnswer: q.wrongAnswer || q.studentAnswer,
            correctAnswer: q.correctAnswer || q.answer,
            analysis: q.analysis || q.explanation,
          }))
        : []
    }

    // 知识点分组数据（增强源）
    if (kpRes.status === 'fulfilled') {
      const kpData = kpRes.value?.data?.data || kpRes.value?.data || kpRes.value || []
      if (Array.isArray(kpData) && kpData.length > 0) {
        // 将知识点分组信息合并到现有题目中（补充 knowledgePointName/knowledgePointId）
        const kpMap = {}
        kpData.forEach((group) => {
          if (group.knowledgePointId && group.questions) {
            kpMap[group.knowledgePointId] = group.knowledgePointName || group.name
            group.questions.forEach((q) => {
              const existing = questions.value.find((eq) => eq.id === q.id)
              if (existing && !existing.knowledgePointName) {
                existing.knowledgePointName = group.knowledgePointName || group.name
                existing.knowledgePointId = group.knowledgePointId
              }
            })
          }
        })
      }
    }
  } catch (e) {
    console.error('加载错题失败:', e)
  } finally {
    loading.value = false
  }
}

/* ---- 展开/收起 ---- */
function toggleExpand(id) {
  expandedId.value = expandedId.value === id ? null : id
}

/* ---- 标记掌握/取消 ---- */
async function markMastered(q) {
  try {
    // 修复 P1-20：原误调不存在的 markAsReviewed，导致标记掌握功能报错
    await wrongQuestionApi.markMastered(q.id)
    q.mastered = true
    stats.value.mastered++
    ElMessage.success('已标记为掌握')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function unmarkMastered(q) {
  try {
    // 修复 P1-20：取消掌握应调用 unmarkMastered（原误调 markAsReviewed，且与掌握共用同一方法）
    await wrongQuestionApi.unmarkMastered(q.id)
    q.mastered = false
    stats.value.mastered = Math.max(0, stats.value.mastered - 1)
    ElMessage.info('已取消掌握标记')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

/* ---- 再次练习 ---- */
async function handlePractice(q) {
  try {
    ElMessage.info(`正在生成「${(q.questionContent || '').substring(0, 20)}...」的练习题`)
    const res = await wrongQuestionApi.generatePracticePaper({
      questionIds: [q.id],
      type: 'single',
    })
    const paper = res.data?.data || res.data || res || {}
    if (paper.id || paper.questions) {
      ElMessage.success('练习题已生成，准备跳转...')
    } else {
      ElMessage.warning('未生成有效练习内容')
    }
  } catch (e) {
    console.error('生成练习失败:', e)
    ElMessage.error('生成练习失败，请稍后重试')
  }
}

/* ---- 相关推荐 ---- */
async function handleRelated(q) {
  try {
    if (!q.id) return
    ElMessage.loading({ message: '正在查找相关题目...', duration: 0 })
    const res = await wrongQuestionApi.getRelated(q.id)
    const related = res.data?.data || res.data || res || []
    ElMessage.closeAll()
    if (Array.isArray(related) && related.length > 0) {
      ElMessage.success(`找到 ${related.length} 道相关题目`)
      // 将相关题目追加到列表中（去重）
      const existingIds = new Set(questions.value.map((eq) => eq.id))
      related.forEach((rq) => {
        if (!existingIds.has(rq.id)) {
          questions.value.push({
            ...rq,
            mastered: false,
            questionContent: rq.questionContent || rq.content,
            correctAnswer: rq.correctAnswer || rq.answer,
            analysis: rq.analysis || rq.explanation,
          })
        }
      })
    } else {
      ElMessage.info('暂无相关推荐题目')
    }
  } catch (e) {
    ElMessage.closeAll()
    console.error('获取相关推荐失败:', e)
    ElMessage.error('获取推荐失败，请稍后重试')
  }
}

/* ---- 生成练习卷 ---- */
async function generatePracticePaper() {
  try {
    const ids = filteredQuestions.value.filter((q) => !q.mastered).map((q) => q.id)
    if (ids.length === 0) {
      ElMessage.warning('没有需要复习的错题')
      return
    }
    ElMessage.loading({ message: `正在基于 ${ids.length} 道错题生成练习卷...`, duration: 0 })
    const res = await wrongQuestionApi.generatePracticePaper({
      questionIds: ids,
      type: 'batch',
    })
    ElMessage.closeAll()
    const paper = res.data?.data || res.data || res || {}
    if (paper.id || paper.questions) {
      ElMessage.success(`练习卷已生成，共 ${paper.questions?.length || ids.length} 道题`)
    } else {
      ElMessage.warning('未生成有效练习卷内容')
    }
  } catch (e) {
    ElMessage.closeAll()
    console.error('生成练习卷失败:', e)
    ElMessage.error('生成练习卷失败，请稍后重试')
  }
}

/* ---- 初始化 ---- */
onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* ---- 统计卡片复用全局样式 ---- */

/* ---- 工具栏 ---- */
.wq-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-md);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}

.filter-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  overflow-x: auto;
  max-width: 100%;
}

.filter-tag {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--ac-bg);
  color: var(--ac-text-secondary);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  border: 1px solid transparent;
  white-space: nowrap;
}

.filter-tag:hover {
  background: var(--ac-bg-hover);
}

.filter-tag.active {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  border-color: var(--ac-primary-light);
  font-weight: 500;
}

.filter-tag-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
}

/* ---- 错题卡片 ---- */
.wrong-questions-list {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-sm);
}

.question-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
  overflow: hidden;
}

.question-card:hover {
  box-shadow: var(--ac-shadow-sm);
  border-left: 3px solid var(--ac-danger);
}

.question-card.expanded {
  border-left-color: var(--ac-primary);
}

.q-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  margin-bottom: var(--ac-spacing-sm);
}

.q-header-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
}

.q-kp-tag {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg);
  padding: 2px 10px;
  border-radius: 999px;
}

.q-header-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.expand-icon {
  color: var(--ac-text-muted);
  font-size: 14px;
  transition: transform var(--ac-transition-fast);
  margin-left: 4px;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.q-content {
  margin-bottom: var(--ac-spacing-xs);
}

.q-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--ac-text-primary);
  line-height: 1.7;
  margin: 0 0 var(--ac-spacing-sm);
}

.q-answer-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
}

.answer-label {
  color: var(--ac-text-muted);
  white-space: nowrap;
}

.answer-value {
  font-weight: 500;
}

.answer-value.wrong {
  color: var(--ac-danger);
  text-decoration: line-through;
  opacity: 0.8;
}

.answer-value.correct {
  color: var(--ac-success);
  font-weight: 600;
}

/* ---- 展开详情区 ---- */
.q-detail {
  margin-top: var(--ac-spacing-md);
  padding-top: var(--ac-spacing-md);
  border-top: 1px dashed var(--ac-border);
}

.detail-section {
  margin-bottom: var(--ac-spacing-sm);
}

.detail-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  margin-right: 6px;
}

.detail-label.correct {
  color: var(--ac-success);
}

.detail-value {
  font-size: 14px;
  color: var(--ac-text-primary);
  font-weight: 500;
}

.detail-value.correct {
  color: var(--ac-success);
}

.analysis-text {
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.8;
  margin: 6px 0 0;
  padding: 10px 14px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  gap: var(--ac-spacing-sm);
  margin-top: var(--ac-spacing-sm);
}

/* ---- 动画 ---- */
.list-fade-enter-active,
.list-fade-leave-active {
  transition: all 0.3s ease;
}
.list-fade-enter-from,
.list-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.expand-fade-enter-active {
  transition: all 0.25s ease;
}
.expand-fade-leave-active {
  transition: all 0.15s ease;
}
.expand-fade-enter-from,
.expand-fade-leave-to {
  opacity: 0;
  max-height: 0;
  overflow: hidden;
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 768px) {
  .q-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--ac-spacing-xs);
  }
  .q-header-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
