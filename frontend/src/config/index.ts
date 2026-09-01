const config = {
  appName: 'AI智能课程平台',
  version: '1.0.0',
  apiBaseURL: '/api',
  uploadLimit: 50 * 1024 * 1024,
  defaultAvatar: '/icons.svg',
  pageSize: 10,
  pageSizeOptions: [10, 20, 50, 100],
  dateFormat: 'YYYY-MM-DD',
  datetimeFormat: 'YYYY-MM-DD HH:mm:ss',
  chartColors: [
    'var(--ac-primary)',
    'var(--ac-success)',
    'var(--ac-warning)',
    'var(--ac-danger)',
    'var(--ac-text-muted)',
    'var(--ac-secondary)',
  ],
  learningLevels: [
    { value: 'easy', label: '入门', color: 'var(--ac-success)' },
    { value: 'medium', label: '中等', color: 'var(--ac-warning)' },
    { value: 'hard', label: '困难', color: 'var(--ac-danger)' },
  ],
}

export default config
export const appName = config.appName
export const apiBaseURL = config.apiBaseURL
export const uploadLimit = config.uploadLimit
export const pageSize = config.pageSize
export const pageSizeOptions = config.pageSizeOptions
export const dateFormat = config.dateFormat
export const datetimeFormat = config.datetimeFormat
export const chartColors = config.chartColors
export const learningLevels = config.learningLevels
