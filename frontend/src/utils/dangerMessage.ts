/**
 * D2-2 危险操作确认语规范化原语——纯文案部分，不含任何弹层宿主依赖。
 *
 * 统一约束：删除 / 结束考试 / 取消上传等不可逆或高风险操作的二次确认，
 * 必须明确「影响范围 / 不可逆性 / 恢复路径」三要素，避免此前的泛化一句话
 * （如「确定删除该试卷？此操作不可恢复。」）让用户无法评估后果。
 *
 * 拆出本模块的原因：宿主端有 Element Plus 版（utils/dangerConfirm.ts）与
 * 自研弹层版两种，文案模板必须同源，否则两处措辞必然分叉。
 *
 * 文案模板（irreversible !== false 时）：
 *   确定{verb}{target}「{subject}」？{impact}，此操作不可恢复；{recovery}。
 * 文案模板（irreversible === false 时）：
 *   确定{verb}{target}「{subject}」？{impact}，{note}。
 */

export interface DangerConfirmOptions {
  /** 动作，如「删除」「结束」「强制交卷」 */
  verb: string
  /** 对象类型，如「试卷」「班级」「章节」「知识点」 */
  target: string
  /** 对象名称（可选），如试卷标题、班级名 */
  subject?: string | number
  /** 影响范围描述（可选），直接拼接在问句后，说明会一并影响什么。 */
  impact?: string
  /** 是否不可恢复；默认 true。false 时表示操作可逆/可再次切换，仅需说明后果 */
  irreversible?: boolean
  /** 恢复路径提示；irreversible 为 true 时默认 DEFAULT_RECOVERY */
  recovery?: string
  /** 可逆操作的补充提示（irreversible === false 时生效），如「结束后暂不支持重新开启」 */
  note?: string
  /** 确认按钮文字，默认取 verb */
  confirmText?: string
  /** 取消按钮文字，默认「取消」 */
  cancelText?: string
}

export const DEFAULT_RECOVERY = '如需恢复，请联系平台管理员协助处理'

/** 拼接规范化确认文案（纯函数，便于单测）。 */
export function buildDangerMessage(options: DangerConfirmOptions): string {
  const subject =
    options.subject !== undefined && options.subject !== null && options.subject !== ''
      ? `「${options.subject}」`
      : ''
  let msg = `确定${options.verb}${options.target}${subject}？`
  const clauses: string[] = []
  if (options.impact) clauses.push(options.impact)
  if (options.irreversible !== false) {
    clauses.push('此操作不可恢复')
    msg = `${msg}${clauses.join('，')}；${options.recovery ?? DEFAULT_RECOVERY}`
  } else {
    if (options.note) clauses.push(options.note)
    if (clauses.length) msg = `${msg}${clauses.join('，')}`
  }
  return `${msg}。`
}
