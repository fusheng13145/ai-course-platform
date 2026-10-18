import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import ragApi from '@/api/rag'

/**
 * 「从课程资料一键构建课程知识库」的共享逻辑。
 * <p>管理员知识库管理页与教师课程详情页共用同一入口，因此把请求与状态收敛在此，
 * 避免两处各写一份而产生行为漂移。</p>
 *
 * <p><b>失败可见</b>：错误不进 {@code ElMessage} 一闪而过，而是写入 {@code buildError} 常驻展示；
 * 「引擎未接入」（mode=LOCAL）与「引擎不可用」（mode=UNAVAILABLE / code=503）分别提示，
 * 不把「没真正写入」粉饰成「已入库」。</p>
 *
 * @param courseId 当前课程 ID 的 ref（可空；为空时拒绝发起请求）
 * @returns {{ building: import('vue').Ref<boolean>, buildError: import('vue').Ref<string>,
 *   buildResult: import('vue').Ref<any>, build: () => Promise<boolean> }}
 */
export function useCourseKbBuild(courseId) {
  /** 请求进行中（用于按钮 loading，防重复提交） */
  const building = ref(false)
  /** 常驻错误信息（空串表示无错误），供页面用 el-alert 展示 */
  const buildError = ref('')
  /** 最近一次构建结果（mode/total/added/skipped/documents/note），供页面展示明细 */
  const buildResult = ref(null)

  /** mode=LOCAL：引擎未接入，仅枚举未写入（属已知降级态，非错误） */
  const isLocalOnly = (res) => res?.mode === 'LOCAL'
  /** mode=UNAVAILABLE 或 code=503：引擎不可用（真实失败） */
  const isUnavailable = (res) => res?.mode === 'UNAVAILABLE' || res?.code === 503

  /**
   * 发起构建。
   * @returns {Promise<boolean>} 是否真正写入知识库（ENGINE 成功为 true；LOCAL / 失败为 false）
   */
  async function build() {
    const id = courseId?.value
    if (!id) {
      ElMessage.warning('请先选择课程')
      return false
    }
    building.value = true
    buildError.value = ''
    buildResult.value = null
    try {
      const res = await ragApi.ragBuildCourseKnowledgeBase(id)
      if (!res || typeof res !== 'object') {
        buildError.value = '课程知识库构建失败：后端未返回有效结果'
        return false
      }
      buildResult.value = res
      if (isUnavailable(res)) {
        buildError.value = String(res.error || 'RAG 引擎不可用，课程资料未能写入知识库')
        return false
      }
      if (isLocalOnly(res)) {
        // 引擎未接入：如实告知「只枚举未写入」，不刷新文档列表（列表本就为空）
        ElMessage.warning(res.note || 'RAG 引擎未接入：仅枚举了课程资料，未真正写入知识库')
        return false
      }
      ElMessage.success(res.note || `已写入 ${res.added ?? 0} 篇课程资料并重建索引`)
      return true
    } catch (e) {
      logger.error('课程知识库构建失败:', e)
      buildError.value = '课程知识库构建失败：请检查后端 RAG 服务后重试'
      return false
    } finally {
      building.value = false
    }
  }

  return { building, buildError, buildResult, build }
}
