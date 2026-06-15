import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'  // 保留基础样式（莫兰迪主题覆盖依赖此文件）
import 'nprogress/nprogress.css'

import ECharts from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart, ScatterChart, HeatmapChart, RadarChart, GraphChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  ToolboxComponent, DataZoomComponent, VisualMapComponent
} from 'echarts/components'

use([
  CanvasRenderer, LineChart, BarChart, PieChart, ScatterChart,
  HeatmapChart, RadarChart, GraphChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  ToolboxComponent, DataZoomComponent, VisualMapComponent
])

import App from './App.vue'
import router from './router'
import './styles/index.scss'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.component('v-chart', ECharts)
app.use(createPinia())
app.use(router)
// Element Plus 组件已通过 unplugin-vue-components 自动按需注册，无需手动 app.use()

/* ============================================
 * Vue 全局错误处理器
 * 捕获所有未处理的组件渲染异常和异步错误
 * ============================================ */
app.config.errorHandler = (err, instance, info) => {
  // 控制台输出完整错误信息（开发调试用）
  console.error('[Global Error]', err)
  console.error('[Error Info]', info)
  console.error('[Component]', instance?.$options?.name || 'Unknown')

  // TODO: 生产环境可接入 Sentry / 等监控系统上报
  // 例如: Sentry.captureException(err, { extra: { info, component: instance?.$options?.name } })
}

app.mount('#app')