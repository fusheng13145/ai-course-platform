import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(({ mode }) => ({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })]
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    strictPort: true,
    host: true,
    // 热更新配置
    hmr: {
      overlay: true,  // 显示错误遮罩层
      wait: 1000       // 等待时间(ms)
    },
    proxy: {
      '/api': {
        // 本地后端监听 8080
        target: 'http://127.0.0.1:8080/api',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    },
    // 文件监视配置
    watch: {
      usePolling: true,  // 使用轮询模式（Windows环境推荐）
      interval: 100      // 轮询间隔(ms)
    }
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
          if (id.includes('element-plus')) return 'element-plus'
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('axios')) return 'axios'
          if (id.includes('node_modules')) {
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) return 'vue-vendor'
            // 其余 node_modules 归入 vendor
            return 'vendor'
          }
        }
      }
    }
  }
}))
