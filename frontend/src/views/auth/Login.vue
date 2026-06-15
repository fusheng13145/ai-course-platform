<template>
  <div class="auth-page">
    <!-- 浮动粒子背景 - 不受鼠标影响 -->
    <div class="particles">
      <span v-for="i in 24" :key="i" class="particle" :style="particleStyle(i)"></span>
    </div>

    <div class="auth-container" :class="{ 'enter-active': pageEntered }">
      <!-- 左侧品牌区 -->
      <div class="brand-section">
        <div class="brand-bg-shapes">
          <div class="shape shape-1"></div>
          <div class="shape shape-2"></div>
          <div class="shape shape-3"></div>
          <div class="shape shape-4"></div>
        </div>

        <div class="brand-content">
          <div class="brand-icon float-icon">
            <el-icon :size="48"><School /></el-icon>
          </div>
          <h1 class="brand-title">
            <span v-for="(char, i) in titleChars" :key="i" class="title-char"
                  :style="{ animationDelay: (0.6 + i * 0.05) + 's' }">{{ char }}</span>
          </h1>
          <p class="brand-subtitle">开启您的智能学习之旅</p>

          <div class="features">
            <div v-for="(feat, idx) in features" :key="idx" class="feature-item"
                 :class="{ 'feature-visible': featuresVisible }"
                 :style="{ transitionDelay: (0.8 + idx * 0.15) + 's' }"
                 @mouseenter="hoverFeature = idx" @mouseleave="hoverFeature = -1">
              <div class="feature-icon-wrap" :class="{ 'icon-glow': hoverFeature === idx }">
                <el-icon><component :is="feat.icon" /></el-icon>
              </div>
              <span>{{ feat.label }}</span>
              <div class="feature-line" :class="{ 'line-expand': hoverFeature === idx }"></div>
            </div>
          </div>

          <svg class="wave-svg" viewBox="0 0 280 40" preserveAspectRatio="none">
            <path d="M0,20 Q35,5 70,20 T140,20 T210,20 T280,20 L280,40 L0,40 Z" fill="rgba(255,255,255,0.08)"/>
            <path d="M0,25 Q35,10 70,25 T140,25 T210,25 T280,25 L280,40 L0,40 Z" fill="rgba(255,255,255,0.04)" style="animation: waveShift 8s ease-in-out infinite"/>
          </svg>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-section">
        <div class="form-card" :class="{ 'card-enter': cardEntered }">
          <div class="theme-toggle-row"><ThemeSwitcher /></div>

          <div class="form-header">
            <h2>欢迎回来</h2>
            <p>登录您的账号继续学习</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
            <transition-group name="form-item" tag="div" class="form-items-wrap">
              <el-form-item key="username" prop="username">
                <el-input
                  v-model="form.username" placeholder="用户名"
                  @focus="focusedField = 'username'" @blur="focusedField = null"
                  :class="{ 'input-focused': focusedField === 'username' }"
                >
                  <template #prefix>
                    <div class="input-prefix-icon" :class="{ 'icon-bounce': focusedField === 'username' }">
                      <el-icon><User /></el-icon>
                    </div>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item key="password" prop="password">
                <el-input
                  v-model="form.password" type="password" placeholder="密码"
                  show-password
                  @focus="focusedField = 'password'" @blur="focusedField = null"
                  :class="{ 'input-focused': focusedField === 'password' }"
                  @keyup.enter="handleLogin"
                >
                  <template #prefix>
                    <div class="input-prefix-icon" :class="{ 'icon-bounce': focusedField === 'password' }">
                      <el-icon><Lock /></el-icon>
                    </div>
                  </template>
                </el-input>
              </el-form-item>
            </transition-group>

            <el-form-item>
              <div class="form-options">
                <el-checkbox v-model="form.remember">记住我</el-checkbox>
                <router-link to="/forgot-password" class="forgot-link">忘记密码？</router-link>
              </div>
            </el-form-item>

            <el-form-item>
              <button type="button" class="login-btn" :class="{ 'btn-loading': auth.isLoading, 'btn-clicked': btnClicked }"
                      :disabled="auth.isLoading" @click="handleLoginWithEffect">
                <span class="btn-text">{{ auth.isLoading ? '登录中...' : '登 录' }}</span>
                <span class="btn-ripple" v-if="ripplePos" :style="rippleStyle"></span>
                <span class="btn-loader" v-if="auth.isLoading"></span>
              </button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>还没有账号？</span>
            <router-link to="/register" class="switch-link">立即注册</router-link>
          </div>

          <transition name="demo-slide">
            <div class="demo-accounts" v-if="showError">
              <p>演示账号</p>
              <div class="demo-list">
                <span v-for="(demo, idx) in demoAccounts" :key="idx"
                      @click="fillDemo(demo.user, demo.pass)"
                      class="demo-tag">{{ demo.label }}</span>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)
const showError = ref(false)

// 入场动画状态
const pageEntered = ref(false)
const cardEntered = ref(false)
const featuresVisible = ref(false)
const hoverFeature = ref(-1)
const focusedField = ref(null)
const btnClicked = ref(false)

// ===== 标题打字效果 =====
const titleChars = 'AI 智能课程平台'.split('')

// 特性列表
const features = [
  { icon: 'MagicStick', label: 'AI 智能辅导' },
  { icon: 'TrendCharts', label: '学习数据分析' },
  { icon: 'Connection', label: '知识图谱构建' },
]

// 演示账号
const demoAccounts = [
  { label: '管理员', user: 'admin', pass: 'admin123' },
  { label: '教师', user: 'teacher1', pass: 'teacher123' },
  { label: '学生', user: 'student1', pass: 'student123' },
]

// 粒子样式（固定位置，不受鼠标影响）
function particleStyle(i) {
  const size = 2 + Math.random() * 6
  const left = Math.random() * 100
  const delay = Math.random() * 20
  const duration = 14 + Math.random() * 22
  return {
    width: size + 'px',
    height: size + 'px',
    left: left + '%',
    bottom: '-10px',
    animationDuration: duration + 's',
    animationDelay: delay + 's',
    opacity: 0.12 + Math.random() * 0.28,
  }
}

// 按钮波纹效果
const ripplePos = ref(null)
const rippleStyle = computed(() => ripplePos.value ? {
  left: ripplePos.value.x + 'px',
  top: ripplePos.value.y + 'px',
} : {})

const form = reactive({
  username: '',
  password: '',
  remember: true,
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function fillDemo(username, password) {
  form.username = username
  form.password = password
}

async function handleLoginWithEffect(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  ripplePos.value = { x: e.offsetX || e.clientX - rect.left, y: e.offsetY || e.clientY - rect.top }
  btnClicked.value = true
  setTimeout(() => { btnClicked.value = false; ripplePos.value = null }, 600)
  await handleLogin()
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await auth.login({ username: form.username, password: form.password, remember: form.remember })
      ElMessage.success({ message: '登录成功', duration: 1500 })
      const redirect = route.query.redirect || auth.getDefaultRoute()
      setTimeout(() => router.push(redirect), 300)
    } catch (e) {
      showError.value = true
    }
  })
}

// 页面入场动画
onMounted(() => {
  nextTick(() => {
    requestAnimationFrame(() => {
      pageEntered.value = true
      setTimeout(() => { cardEntered.value = true }, 200)
      setTimeout(() => { featuresVisible.value = true }, 400)
    })
  })
})
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ac-bg);
  padding: 20px;
  position: relative;
  overflow: hidden;
  cursor: default;
}

/* ===== 浮动粒子（独立运动，不受鼠标影响） ===== */
.particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.particle {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(135deg, #7A8BA5, #B89A8A);
  animation: particleFloat linear infinite;
}

@keyframes particleFloat {
  0% { transform: translateY(0) rotate(0deg); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translateY(-110vh) rotate(720deg); opacity: 0; }
}

/* ===== 容器入场动画 ===== */
.auth-container {
  display: flex;
  width: 100%;
  max-width: 1000px;
  min-height: 600px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-xl);
  box-shadow: var(--ac-shadow-xl);
  overflow: hidden;
  position: relative;
  z-index: 2;
  opacity: 0;
  transform: translateY(30px) scale(0.97);
  transition: opacity 0.7s cubic-bezier(0.22, 1, 0.36, 1),
              transform 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}

.auth-container.enter-active {
  opacity: 1;
  transform: translateY(0) scale(1);
}

/* ===== 左侧品牌区（无视差） ===== */
.brand-section {
  flex: 1;
  background: linear-gradient(135deg, #7A8BA5 0%, #9B8AA5 50%, #B89A8A 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  position: relative;
  overflow: hidden;
}

.brand-bg-shapes {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.shape {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.08);
  animation: shapeFloat 20s ease-in-out infinite;
}

.shape-1 { width: 300px; height: 300px; top: -80px; right: -60px; animation-delay: 0s; }
.shape-2 { width: 200px; height: 200px; bottom: -40px; left: -40px; animation-delay: -5s; border-color: rgba(255,255,255,0.06); }
.shape-3 { width: 120px; height: 120px; top: 40%; left: 10%; animation-delay: -10s; border-color: rgba(255,255,255,0.1); }
.shape-4 { width: 180px; height: 180px; top: 20%; right: 20%; animation-delay: -15s; border-style: dashed; }

@keyframes shapeFloat {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  33% { transform: translate(15px, -20px) rotate(120deg); }
  66% { transform: translate(-10px, 15px) rotate(240deg); }
}

.brand-content {
  text-align: center;
  color: #fff;
  position: relative;
  z-index: 1;
}

.brand-icon {
  width: 88px;
  height: 88px;
  margin: 0 auto 28px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(12px);
  box-shadow: 0 8px 32px rgba(0,0,0,0.1), inset 0 1px 0 rgba(255,255,255,0.2);
  animation: iconFloat 6s ease-in-out infinite;
}

@keyframes iconFloat {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-8px) rotate(2deg); }
}

.brand-title {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: 1px;
  display: flex;
  gap: 2px;
  justify-content: center;
  flex-wrap: wrap;
}

.title-char {
  display: inline-block;
  opacity: 0;
  transform: translateY(20px) scale(0.8);
  animation: charReveal 0.5s ease forwards;
}

@keyframes charReveal {
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.brand-subtitle {
  font-size: 15px;
  opacity: 0;
  animation: fadeInUp 0.6s ease forwards 1.1s;
  margin-bottom: 44px;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

.features {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px 20px;
  background: rgba(255, 255, 255, 0.09);
  border-radius: 14px;
  backdrop-filter: blur(10px);
  font-size: 14px;
  cursor: default;
  opacity: 0;
  transform: translateX(-24px);
  transition: all 0.45s cubic-bezier(0.22, 1, 0.36, 1),
              background 0.3s, box-shadow 0.3s;
  position: relative;
  overflow: hidden;
}

.feature-item.feature-visible {
  opacity: 1;
  transform: translateX(0);
}

.feature-item:hover {
  background: rgba(255, 255, 255, 0.16);
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
  transform: translateX(6px);
}

.feature-icon-wrap {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: rgba(255,255,255,0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.35s ease;
  flex-shrink: 0;
}

.feature-icon-wrap .el-icon {
  font-size: 17px;
  color: #fff;
}

.feature-icon-wrap.icon-glow {
  background: rgba(255,255,255,0.3);
  box-shadow: 0 0 18px rgba(255,255,255,0.25);
  transform: scale(1.1) rotate(-5deg);
}

.feature-line {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(to bottom, #CBB58A, #B89A8A);
  border-radius: 0 2px 2px 0;
  transform: scaleY(0);
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}

.feature-line.line-expand {
  transform: scaleY(1);
}

.wave-svg {
  margin-top: 32px;
  width: 220px;
  height: 36px;
  opacity: 0;
  animation: fadeInUp 0.6s ease forwards 1.6s;
}

@keyframes waveShift {
  0%, 100% { d: path("M0,20 Q35,5 70,20 T140,20 T210,20 T280,20 L280,40 L0,40 Z"); }
  50% { d: path("M0,18 Q35,28 70,18 T140,18 T210,18 T280,18 L280,40 L0,40 Z"); }
}

/* ===== 右侧表单区 ===== */
.form-section {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
}

.theme-toggle-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.form-card {
  width: 100%;
  max-width: 370px;
  opacity: 0;
  transform: translateX(24px);
  transition: opacity 0.55s cubic-bezier(0.22, 1, 0.36, 1),
              transform 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}

.form-card.card-enter {
  opacity: 1;
  transform: translateX(0);
}

.form-header {
  text-align: center;
  margin-bottom: 32px;
}

.form-header h2 {
  font-size: 25px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}

.form-header p {
  font-size: 14px;
  color: var(--ac-text-secondary);
}

.form-items-wrap {
  display: flex;
  flex-direction: column;
}

.form-item-enter-active {
  transition: all 0.4s cubic-bezier(0.22, 1, 0.36, 1);
}
.form-item-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}

.input-prefix-icon {
  display: flex;
  align-items: center;
  transition: transform 0.3s cubic-bezier(0.68, -0.55, 0.27, 1.55);
}

.input-prefix-icon.icon-bounce {
  transform: scale(1.2) rotate(-8deg);
  color: #7A8BA5 !important;
}

:deep(.el-input.input-focused .el-input__wrapper) {
  box-shadow: 0 0 0 1px #7A8BA5 inset, 0 0 0 3px rgba(122,139,165,0.12) !important;
  transition: box-shadow 0.35s ease;
}

:deep(.el-input__wrapper) {
  transition: box-shadow 0.35s ease, background-color 0.3s;
  border-radius: 11px !important;
}

.login-btn {
  position: relative;
  width: 100%;
  height: 42px;
  border: none;
  border-radius: 11px;
  background: linear-gradient(135deg, #7A8BA5 0%, #8B95AD 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);
  box-shadow: 0 4px 15px rgba(122,139,165,0.3);
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(122,139,165,0.4);
  background: linear-gradient(135deg, #6E7F99 0%, #7F899D 100%);
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 10px rgba(122,139,165,0.3);
}

.login-btn.btn-clicked:not(:disabled) {
  transform: scale(0.97);
}

.login-btn.btn-loading {
  pointer-events: none;
}

.btn-text {
  position: relative;
  z-index: 2;
}

.btn-ripple {
  position: absolute;
  border-radius: 50%;
  background: rgba(255,255,255,0.35);
  width: 100px;
  height: 100px;
  transform: translate(-50%, -50%) scale(0);
  animation: rippleExpand 0.6s ease-out forwards;
  pointer-events: none;
}

@keyframes rippleExpand {
  to { transform: translate(-50%, -50%) scale(4); opacity: 0; }
}

.btn-loader {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 18px;
  height: 18px;
  margin: -9px 0 0 -9px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.forgot-link {
  font-size: 13px;
  color: #7A8BA5;
  text-decoration: none;
  transition: all 0.3s;
  position: relative;
}

.forgot-link::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 0;
  height: 1.5px;
  background: #7A8BA5;
  transition: width 0.3s ease;
}

.forgot-link:hover::after {
  width: 100%;
}

.forgot-link:hover {
  color: #5D6B82;
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--ac-text-secondary);
}

.switch-link {
  color: #7A8BA5;
  text-decoration: none;
  font-weight: 600;
  margin-left: 4px;
  transition: all 0.3s;
  position: relative;
}

.switch-link:hover {
  color: #5D6B82;
}

.demo-accounts {
  margin-top: 24px;
  padding: 18px;
  background: rgba(122,139,165,0.07);
  border-radius: 14px;
  text-align: center;
  border: 1px solid rgba(122,139,165,0.1);
}

.demo-accounts p {
  font-size: 12px;
  color: var(--ac-text-secondary);
  margin-bottom: 10px;
  letter-spacing: 1px;
}

.demo-list {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.demo-tag {
  font-size: 13px;
  color: #7A8BA5;
  cursor: pointer;
  padding: 6px 16px;
  background: var(--ac-bg-elevated);
  border-radius: 20px;
  transition: all 0.35s cubic-bezier(0.22, 1, 0.36, 1);
  border: 1px solid transparent;
  opacity: 0;
  animation: demoTagIn 0.4s ease forwards;
}

@keyframes demoTagIn {
  from { opacity: 0; transform: scale(0.85); }
  to { opacity: 1; transform: scale(1); }
}

.demo-tag:hover {
  background: #7A8BA5;
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(122,139,165,0.3);
}

.demo-slide-enter-active { transition: all 0.45s cubic-bezier(0.22, 1, 0.36, 1); }
.demo-slide-enter-from { opacity: 0; transform: translateY(-12px); }
.demo-slide-leave-active { transition: all 0.3s ease; }
.demo-slide-leave-to { opacity: 0; transform: translateY(8px); }

/* 暗色模式 */
[data-theme="dark"] .auth-page { background: var(--ac-bg); }
[data-theme="dark"] .brand-section { background: linear-gradient(135deg, #3D4A5C 0%, #4E4658 50%, #5E4A48 100%); }
[data-theme="dark"] .feature-item { background: rgba(255, 255, 255, 0.06); }
[data-theme="dark"] .feature-item:hover { background: rgba(255, 255, 255, 0.12); }
[data-theme="dark"] .demo-accounts { background: rgba(122, 139, 165, 0.08); border-color: rgba(122, 139, 165, 0.12); }
[data-theme="dark"] .login-btn { box-shadow: 0 4px 15px rgba(122,139,165,0.2); }

@media (max-width: 768px) {
  .brand-section { display: none; }
  .auth-container { max-width: 420px; }
  .cursor-glow { display: none; }
}
</style>
