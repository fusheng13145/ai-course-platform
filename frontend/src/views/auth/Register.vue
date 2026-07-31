<template>
  <div class="auth-page">
    <div class="auth-card animate-in">
      <div class="brand-side">
        <div class="brand-glow"></div>
        <div class="brand-inner">
          <div class="brand-mark-lg">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
          </div>
          <h1 class="brand-title">云枢教学平台</h1>
          <p class="brand-sub">创建您的学习账号</p>
          <div class="features">
            <div class="feat-item"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg><span>AI 智能辅导</span></div>
            <div class="feat-item"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg><span>学习数据分析</span></div>
            <div class="feat-item"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg><span>知识图谱构建</span></div>
          </div>
        </div>
      </div>

      <div class="form-side">
        <div class="theme-row"><ThemeSwitcher /></div>
        <div class="form-header"><h2>创建账号</h2><p>注册一个新账号</p></div>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" class="form-wrap">
          <div class="field"><label>用户名</label><el-input v-model="form.username" placeholder="用户名" class="auth-input" /></div>
          <div class="field"><label>邮箱</label><el-input v-model="form.email" placeholder="example@just.edu.cn" class="auth-input" /></div>
          <div class="field"><label>密码</label><el-input v-model="form.password" type="password" placeholder="至少 6 位" show-password class="auth-input" /></div>
          <div class="field"><label>确认密码</label><el-input v-model="form.confirmPwd" type="password" placeholder="再次输入密码" show-password class="auth-input" /></div>

          <el-form-item>
            <el-checkbox v-model="form.agree">我已阅读并同意 <router-link to="/agreement" class="link">用户协议</router-link> 和 <router-link to="/privacy" class="link">隐私政策</router-link></el-checkbox>
          </el-form-item>

          <button class="btn btn-primary btn-lg btn-block" :disabled="auth.isLoading" type="button" @click="handleRegister">
            <span v-if="auth.isLoading" class="btn-loader-dot"></span>{{ auth.isLoading ? '注册中...' : '注 册' }}
          </button>
        </el-form>

        <div class="form-foot"><span>已有账号？</span><router-link to="/login">立即登录</router-link></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)

const form = reactive({ username: '', email: '', password: '', confirmPwd: '', agree: false })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
  confirmPwd: [{ required: true, message: '请确认密码', trigger: 'blur' }],
}

async function handleRegister() {
  if (!form.username || !form.email || !form.password) { ElMessage.warning('请完善注册信息'); return }
  if (form.password !== form.confirmPwd) { ElMessage.warning('两次密码不一致'); return }
  if (!form.agree) { ElMessage.warning('请阅读并同意用户协议和隐私政策'); return }
  try {
    await auth.register({ username: form.username, email: form.email, password: form.password })
    ElMessage.success('注册成功，即将跳转')
    const redirect = route.query.redirect || auth.getDefaultRoute()
    router.push(redirect)
  } catch (e) {
    if (e?.response?.data?.message) ElMessage.error(e.response.data.message)
    else ElMessage.error('注册失败')
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh; display: grid; place-items: center; padding: 24px;
  background:
    radial-gradient(900px 480px at 85% -10%, rgba(193,59,48,.06), transparent 60%),
    radial-gradient(800px 420px at -8% 6%, rgba(37,99,168,.05), transparent 58%),
    var(--bg);
}
.auth-card { display: flex; max-width: 880px; width: 100%; border-radius: 20px; overflow: hidden; box-shadow: var(--sh-modal); border: 1px solid var(--line); background: var(--surface); min-height: 560px; }
.brand-side { width: 380px; flex-shrink: 0; position: relative; overflow: hidden; background: linear-gradient(160deg, var(--accent-strong), var(--accent) 40%, var(--sidebar-bg-end)); color: #fff; display: flex; align-items: center; justify-content: center; }
.brand-glow { position: absolute; top: -60%; right: -40%; width: 300px; height: 300px; border-radius: 50%; background: radial-gradient(circle, rgba(255,255,255,.18), transparent 70%); }
.brand-inner { position: relative; z-index: 1; text-align: center; padding: 40px 32px; }
.brand-mark-lg { width: 80px; height: 80px; border-radius: 20px; margin: 0 auto 20px; background: rgba(255,255,255,.15); backdrop-filter: blur(8px); border: 1px solid rgba(255,255,255,.2); display: grid; place-items: center; box-shadow: 0 8px 32px rgba(0,0,0,.25), inset 0 1px 0 rgba(255,255,255,.2); }
.brand-title { font-size: 22px; font-weight: 700; letter-spacing: 1px; margin-bottom: 6px; }
.brand-sub { font-size: 13px; opacity: .78; margin-bottom: 28px; }
.features { display: flex; flex-direction: column; gap: 12px; align-items: center; }
.feat-item { display: flex; align-items: center; gap: 9px; font-size: 13px; opacity: .88; }
.feat-item svg { color: var(--ok); }
.form-side { flex: 1; display: flex; flex-direction: column; padding: 40px 44px; }
.theme-row { display: flex; justify-content: flex-end; margin-bottom: 8px; }
.form-header { margin-bottom: 20px; }
.form-header h2 { font-size: 22px; font-weight: 700; }
.form-header p { font-size: 13px; color: var(--ink-3); margin-top: 4px; }
.form-wrap { display: flex; flex-direction: column; gap: 2px; flex: 1; }
.form-foot { text-align: center; font-size: 13px; margin-top: 16px; color: var(--ink-3); }
.form-foot a { color: var(--accent); font-weight: 600; margin-left: 4px; }
.link { color: var(--info); }
.auth-input :deep(.el-input__wrapper) { border-radius: 8px !important; border-color: var(--line-2) !important; box-shadow: none !important; padding: 8px 12px !important; }
.auth-input :deep(.el-input__wrapper:hover) { border-color: var(--line-strong) !important; }
.auth-input :deep(.el-input__wrapper.is-focus) { border-color: var(--accent) !important; box-shadow: 0 0 0 3px var(--accent-softer) !important; }
.btn-loader-dot { width: 16px; height: 16px; border: 2px solid rgba(255,255,255,.4); border-top-color: #fff; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 768px) { .auth-card { flex-direction: column; max-width: 420px; } .brand-side { width: 100%; min-height: 160px; } .brand-inner { padding: 24px; } .form-side { padding: 28px 24px; } }
</style>
