/**
 * 语音能力封装（D8，纯前端 Web Speech API，零后端依赖）。
 *
 * 能力：
 * 1. STT 语音输入：基于 SpeechRecognition / webkitSpeechRecognition（Chrome/Edge 支持），
 *    用于 AI 对话页语音提问；
 * 2. TTS 语音朗读：基于 speechSynthesis，朗读知识点 / AI 回复（优先中文语音）。
 *
 * 设计边界（见 docs/软件产品路线图.md §5.2-1.3）：
 * - 纯浏览器能力，无后端改动；Firefox/Safari 部分能力缺失时按能力检测降级提示；
 * - 识别/朗读失败一律静默降级，不干扰主流程。
 */

interface SpeechRecognitionWindow extends Window {
  SpeechRecognition?: typeof SpeechRecognition
  webkitSpeechRecognition?: typeof SpeechRecognition
}

/** 语音识别是否可用 */
export function isRecognitionSupported() {
  const w = window as unknown as SpeechRecognitionWindow
  return typeof window !== 'undefined' && !!(w.SpeechRecognition || w.webkitSpeechRecognition)
}

/** 语音合成是否可用 */
export function isSpeechSupported() {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

/**
 * 创建并启动一次语音识别会话。
 * @param {Object} opts
 * @param {(text: string, isFinal: boolean) => void} opts.onResult 识别结果回调（isFinal=true 表示一段识别结束）
 * @param {() => void} [opts.onEnd] 识别会话自然结束
 * @param {(err: unknown) => void} [opts.onError] 识别错误
 * @returns {{ stop: () => void }} 返回 stop 用于手动停止；识别器不支持时返回 null
 */
export function startRecognition({ onResult, onEnd, onError }) {
  const w = window as unknown as SpeechRecognitionWindow
  const SR = w.SpeechRecognition || w.webkitSpeechRecognition
  if (!SR) return null

  const recognition = new SR()
  recognition.lang = 'zh-CN'
  recognition.continuous = false // 单段识别：说完自动结束，避免长会话资源泄漏
  recognition.interimResults = true

  recognition.onresult = (event) => {
    let interim = ''
    let final = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const transcript = event.results[i][0].transcript
      if (event.results[i].isFinal) final += transcript
      else interim += transcript
    }
    if (interim) onResult?.(interim, false)
    if (final) onResult?.(final, true)
  }
  recognition.onerror = (e) => {
    // not-allowed / no-speech 属用户主动取消或静默，不视为错误打扰
    if (e?.error === 'not-allowed' || e?.error === 'no-speech') {
      onEnd?.()
      return
    }
    onError?.(e)
    onEnd?.()
  }
  recognition.onend = () => onEnd?.()

  try {
    recognition.start()
  } catch {
    // 连续快速点击可能抛 InvalidStateError
    onEnd?.()
    return null
  }
  return { stop: () => recognition.stop() }
}

/** 中文语音优先的合成器；找不到中文语音则用默认语音。 */
function pickChineseVoice() {
  const voices = window.speechSynthesis.getVoices()
  return (
    voices.find((v) => v.lang === 'zh-CN') ||
    voices.find((v) => v.lang?.toLowerCase().startsWith('zh')) ||
    voices[0] ||
    null
  )
}

/**
 * 朗读文本（TTS）。
 * @param {string} text 要朗读的文本
 * @param {Object} [opts]
 * @param {() => void} [opts.onEnd]
 * @param {() => void} [opts.onError]
 * @returns {boolean} 是否成功启动朗读
 */
export function speak(
  text,
  { onEnd, onError }: { onEnd?: () => void; onError?: (e?: Error) => void } = {},
) {
  if (!isSpeechSupported() || !text) return false
  try {
    const utter = new SpeechSynthesisUtterance(text)
    const voice = pickChineseVoice()
    if (voice) utter.voice = voice
    utter.lang = voice?.lang || 'zh-CN'
    utter.rate = 1.0
    utter.onend = () => onEnd?.()
    utter.onerror = () => {
      onEnd?.()
      onError?.(new Error('speech synthesis error'))
    }
    window.speechSynthesis.cancel() // 避免多段朗读叠加
    window.speechSynthesis.speak(utter)
    return true
  } catch {
    onError?.(new Error('speech synthesis unavailable'))
    return false
  }
}

/** 停止当前朗读。 */
export function stopSpeaking() {
  if (isSpeechSupported()) {
    try {
      window.speechSynthesis.cancel()
    } catch {
      /* 静默 */
    }
  }
}

export default { isRecognitionSupported, isSpeechSupported, startRecognition, speak, stopSpeaking }
