import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'nprogress/nprogress.css'

import ECharts from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart, ScatterChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  ToolboxComponent, DataZoomComponent, VisualMapComponent
} from 'echarts/components'

use([
  CanvasRenderer, LineChart, BarChart, PieChart, ScatterChart,
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
app.use(ElementPlus)
app.mount('#app')