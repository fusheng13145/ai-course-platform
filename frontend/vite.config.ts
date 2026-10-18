import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { readdirSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

/**
 * 枚举 element-plus 全部按需样式入口，加入 optimizeDeps.include。
 * 背景：unplugin-vue-components 在「转换时」注入 `element-plus/es/components/x/style/css`
 * 导入，Vite 启动扫描无法预知全部组件 → 运行时发现新依赖会触发重新预构建 + 整页刷新
 * （症状：切换页面卡顿数秒后自动 reload 且路由跳转丢失）。
 * Vite 8(Rolldown) 下 include 的 glob 写法未展开，这里改用文件系统确定性枚举。
 */
function elementPlusStyleDeps() {
  const root = dirname(fileURLToPath(new URL(import.meta.url)))
  const compDir = join(root, 'node_modules', 'element-plus', 'es', 'components')
  if (!existsSync(compDir)) return []
  return readdirSync(compDir)
    .filter((name) => existsSync(join(compDir, name, 'style', 'css.mjs')))
    .map((name) => `element-plus/es/components/${name}/style/css`)
}

export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
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
      'echarts',
      'vue-echarts',
      '@element-plus/icons-vue',
      'element-plus/es',
      // unplugin 按需导入的组件样式是独立依赖入口，必须全量预构建（确定性枚举，非 glob）
      ...elementPlusStyleDeps(),
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
        // 本地后端监听 8080
        target: 'http://127.0.0.1:8080/api',
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
    // 大 chunk 警告阈值调至 1MB（ECharts 等可视化库天然较大）
    chunkSizeWarningLimit: 1024,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus')) return 'element-plus'
            if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
            // 仅保留真实依赖的独立分块。历史上引用的 exceljs / pdfjs-dist / jspdf /
            // marked / markdown 均不在 package.json 内，属死配置（分支永不命中），已删除。
            if (id.includes('xlsx')) return 'xlsx'
            if (id.includes('html2canvas')) return 'html2canvas'
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
