import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  isRecognitionSupported,
  isSpeechSupported,
  startRecognition,
  speak,
  stopSpeaking,
} from '@/composables/useSpeech'

/**
 * AI 学习助手语音域（STT 输入 + TTS 朗读）
 * <p>承载录音开关（识别终句自动发送）与消息朗读；输入框内容与发送动作由聊天域
 * 注入，识别控制器的清理经 stopSpeech 交还编排层的 onBeforeUnmount。</p>
 */
export function useAIAssistantSpeech(deps) {
  const { inputMessage, handleSend } = deps

  const recognitionSupported = isRecognitionSupported()
  const speechSupported = isSpeechSupported()
  const recording = ref(false)
  let recognitionCtrl = null

  function toggleRecording() {
    if (recording.value) {
      recognitionCtrl?.stop()
      recognitionCtrl = null
      recording.value = false
      return
    }
    const ctrl = startRecognition({
      onResult: (text, isFinal) => {
        inputMessage.value = text
        if (isFinal) {
          recording.value = false
          handleSend()
        }
      },
      onEnd: () => {
        recording.value = false
        recognitionCtrl = null
      },
      onError: () => {
        ElMessage.warning('语音识别不可用，请检查浏览器权限或改用文本输入')
      },
    })
    if (!ctrl) {
      ElMessage.warning('当前浏览器不支持语音识别，请使用 Chrome / Edge')
      return
    }
    recognitionCtrl = ctrl
    recording.value = true
    ElMessage.info('正在聆听…请说话（点击按钮停止）')
  }

  function speakMessage(content) {
    if (!content) return
    const text = content.replace(/[#*`>\-|]/g, '').slice(0, 500)
    speak(text, { onError: () => ElMessage.warning('语音朗读不可用') })
  }

  /** 停掉在途识别与朗读（编排层 onBeforeUnmount 调用） */
  function stopSpeech() {
    if (recognitionCtrl) {
      recognitionCtrl.stop()
      recognitionCtrl = null
    }
    stopSpeaking()
  }

  return {
    recognitionSupported,
    speechSupported,
    recording,
    toggleRecording,
    speakMessage,
    stopSpeech,
  }
}
