import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import Components from 'unplugin-vue-components/vite'

export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    Components({}),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 依赖预构建：显式列出全部重依赖，避免懒加载路由首次打开时
  // 触发 Vite 运行时"发现新依赖 → 重新预构建 → 强制整页刷新"的问题
  // （症状：切换标签页卡顿数秒后页面自动 reload，且路由跳转丢失）
  optimizeDeps: {
    include: [
      'vue',
      'vue-router',
      'pinia',
      'axios',
      'dayjs',
      'nprogress',
      'dompurify',
    ],
  },
  server: {
    port: 5173,
    strictPort: true,
    // 安全默认：仅监听本机回环（localhost），不把 dev server 暴露到整个局域网。
    // 原因：dev server 同时挂着 /api 代理，host:true 会绑定到 0.0.0.0，
    // 在不可信网络（公共 Wi-Fi / 共享办公网）下等同把本机后端接口对外开放。
    // 需要真机或局域网联调时显式开启：VITE_DEV_HOST=true npm run dev
    host: process.env.VITE_DEV_HOST === 'true',
    // 热更新配置
    hmr: {
      overlay: true, // 显示错误遮罩层
    },
    // 预热高频页面模块，降低首次导航延迟
    warmup: {
      clientFiles: ['./src/main.js', './src/layouts/*.vue', './src/views/*/Dashboard.vue'],
    },
    proxy: {
      '/api': {
        // 本地后端监听 8080；冲突时可用 VITE_PROXY_TARGET 指向其他端口（如
        // http://127.0.0.1:8081），缺省行为不变（2026-11-09 视觉 V4 批：8080 被
        // 其他课程作业后端占用期间的并行端口方案）。env 值缺 /api 后缀时自动补齐。
        target: (() => {
          const t = process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080/api'
          return /\/api\/?$/.test(t) ? t.replace(/\/$/, '') : `${t.replace(/\/$/, '')}/api`
        })(),
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        // 必须开启：/api/ws 是 WebSocket 端点（实时通知 + 考试实时通道，握手见后端
        // WebSocketConfig）。缺 ws 时 dev/preview 不转发 upgrade 请求，浏览器握手必然失败，
        // 前端只能退避重连 5 次后彻底放弃 —— 登录后的实时功能在本机从未真正可用。
        ws: true,
      },
    },
    // 注意：不要开启 watch.usePolling —— 100ms 全量轮询数千文件会拖垮 dev server，
    // Windows 下 Vite 原生文件事件工作正常，无需轮询
  },
  // 生产构建剥离 console/debugger：确保线上产物无调试输出（安全/性能/专业度），
  // 与 src/utils/logger.js 的环境门控形成纵深防御。仅在 production 模式生效，dev 不受影响。
  esbuild: {
    drop: mode === 'production' ? ['console', 'debugger'] : [],
  },
  // 优化构建
  build: {
    // 大 chunk 警告阈值保持 1MB（图表/表格类库天然较大）
    chunkSizeWarningLimit: 1024,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            // 仅保留真实依赖的独立分块。历史上引用的 exceljs / pdfjs-dist / jspdf /
            // marked / markdown 均不在 package.json 内，属死配置（分支永不命中），已删除；
            // html2canvas 同类（FE-11：全仓零引用，依赖已卸载），分支随之删除。
            if (id.includes('xlsx')) return 'xlsx'
            if (id.includes('dompurify')) return 'dompurify'
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router'))
              return 'vue-vendor'
            if (id.includes('axios')) return 'axios'
            // 其余 node_modules 归入 vendor，避免单一巨块
            return 'vendor'
          }
        },
      },
    },
  },
}))
