import { ref } from 'vue'
import type { OverlayConfirmOptions } from '@/bootstrap/overlayHost'

export interface ConfirmState {
  open: boolean
  message: string
  title: string
  confirmText: string
  cancelText: string
  danger: boolean
}

const state = ref<ConfirmState>({
  open: false,
  message: '',
  title: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: false,
})

let resolver: ((value: boolean) => void) | null = null

export function useConfirm() {
  return state
}

/** 共用件的 confirmDanger 接到 自己的弹层，不再依赖 Element Plus MessageBox */
export function confirmWith(
  message: string,
  title: string,
  options: OverlayConfirmOptions = {},
): Promise<boolean> {
  resolver?.(false)
  return new Promise((resolve) => {
    resolver = resolve
    state.value = {
      open: true,
      message,
      title,
      confirmText: options.confirmButtonText || '确定',
      cancelText: options.cancelButtonText || '取消',
      danger: Boolean(options.danger),
    }
  })
}

export function settleConfirm(value: boolean) {
  const done = resolver
  resolver = null
  state.value = { ...state.value, open: false }
  done?.(value)
}
