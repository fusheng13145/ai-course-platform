import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import questionApi from '@/api/question'
import { confirmDanger } from '@/utils/dangerConfirm'
import { OPTION_LABELS } from './useQuestionsShared'

/** 题目表单初始形状（新建与编辑复用） */
function createQuestionForm() {
  return {
    type: 'SINGLE_CHOICE',
    difficulty: 'MEDIUM',
    courseId: '',
    content: '',
    options: [
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
    ],
    answer: '',
    explanation: '',
    score: 5,
  }
}

/**
 * 题库编辑对话框域
 * <p>承载题目增删改的对话框状态与动作：表单（选择题选项编排/判断题/填空简答编程题
 * 答案处理）、保存（按题型装配 payload）、删除（confirmDanger）；保存/删除成功后
 * 经注入的 fetchQuestions 刷新列表。</p>
 */
export function useQuestionEditDialog(deps) {
  const { fetchQuestions } = deps

  const saving = ref(false)
  const showEditDialog = ref(false)
  const isEditing = ref(false)
  const editingId = ref(null)
  const questionFormRef = ref(null)
  const questionForm = reactive(createQuestionForm())

  const questionRules = {
    type: [{ required: true, message: '请选择题型', trigger: 'change' }],
    difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
    content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
    score: [{ required: true, message: '请设置分值', trigger: 'blur' }],
  }

  function openCreateDialog() {
    isEditing.value = false
    editingId.value = null
    Object.assign(questionForm, createQuestionForm())
    showEditDialog.value = true
  }

  function editQuestion(row) {
    isEditing.value = true
    editingId.value = row.id

    // 填充表单数据
    Object.assign(questionForm, {
      type: row.type || 'SINGLE_CHOICE',
      difficulty: row.difficulty || 'MEDIUM',
      courseId: row.courseId || '',
      content: row.content || '',
      answer: row.answer || '',
      explanation: row.explanation || '',
      score: row.score || 5,
    })

    // 处理选择题选项
    if (row.options && Array.isArray(row.options)) {
      questionForm.options = row.options.map((opt) => ({
        content: opt.content || opt.text || '',
        isCorrect: opt.isCorrect === true || opt.isAnswer === true,
      }))
    } else {
      questionForm.options = [
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
        { content: '', isCorrect: false },
      ]
    }

    showEditDialog.value = true
  }

  async function saveQuestion() {
    if (!questionFormRef.value) return

    await questionFormRef.value.validate(async (valid) => {
      if (!valid) return

      saving.value = true
      try {
        const payload: Record<string, unknown> = {
          type: questionForm.type,
          difficulty: questionForm.difficulty,
          courseId: questionForm.courseId,
          content: questionForm.content,
          score: questionForm.score,
          explanation: questionForm.explanation,
        }

        // 根据题型处理答案
        if (isChoiceType(questionForm.type)) {
          payload.options = questionForm.options.map((opt) => opt.content)
          payload.answer = questionForm.options
            .map((opt, idx) => (opt.isCorrect ? OPTION_LABELS[idx] : null))
            .filter(Boolean)
            .join(',')
        } else if (questionForm.type === 'TRUE_FALSE') {
          payload.answer = String(questionForm.answer)
        } else {
          payload.answer = questionForm.answer
        }

        if (isEditing.value && editingId.value) {
          await questionApi.updateQuestion(editingId.value, payload)
          ElMessage.success('更新成功')
        } else {
          await questionApi.createQuestion(payload)
          ElMessage.success('创建成功')
        }

        showEditDialog.value = false
        fetchQuestions()
      } catch (error) {
        logger.error('保存题目失败:', error)
        ElMessage.error('保存失败')
      } finally {
        saving.value = false
      }
    })
  }

  async function deleteQuestion(row) {
    try {
      // D2-2：题干截断进 subject，并明确不可逆性与恢复路径
      const ok = await confirmDanger({
        verb: '删除',
        target: '题目',
        subject: `${row.content.slice(0, 50)}...`,
      })
      if (!ok) return

      await questionApi.deleteQuestion(row.id)
      ElMessage.success('删除成功')
      fetchQuestions()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error('删除失败')
    }
  }

  /* ---- 选项操作 ---- */

  function addOption() {
    questionForm.options.push({ content: '', isCorrect: false })
  }

  function removeOption(index) {
    questionForm.options.splice(index, 1)
  }

  function isChoiceType(type) {
    return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(type)
  }

  return {
    saving,
    showEditDialog,
    isEditing,
    editingId,
    questionFormRef,
    questionForm,
    questionRules,
    openCreateDialog,
    editQuestion,
    saveQuestion,
    deleteQuestion,
    addOption,
    removeOption,
    isChoiceType,
  }
}
