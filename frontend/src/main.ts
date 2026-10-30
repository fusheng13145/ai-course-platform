import '@/bootstrap/apiHostEp'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'nprogress/nprogress.css'

import App from './App.vue'
import { createV2Router } from './v2/router'
import { initMonitoring, captureFrontendError } from './monitoring'

// 首屏前先把 v2 主题落到 <html data-theme>，避免暗色用户先看到一帧浅色
document.documentElement.dataset.theme =
  localStorage.getItem('v2-theme') === 'dark' ? 'dark' : 'light'

const app = createApp(App)
app.use(createPinia())
app.use(createV2Router())
initMonitoring()

app.config.errorHandler = (err, instance, info) => {
  captureFrontendError(err, info, instance?.$options?.name)
}

app.mount('#app')
