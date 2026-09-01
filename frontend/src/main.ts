import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css' // 保留基础样式（莫兰迪主题覆盖依赖此文件）
import 'nprogress/nprogress.css'

import ECharts from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import {
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  HeatmapChart,
  RadarChart,
  GraphChart,
  TreeChart,
} from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
  DataZoomComponent,
  VisualMapComponent,
} from 'echarts/components'

use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  ScatterChart,
  HeatmapChart,
  RadarChart,
  GraphChart,
  TreeChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
  DataZoomComponent,
  VisualMapComponent,
])

import App from './App.vue'
import router from './router'
import './styles/index.scss'
import './styles/design-system.css'
import { initMonitoring, captureFrontendError } from './monitoring'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.component('VChart', ECharts)
app.use(createPinia())
app.use(router)
// Element Plus 组件已通过 unplugin-vue-components 自动按需注册，无需手动 app.use()

// 初始化前端监控（幂等；未配置 VITE_MONITOR_ENDPOINT 时静默降级为控制台输出）
initMonitoring()

/* ============================================
 * Vue 全局错误处理器
 * 捕获所有未处理的组件渲染异常和异步错误，
 * 统一经 monitoring 模块上报（见 src/monitoring/index.js）。
 * ============================================ */
app.config.errorHandler = (err, instance, info) => {
  captureFrontendError(err, info, instance?.$options?.name)
}

app.mount('#app')
