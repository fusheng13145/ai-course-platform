import { ref, computed } from 'vue'
import { overlayHost } from '@/bootstrap/overlayHost'

/** 倒计时/自动保存定时器域对外注入的动作（由编排层提供，避免域间反向依赖） */
export interface ExamTimerHandlers {
  /** 倒计时归零时的自动交卷动作（submitExamOnTimeout） */
  onTimeout: () => void
  /** 自动保存定时器每 30s tick 的保存动作（autoSaveAnswers） */
  onAutoSave: () => Promise<boolean>
  /** 提示宿主；缺省沿用浮层宿主（旧端为 Element Plus），v2 侧注入自有 toast */
  notify?: { warning: (message: string) => void }
}

/**
 * 学生考试·定时器域（useStudentExams 内部拆分，FE-06）。
 *
 * <p>承载考试倒计时（含 5 分钟整点提醒、归零自动交卷触发）、30s 自动保存
 * 定时器与统一清理。交卷与保存的**业务动作**经 handlers 注入——定时器域
 * 只负责"何时触发"，不持有会话/提交状态。</p>
 *
 * <p>仅由 useStudentExams 编排消费，不单独对外承诺导出面。</p>
 */
export function useStudentExamTimers(handlers: ExamTimerHandlers) {
  const notify = handlers.notify ?? overlayHost().message
  let countdownTimer: ReturnType<typeof setInterval> | null = null
  const autoSaveTimer = ref<ReturnType<typeof setInterval> | null>(null)
  const totalSeconds = ref(0)
  const remainingSeconds = ref(0)

  /** 剩余分钟数 */
  const remainingMinutes = computed(() => Math.floor(remainingSeconds.value / 60))

  /** 倒计时显示文本 */
  const countdownDisplay = computed(() => {
    const m = Math.floor(remainingSeconds.value / 60)
    const s = remainingSeconds.value % 60
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  })

  /* ---- 倒计时 ---- */

  function startCountdown() {
    if (countdownTimer) clearInterval(countdownTimer)
    countdownTimer = setInterval(() => {
      if (remainingSeconds.value > 0) {
        remainingSeconds.value--
        // 归零（剩余 0 秒）不再提示「剩余时间：0 分钟」——该文案具有误导性；
        // 自动交卷提示由下一个 tick 的「考试时间已到」分支统一给出。
        if (
          remainingSeconds.value > 0 &&
          remainingSeconds.value <= 300 &&
          remainingSeconds.value % 60 === 0
        ) {
          notify.warning(`剩余时间：${Math.ceil(remainingSeconds.value / 60)} 分钟`)
        }
      } else {
        // 时间到时停止倒计时并自动交卷。交卷失败（如断网）由 submitExamOnTimeout
        // 兜底重试，直到服务端确认收卷，避免考试卡在「已到时却未交卷」的悬空态。
        clearInterval(countdownTimer)
        countdownTimer = null
        notify.warning('考试时间已到，将自动提交试卷')
        handlers.onTimeout()
      }
    }, 1000)
  }

  /* ---- 自动保存 ---- */

  function startAutoSave() {
    if (autoSaveTimer.value) clearInterval(autoSaveTimer.value)
    autoSaveTimer.value = setInterval(() => {
      handlers.onAutoSave()
    }, 30000) // 每30秒
  }

  function stopAutoSave() {
    if (autoSaveTimer.value) {
      clearInterval(autoSaveTimer.value)
      autoSaveTimer.value = null
    }
  }

  /** 清理倒计时与自动保存定时器（成功交卷 / 手动退出 / 组件卸载） */
  function cleanupTimers() {
    if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
    // 复用 stopAutoSave：与自动保存启动/停止保持单一实现，避免两处清理逻辑漂移。
    stopAutoSave()
  }

  return {
    totalSeconds,
    remainingSeconds,
    remainingMinutes,
    countdownDisplay,
    startCountdown,
    startAutoSave,
    stopAutoSave,
    cleanupTimers,
  }
}
