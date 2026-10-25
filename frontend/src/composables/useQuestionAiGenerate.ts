import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import questionApi from '@/api/question'

/**
 * AI 智能出题域
 * <p>承载 AI 出题对话框（主题/题型/难度/数量/课程/补充要求）与生成动作；
 * 生成成功后经注入的 fetchQuestions 刷新列表。</p>
 */
export function useQuestionAiGenerate(deps) {
  const { fetchQuestions } = deps

  const aiGenerating = ref(false)
  const showAIDialog = ref(false)
  const aiFormRef = ref(null)
  const aiForm = reactive({
    topic: '',
    type: 'MIXED',
    difficulty: 'MIXED',
    count: 5,
    courseId: '',
    requirements: '',
  })

  const aiRules = {
    topic: [{ required: true, message: '请输入知识点或主题', trigger: 'blur' }],
  }

  async function generateByAI() {
    if (!aiFormRef.value) return

    await aiFormRef.value.validate(async (valid) => {
      if (!valid) return

      aiGenerating.value = true
      try {
        const payload: Record<string, unknown> = {
          topic: aiForm.topic,
          type: aiForm.type,
          difficulty: aiForm.difficulty,
          count: aiForm.count,
          courseId: aiForm.courseId || undefined,
          requirements: aiForm.requirements || undefined,
        }

        const res = await questionApi.generateByAI(payload)
        const generated = res.data?.questions || res.data || []

        if (Array.isArray(generated) && generated.length > 0) {
          ElMessage.success(`成功生成 ${generated.length} 道题目`)
          showAIDialog.value = false
          fetchQuestions()
        } else {
          ElMessage.warning('未生成有效题目')
        }
      } catch (error) {
        logger.error('AI生成题目失败:', error)
        ElMessage.error('AI生成失败，请稍后重试')
      } finally {
        aiGenerating.value = false
      }
    })
  }

  return {
    aiGenerating,
    showAIDialog,
    aiFormRef,
    aiForm,
    aiRules,
    generateByAI,
  }
}
