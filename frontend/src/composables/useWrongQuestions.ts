import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import wrongQuestionApi from '@/api/wrongQuestion'

/**
 * 学生错题页数据逻辑（拆分自 student/academic/WrongQuestions.vue，2026-08-29）
 */
export function useWrongQuestions() {
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
      .sort((a: any, b: any) => b.count - a.count)
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
          kpData.forEach((group) => {
            if (group.knowledgePointId && group.questions) {
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
      ElMessage({ message: '正在查找相关题目...', duration: 0 })
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
      ElMessage({ message: `正在基于 ${ids.length} 道错题生成练习卷...`, duration: 0 })
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

  onMounted(loadData)

  return {
    loading,
    stats,
    questions,
    expandedId,
    selectedKpId,
    filteredQuestions,
    knowledgePointTags,
    loadData,
    toggleExpand,
    markMastered,
    unmarkMastered,
    handlePractice,
    handleRelated,
    generatePracticePaper,
  }
}
