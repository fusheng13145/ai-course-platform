import dayjs from 'dayjs'

/** 格式化时间：YYYY-MM-DD HH:mm */
export function formatTime(value, fallback = '-') {
  if (!value) return fallback
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : fallback
}

/** 格式化日期：YYYY-MM-DD */
export function formatDate(value, fallback = '-') {
  if (!value) return fallback
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD') : fallback
}

/** 格式化日期时间（到秒）：YYYY-MM-DD HH:mm:ss */
export function formatDateTime(value, fallback = '-') {
  if (!value) return fallback
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : fallback
}
