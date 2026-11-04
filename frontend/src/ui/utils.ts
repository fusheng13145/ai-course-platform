export function approvalLabel(value: unknown): string {
  const raw = String(value || '')
    .trim()
    .toUpperCase()
  if (raw === 'APPROVED') return '已通过'
  if (raw === 'PENDING') return '待审批'
  if (raw === 'REJECTED') return '已驳回'
  return String(value || '—')
}
