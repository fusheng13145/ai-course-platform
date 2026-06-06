const config = {
  appName: "AI智能课程平台",
  version: "1.0.0",
  apiBaseURL: "/api",
  uploadLimit: 50 * 1024 * 1024,
  defaultAvatar: "/icons.svg",
  pageSize: 10,
  pageSizeOptions: [10, 20, 50, 100],
  dateFormat: "YYYY-MM-DD",
  datetimeFormat: "YYYY-MM-DD HH:mm:ss",
  chartColors: ["#409EFF", "#67C23A", "#E6A23C", "#F56C6C", "#909399", "#B37FEB"],
  learningLevels: [
    { value: "easy", label: "入门", color: "#67C23A" },
    { value: "medium", label: "中等", color: "#E6A23C" },
    { value: "hard", label: "困难", color: "#F56C6C" }
  ]
};

export default config;
export const appName = config.appName;
export const apiBaseURL = config.apiBaseURL;
export const uploadLimit = config.uploadLimit;
export const pageSize = config.pageSize;
export const pageSizeOptions = config.pageSizeOptions;
export const dateFormat = config.dateFormat;
export const datetimeFormat = config.datetimeFormat;
export const chartColors = config.chartColors;
export const learningLevels = config.learningLevels;
