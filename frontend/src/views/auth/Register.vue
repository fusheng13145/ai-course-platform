<template>
  <div class="auth-page">
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
          <div class="ring ring-1"></div>
          <div class="ring ring-2"></div>
        </div>

        <div class="brand-content">
          <div class="brand-icon-wrap">
            <div class="brand-icon-inner">
              <el-icon :size="44"><School /></el-icon>
            </div>
            <div class="icon-ring"></div>
          </div>
          <h1 class="brand-title">
            <span v-for="(char, i) in titleChars" :key="i" class="title-char"
                  :style="{ animationDelay: (0.5 + i * 0.05) + 's' }">{{ char }}</span>
          </h1>
          <p class="brand-subtitle">加入AI智能课程平台</p>

          <div class="progress-indicator">
            <div class="progress-track"><div class="progress-fill" :style="{ width: progressWidth }"></div></div>
            <span class="progress-text">{{ progressText }}</span>
          </div>

          <div class="features">
            <div v-for="(feat, idx) in features" :key="idx" class="feature-item"
                 :class="{ 'feature-visible': featuresVisible, 'feature-highlight': highlightIdx === idx }"
                 :style="{ transitionDelay: (0.8 + idx * 0.15) + 's' }"
                 @mouseenter="highlightIdx = idx" @mouseleave="highlightIdx = -1">
              <div class="feature-num">{{ idx + 1 }}</div>
              <div class="feature-info">
                <span class="feature-label">{{ feat.label }}</span>
                <span class="feature-desc">{{ feat.desc }}</span>
              </div>
              <div class="feature-dot" :class="{ 'dot-pulse': highlightIdx === idx }"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-section">
        <div class="form-card" :class="{ 'card-enter': cardEntered }">
          <div class="theme-toggle-row"><ThemeSwitcher /></div>

          <div class="form-header">
            <h2>创建账号</h2>
            <p>填写以下信息完成注册</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large" @submit.prevent="handleRegister">
            <el-form-item prop="role">
              <div class="role-cards">
                <div v-for="role in roles" :key="role.value" class="role-card" :class="{ 'role-selected': form.role === role.value }"
                     @click="selectRole(role.value)">
                  <div class="role-icon-wrap"><el-icon><component :is="role.icon" /></el-icon></div>
                  <span>{{ role.label }}</span>
                  <div class="role-check" v-if="form.role === role.value"><el-icon><Check /></el-icon></div>
                </div>
              </div>
            </el-form-item>

            <transition-group name="field-slide" tag="div" class="fields-wrap">
              <el-form-item key="username" prop="username">
                <el-input v-model="form.username" placeholder="用户名"
                          @focus="focusedField = 'username'" @blur="focusedField = null"
                          :class="{ 'input-focused': focusedField === 'username' }" />
              </el-form-item>
              <el-form-item key="fullName" prop="fullName">
                <el-input v-model="form.fullName" placeholder="真实姓名"
                          @focus="focusedField = 'fullName'" @blur="focusedField = null"
                          :class="{ 'input-focused': focusedField === 'fullName' }" />
              </el-form-item>
              <el-form-item key="phone" prop="phone">
                <el-input v-model="form.phone" placeholder="手机号" maxlength="11" prefix-icon="Phone"
                          @focus="focusedField = 'phone'" @blur="focusedField = null"
                          :class="{ 'input-focused': focusedField === 'phone' }" />
              </el-form-item>
              <el-form-item key="password" prop="password">
                <el-input v-model="form.password" type="password" placeholder="密码（至少6位）"
                          show-password prefix-icon="Lock"
                          @focus="focusedField = 'password'" @blur="onPasswordBlur"
                          :class="{ 'input-focused': focusedField === 'password' }" />
                <transition name="strength-fade">
                  <div v-if="passwordStrength > 0 && form.password" class="password-strength">
                    <div class="strength-bar">
                      <span v-for="lvl in 4" :key="lvl" class="strength-segment"
                            :class="{ 'seg-filled': lvl <= passwordStrength, 'seg-weak': passwordStrength===1, 'seg-medium': passwordStrength===2, 'seg-strong': passwordStrength>=3 }"></span>
                    </div>
                    <span class="strength-text">{{ strengthLabel }}</span>
                  </div>
                </transition>
              </el-form-item>
              <el-form-item key="confirmPwd" prop="confirmPassword">
                <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码"
                          show-password @keyup.enter="handleRegister"
                          @focus="focusedField = 'confirmPwd'" @blur="focusedField = null"
                          :class="{ 'input-focused': focusedField === 'confirmPwd' }" />
              </el-form-item>
            </transition-group>

            <el-form-item>
              <label class="agreement-label" :class="{ 'checked': form.agreement }" @click="toggleAgreement">
                <span class="custom-checkbox" :class="{ 'checkbox-checked': form.agreement }">
                  <el-icon v-if="form.agreement" :size="12"><Check /></el-icon>
                </span>
                <span>我已阅读并同意</span><a href="javascript:;" class="agree-link">《用户协议》</a>
              </label>
            </el-form-item>

            <el-form-item>
              <button type="button" class="register-btn" :class="{ 'btn-loading': loading, 'btn-clicked': btnClicked }"
                      :disabled="loading || !form.agreement" @click="handleRegWithEffect">
                <span class="btn-text">{{ loading ? '注册中...' : '立即注册' }}</span>
                <span class="btn-ripple" v-if="ripplePos" :style="rippleStyle"></span>
                <span class="btn-loader" v-if="loading"></span>
              </button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>已有账号？</span><router-link to="/login" class="switch-link">立即登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const store = useAuthStore()
const formRef = ref(), loading = ref(false)

// 入场动画状态
const pageEntered = ref(false), cardEntered = ref(false), featuresVisible = ref(false)
const highlightIdx = ref(-1), focusedField = ref(null), btnClicked = ref(false)

const titleChars = 'AI 智能课程平台'.split('')
const roles = [
  { value: 'STUDENT', label: '学生', icon: 'Reading' },
  { value: 'TEACHER', label: '教师', icon: 'Monitor' },
]
const features = [
  { label: '个性化学习', desc: 'AI 定制学习路径' },
  { label: '实时数据分析', desc: '追踪学习进度与效果' },
  { label: '知识图谱', desc: '构建系统化知识体系' },
]

function particleStyle(i) {
  const size = 2 + Math.random() * 6
  return {
    width: size+'px', height: size+'px',
    left: Math.random()*100+'%', bottom: '-10px',
    animationDuration: (14+Math.random()*22)+'s',
    animationDelay: Math.random()*20+'s',
    opacity: 0.12+Math.random()*0.28,
  }
}

const ripplePos = ref(null)
const rippleStyle = computed(() => ripplePos.value ? { left: ripplePos.value.x+'px', top: ripplePos.value.y+'px' } : {})

const progressWidth = computed(() => {
  const fields = [form.username, form.fullName, form.phone, form.password, form.confirmPassword, form.role]
  const filled = fields.filter(f=>f && String(f).trim().length>0).length
  return Math.round((filled/fields.length)*100)+'%'
})
const progressText = computed(() => {
  const w = parseInt(progressWidth.value)
  return w<30?'开始填写':w<60?'继续完善':w<90?'即将完成':'准备就绪'
})

const passwordStrength = computed(() => {
  const pwd=form.password; if(!pwd||pwd.length<6)return 0
  let s=0; if(pwd.length>=6)s++; if(pwd.length>=9)s++; if(/[A-Z]/.test(pwd))s++; if(/[0-9]/.test(pwd)&&/[^A-Za-z0-9]/.test(pwd))s++
  return Math.min(s,4)
})
const strengthLabels=['','弱','一般','强','非常强']
const strengthLabel=computed(()=>strengthLabels[passwordStrength.value])
function onPasswordBlur(){focusedField.value=null}

const form = reactive({ username:'', fullName:'', phone:'', role:'', password:'', confirmPassword:'', agreement:false })
function selectRole(val){form.role=val}
function toggleAgreement(){form.agreement=!form.agreement}
const rules={
  username:[{required:true,message:'请输入用户名',trigger:'blur'},{min:3,max:20,message:'长度在3-20个字符',trigger:'blur'}],
  fullName:[{required:true,message:'请输入真实姓名',trigger:'blur'}],
  phone:[{required:true,message:'请输入手机号',trigger:'blur'},{pattern:/^1[3-9]\d{9}$/,message:'请输入有效手机号',trigger:'blur'}],
  role:[{required:true,message:'请选择角色',trigger:'change'}],
  password:[{required:true,message:'请输入密码',trigger:'blur'},{min:6,message:'密码至少6位',trigger:'blur'}],
  confirmPassword:[{required:true,message:'请确认密码',trigger:'blur'},{validator:(r,v,cb)=>v===form.password?cb():cb(new Error('两次密码不一致')),trigger:'blur'}],
}

async function handleRegWithEffect(e){
  const rect=e.currentTarget.getBoundingClientRect(); ripplePos.value={x:e.offsetX||e.clientX-rect.left,y:e.offsetY||e.clientY-rect.top}; btnClicked.value=true
  setTimeout(()=>{btnClicked.value=false;ripplePos.value=null},600); await handleRegister()
}

async function handleRegister(){
  if(!formRef.value)return
  try{await formRef.value.validate();loading.value=true;await store.register(form);ElMessage.success({message:'注册成功，请登录',duration:1500});setTimeout(()=>router.push('/login'),500)}
  catch(e){ElMessage.error(e.response?.data?.message||e.message||'注册失败')}
  finally{loading.value=false}
}

// 页面入场动画
onMounted(()=>{nextTick(()=>{requestAnimationFrame(()=>{pageEntered.value=true;setTimeout(()=>cardEntered.value=true,200);setTimeout(()=>featuresVisible.value=true,400)})})})
</script>

<style scoped>
.auth-page{min-height:100vh;display:flex;align-items:center;justify-content:center;background:var(--ac-bg);padding:20px;position:relative;overflow:hidden;cursor:default}
.particles{position:fixed;inset:0;pointer-events:none;z-index:0;overflow:hidden}
.particle{position:absolute;border-radius:50%;background:linear-gradient(135deg,#8BA888,#B89A8A);animation:particleFloat linear infinite}
@keyframes particleFloat{0%{transform:translateY(0) rotate(0deg);opacity:0}10%{opacity:1}90%{opacity:1}100%{transform:translateY(-110vh) rotate(720deg);opacity:0}}
.auth-container{display:flex;width:100%;max-width:1000px;min-height:600px;background:var(--ac-bg-elevated);border-radius:var(--ac-radius-xl);box-shadow:var(--ac-shadow-xl);overflow:hidden;position:relative;z-index:2;opacity:0;transform:translateY(30px) scale(.97);transition:opacity .7s cubic-bezier(.22,1,.36,1),transform .7s cubic-bezier(.22,1,.36,1)}
.auth-container.enter-active{opacity:1;transform:translateY(0) scale(1)}

.brand-section{flex:1;background:linear-gradient(135deg,#8BA888 0%,#7A8BA5 40%,#B89A8A 100%);display:flex;align-items:center;justify-content:center;padding:48px 36px;position:relative;overflow:hidden}
.brand-bg-shapes{position:absolute;inset:0;overflow:hidden;pointer-events:none}
.shape{position:absolute;border-radius:50%;border:1px solid rgba(255,255,255,.08);animation:shapeFloat 25s ease-in-out infinite}
.shape-1{width:280px;height:280px;top:-70px;right:-50px}.shape-2{width:180px;height:180px;bottom:-30px;left:-30px;border-color:rgba(255,255,255,.06);animation-delay:-8s}.shape-3{width:100px;height:100px;top:45%;left:8%;border-color:rgba(255,255,255,.1);animation-delay:-14s}
.ring{position:absolute;border-radius:50%;border:1px dashed rgba(255,255,255,.06);animation:ringRotate 30s linear infinite}
.ring-1{width:320px;height:320px;top:50%;left:50%;margin-left:-160px;margin-top:-160px}.ring-2{width:240px;height:240px;top:50%;left:50%;margin-left:-120px;margin-top:-120px;animation-direction:reverse;animation-duration:22s}
@keyframes shapeFloat{0%,100%{transform:translate(0,0) rotate(0deg)}33%{transform:translate(18px,-16px) rotate(120deg)}66%{transform:translate(-12px,14px) rotate(240deg)}}
@keyframes ringRotate{to{transform:rotate(360deg)}}

.brand-content{text-align:center;color:#fff;position:relative;z-index:1}
.brand-icon-wrap{position:relative;width:88px;margin:0 auto 28px;display:inline-block}
.brand-icon-inner{width:80px;height:80px;margin:4px;background:rgba(255,255,255,.18);border-radius:22px;display:flex;align-items:center;justify-content:center;backdrop-filter:blur(12px);position:relative;z-index:1;animation:iconFloat 6s ease-in-out infinite}
@keyframes iconFloat{0%,100%{transform:translateY(0) rotate(0deg)}50%{transform:translateY(-8px) rotate(3deg)}}
.icon-ring{position:absolute;inset:0;border:2px solid rgba(255,255,255,.2);border-radius:26px;animation:ringPulse 3s ease-in-out infinite}
@keyframes ringPulse{0%,100%{transform:scale(1);opacity:.6}50%{transform:scale(1.08);opacity:.3}}
.brand-title{font-size:26px;font-weight:700;margin-bottom:10px;letter-spacing:1px;display:flex;gap:2px;justify-content:center;flex-wrap:wrap}
.title-char{display:inline-block;opacity:0;transform:translateY(20px) scale(.8);animation:charReveal .5s ease forwards}
@keyframes charReveal{to{opacity:1;transform:translateY(0) scale(1)}}
.brand-subtitle{font-size:15px;opacity:0;animation:fadeInUp .6s ease forwards 1.1s;margin-bottom:28px}
@keyframes fadeInUp{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)}}

.progress-indicator{max-width:220px;margin:0 auto 24px;text-align:center;opacity:0;animation:fadeInUp .6s ease forwards 1.3s}
.progress-track{height:3px;background:rgba(255,255,255,.15);border-radius:2px;overflow:hidden;margin-bottom:6px}
.progress-fill{height:100%;background:linear-gradient(90deg,#CBB58A,#fff);border-radius:2px;transition:width .4s cubic-bezier(.22,1,.36,1)}
.progress-text{font-size:11px;opacity:.7;letter-spacing:2px;text-transform:uppercase}

.features{display:flex;flex-direction:column;gap:12px}
.feature-item{display:flex;align-items:center;gap:12px;padding:11px 18px;background:rgba(255,255,255,.08);border-radius:13px;backdrop-filter:blur(10px);cursor:default;opacity:0;transform:translateX(-24px);transition:all .45s cubic-bezier(.22,1,.36,1),background .3s,box-shadow .3s;position:relative;overflow:hidden}
.feature-item.feature-visible{opacity:1;transform:translateX(0)}.feature-item:hover{background:rgba(255,255,255,.15);transform:translateX(6px)}.feature-item.feature-highlight{background:rgba(255,255,255,.17)}
.feature-num{width:24px;height:24px;border-radius:8px;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:700;flex-shrink:0}
.feature-info{display:flex;flex-direction:column;text-align:left;flex:1}.feature-label{font-size:14px;font-weight:500}.feature-desc{font-size:11px;opacity:.65;margin-top:1px}
.feature-dot{width:6px;height:6px;border-radius:50%;background:#CBB58A;transition:all .35s;flex-shrink:0}.feature-dot.dot-pulse{box-shadow:0 0 10px #CBB58A;transform:scale(1.6)}

.form-section{flex:1;display:flex;align-items:center;justify-content:center;padding:36px}
.theme-toggle-row{display:flex;justify-content:flex-end;margin-bottom:8px}
.form-card{width:100%;max-width:380px;opacity:0;transform:translateX(24px);transition:opacity .55s cubic-bezier(.22,1,.36,1),transform .55s cubic-bezier(.22,1,.36,1)}
.form-card.card-enter{opacity:1;transform:translateX(0)}
.form-header{text-align:center;margin-bottom:28px}.form-header h2{font-size:25px;font-weight:700;color:var(--ac-text-primary);margin-bottom:8px}.form-header p{font-size:14px;color:var(--ac-text-secondary)}
.fields-wrap{display:flex;flex-direction:column}
.field-slide-enter-active{transition:all .4s cubic-bezier(.22,1,.36,1)}.field-slide-enter-from{opacity:0;transform:translateY(-10px)}
:deep(.el-input.input-focused .el-input__wrapper){box-shadow:0 0 0 1px #8BA888 inset,0 0 0 3px rgba(139,168,136,.12)!important;transition:box-shadow .35s ease}
:deep(.el-input__wrapper){transition:box-shadow .35s ease,background-color .3s;border-radius:11px!important}

.role-cards{display:flex;gap:12px;width:100%}
.role-card{flex:1;padding:14px 12px;border:2px solid var(--ac-border-color);border-radius:12px;text-align:center;cursor:pointer;transition:all .35s cubic-bezier(.22,1,.36,1);position:relative;background:var(--ac-bg-elevated)}
.role-card:hover{border-color:#8BA888;transform:translateY(-2px)}
.role-card.role-selected{border-color:#8BA888;background:rgba(139,168,136,.08);box-shadow:0 4px 16px rgba(139,168,136,.15)}
.role-icon-wrap{width:38px;height:38px;margin:0 auto 8px;border-radius:10px;background:rgba(139,168,136,.1);display:flex;align-items:center;justify-content:center;color:#8BA888;transition:all .35s}
.role-card.role-selected .role-icon-wrap{background:#8BA888;color:#fff}
.role-card span{font-size:13px;font-weight:500;color:var(--ac-text-primary)}
.role-check{position:absolute;top:6px;right:6px;width:18px;height:18px;background:#8BA888;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:11px;animation:checkPop .3s cubic-bezier(.68,-.55,.27,1.55)}
@keyframes checkPop{from{transform:scale(0)}to{transform:scale(1)}}

.password-strength{padding-top:4px}.strength-bar{display:flex;gap:4px;margin-bottom:3px}
.strength-segment{flex:1;height:3px;border-radius:2px;background:var(--ac-border-color);transition:background .3s ease}
.strength-segment.seg-filled.seg-weak{background:#C48080}.strength-segment.seg-filled.seg-medium{background:#CBB58A}.strength-segment.seg-filled.seg-strong{background:#8BA888}
.strength-text{font-size:11px;color:var(--ac-text-secondary)}
.strength-fade-enter-active{transition:all .3s ease}.strength-fade-enter-from{opacity:0;transform:translateY(-4px)}.strength-fade-leave-active{transition:opacity .2s}.strength-fade-leave-to{opacity:0}

.agreement-label{display:flex;align-items:center;gap:8px;cursor:pointer;font-size:13px;color:var(--ac-text-secondary);user-select:none}
.custom-checkbox{width:18px;height:18px;border:2px solid var(--ac-border-color);border-radius:5px;display:flex;align-items:center;justify-content:center;color:#fff;transition:all .3s cubic-bezier(.68,-.55,.27,1.55);flex-shrink:0}
.custom-checkbox.checkbox-checked{background:#8BA888;border-color:#8BA888}.agreement-label.checked .custom-checkbox{transform:scale(1.1)}
.agree-link{color:#8BA888;text-decoration:none}.agree-link:hover{text-decoration:underline}

.register-btn{position:relative;width:100%;height:42px;border:none;border-radius:11px;background:linear-gradient(135deg,#8BA888 0%,#7A9A77 100%);color:#fff;font-size:15px;font-weight:600;cursor:pointer;overflow:hidden;transition:all .35s cubic-bezier(.22,1,.36,1);box-shadow:0 4px 15px rgba(139,168,136,.3)}
.register-btn:hover:not(:disabled):not(.btn-loading){transform:translateY(-2px);box-shadow:0 8px 25px rgba(139,168,136,.4);background:linear-gradient(135deg,#7A9A77 0%,#6B8968 100%)}
.register-btn:active:not(:disabled){transform:translateY(0)}.register-btn.btn-clicked:not(:disabled){transform:scale(.97)}.register-btn:disabled{opacity:.5;cursor:not-allowed}.register-btn.btn-loading{pointer-events:none}
.btn-text{position:relative;z-index:2}
.btn-ripple{position:absolute;border-radius:50%;background:rgba(255,255,255,.35);width:100px;height:100px;transform:translate(-50%,-50%) scale(0);animation:rippleExpand .6s ease-out forwards;pointer-events:none}
@keyframes rippleExpand{to{transform:translate(-50%,-50%) scale(4);opacity:0}}
.btn-loader{position:absolute;top:50%;left:50%;width:18px;height:18px;margin:-9px 0 0 -9px;border:2px solid rgba(255,255,255,.3);border-top-color:#fff;border-radius:50%;animation:spin .7s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}

.form-footer{text-align:center;margin-top:20px;font-size:14px;color:var(--ac-text-secondary)}.switch-link{color:#8BA888;text-decoration:none;font-weight:600;margin-left:4px;transition:all .3s}.switch-link:hover{color:#6B8968}

[data-theme="dark"] .auth-page{background:var(--ac-bg)}[data-theme="dark"] .brand-section{background:linear-gradient(135deg,#3D523D 0%,#3D4A5C 40%,#5E4A48 100%)}
[data-theme="dark"] .feature-item{background:rgba(255,255,255,.06)}[data-theme="dark"] .feature-item:hover{background:rgba(255,255,255,.11)}
[data-theme="dark"] .register-btn{box-shadow:0 4px 15px rgba(139,168,136,.2)}
@media(max-width:768px){.brand-section{display:none}.auth-container{max-width:420px}}
</style>
