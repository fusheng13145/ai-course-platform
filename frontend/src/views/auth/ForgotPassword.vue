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
          <div class="grid-overlay"></div>
        </div>

        <div class="brand-content">
          <div class="brand-icon"><el-icon :size="44"><Lock /></el-icon></div>
          <h1 class="brand-title">
            <span v-for="(char, i) in titleChars" :key="i" class="title-char"
                  :style="{ animationDelay: (0.5 + i * 0.05) + 's' }">{{ char }}</span>
          </h1>
          <p class="brand-subtitle">安全重置您的密码</p>

          <div class="steps-display">
            <div v-for="(step, idx) in steps" :key="idx" class="step-node"
                 :class="{ 'step-done': idx < currentStep, 'step-active': idx === currentStep, 'step-upcoming': idx > currentStep }">
              <div class="step-circle"><el-icon v-if="idx < currentStep" :size="14"><Check /></el-icon><span v-else>{{ idx + 1 }}</span></div>
              <span class="step-label">{{ step }}</span>
            </div>
            <div class="step-line" :style="{ width: stepLineWidth }"></div>
          </div>

          <div class="tip-card" :class="{ 'tip-visible': tipVisible }">
            <div class="tip-icon"><el-icon><component :is="currentTipIcon" /></el-icon></div>
            <p>{{ currentTipText }}</p>
          </div>

          <div class="lock-decor">
            <svg viewBox="0 0 120 140" class="lock-svg">
              <rect x="30" y="55" width="60" height="70" rx="8" fill="rgba(255,255,255,0.08)" stroke="rgba(255,255,255,0.15)" stroke-width="2"/>
              <path d="M42,55 V40 a18,18 0 0,1 36,0 V55" fill="none" stroke="rgba(255,255,255,0.2)" stroke-width="3" stroke-linecap="round" :class="{ 'lock-animate': lockAnimating }"/>
              <circle cx="60" cy="85" r="8" fill="rgba(203,181,138,0.3)" :class="{ 'keyhole-glow': lockAnimating }"/><circle cx="60" cy="85" r="3" fill="#CBB58A"/>
            </svg>
          </div>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-section">
        <div class="form-card" :class="{ 'card-enter': cardEntered }">
          <div class="theme-toggle-row"><ThemeSwitcher /></div>
          <div class="form-header"><h2>找回密码</h2><p>通过邮箱验证码安全重置</p></div>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
            <transition-group name="field-reveal" tag="div" class="fields-wrap">
              <el-form-item key="email" prop="email">
                <el-input v-model="form.email" placeholder="注册邮箱"
                          @focus="focusedField = 'email'" @blur="focusedField = null"
                          :class="{ 'input-focused': focusedField === 'email' }" />
              </el-form-item>
              <el-form-item key="code" prop="code">
                <div style="display:flex;gap:8px;width:100%">
                  <el-input v-model="form.code" placeholder="验证码" style="flex:1"
                            @focus="focusedField = 'code'" @blur="focusedField = null"
                            :class="{ 'input-focused': focusedField === 'code' }" />
                  <button type="button" class="code-btn" :class="{ 'countdown-active': countdown>0, 'pulse-ready': countdown===0&&!sending&&form.email }"
                          :disabled="countdown>0||sending" @click="sendCodeWithEffect">
                    {{ countdown>0?countdown+'s':sending?'发送中...':'获取验证码' }}
                    <span v-if="countdown>0" class="countdown-ring"></span>
                  </button>
                </div>
              </el-form-item>
              <el-form-item key="newPwd" prop="newPassword">
                <el-input v-model="form.newPassword" type="password" placeholder="新密码（至少6位）"
                          show-password
                          @focus="focusedField = 'newPwd'" @blur="focusedField = null"
                          :class="{ 'input-focused': focusedField === 'newPwd' }" />
              </el-form-item>
              <el-form-item key="confirmPwd" prop="confirmPassword">
                <el-input v-model="form.confirmPassword" type="password" placeholder="确认新密码"
                          show-password @keyup.enter="handleSubmitWithEffect"
                          @focus="focusedField = 'confirmPwd'" @blur="focusedField = null"
                          :class="{ 'input-focused': focusedField === 'confirmPwd' }" />
              </el-form-item>
            </transition-group>

            <el-form-item>
              <button type="button" class="submit-btn" :class="{ 'btn-loading': loading, 'btn-clicked': btnClicked }"
                      :disabled="loading" @click="handleSubmitWithEffect">
                <span class="btn-text">{{ loading ? '重置中...' : '重置密码' }}</span>
                <span class="btn-ripple" v-if="ripplePos" :style="rippleStyle"></span>
                <span class="btn-loader" v-if="loading"></span>
              </button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span>想起密码了？</span><router-link to="/login" class="switch-link">返回登录</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import authApi from '@/api/auth'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

const router = useRouter()
const formRef = ref(), loading = ref(false), sending = ref(false), countdown = ref(0)
let timer = null

// 入场动画状态
const pageEntered = ref(false), cardEntered = ref(false), tipVisible = ref(false), lockAnimating = ref(true)
const focusedField = ref(null), btnClicked = ref(false)

const titleChars='找回密码'.split('')
const steps=['验证邮箱','输入验证码','设置新密码']

const currentStep=computed(()=>{
  if(!form.email)return 0;if(!form.code)return 1;if(!form.newPassword||!form.confirmPassword)return 2;return 3})
const stepLineWidth=computed(()=>{const p=(currentStep.value/(steps.length-1))*100;return Math.min(p,100)+'%'})

const tips = [
  { icon: 'Message', text: '输入您注册时使用的邮箱地址' },
  { icon: 'Key', text: '查收邮箱中的6位数字验证码' },
  { icon: 'Lock', text: '设置一个安全的新密码' }
]
const currentTipIcon = computed(() => tips[Math.min(currentStep.value, 2)].icon)
const currentTipText = computed(() => tips[Math.min(currentStep.value, 2)].text)

function particleStyle(i){const s=2+Math.random()*6;return{width:s+'px',height:s+'px',left:Math.random()*100+'%',bottom:'-10px',animationDuration:(14+Math.random()*22)+'s',animationDelay:Math.random()*20+'s',opacity:0.12+Math.random()*0.28}}

const ripplePos = ref(null)
const rippleStyle = computed(() => ripplePos.value ? { left: ripplePos.value.x + 'px', top: ripplePos.value.y + 'px' } : {})
const form = reactive({ email:'', code:'', newPassword:'', confirmPassword:'' })
const rules={email:[{required:true,type:'email',message:'请输入有效邮箱',trigger:'blur'}],newPassword:[{required:true,min:6,message:'密码至少6位',trigger:'blur'}],confirmPassword:[{required:true,message:'请确认密码',trigger:'blur'},{validator:(r,v,cb)=>v===form.newPassword?cb():cb(new Error('两次密码不一致')),trigger:'blur'}]}

async function sendCodeWithEffect(e){btnClicked.value=true;setTimeout(()=>btnClicked.value=false,400);await sendCode()}
async function sendCode(){
  if(!form.email){ElMessage.warning('请先输入邮箱');return}
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)){ElMessage.warning('请输入有效的邮箱地址');return}
  sending.value=true
  try{await authApi.sendVerificationCode({email:form.email,type:'FORGOT_PASSWORD'});ElMessage.success('验证码已发送到邮箱');startCountdown();lockAnimating.value=true;setTimeout(()=>lockAnimating.value=false,800)}
  catch(e){ElMessage.error(e.response?.data?.message||'发送验证码失败')}finally{sending.value=false}
}
function startCountdown(){countdown.value=60;timer=setInterval(()=>{countdown.value--;if(countdown.value<=0){clearInterval(timer);timer=null}},1000)}

async function handleSubmitWithEffect(e){const r=e.currentTarget.getBoundingClientRect();ripplePos.value={x:e.offsetX||e.clientX-r.left,y:e.offsetY||e.clientY-r.top};btnClicked.value=true;setTimeout(()=>{btnClicked.value=false;ripplePos.value=null},600);await handleSubmit()}
async function handleSubmit(){
  if(!formRef.value)return;await formRef.value.validate(async(valid)=>{if(!valid)return;if(!form.code){ElMessage.warning('请输入验证码');return}
  loading.value=true
  try{await authApi.resetPasswordByEmail({email:form.email,code:form.code,newPassword:form.newPassword});ElMessage.success({message:'密码重置成功，请登录',duration:1500});setTimeout(()=>router.push('/login'),500)}
  catch(e){ElMessage.error(e.response?.data?.message||'密码重置失败')}finally{loading.value=false}})}

// 页面入场动画
onMounted(()=>{nextTick(()=>{requestAnimationFrame(()=>{pageEntered.value=true;setTimeout(()=>cardEntered.value=true,200);setTimeout(()=>tipVisible.value=true,500)})})})
onUnmounted(()=>{if(timer)clearInterval(timer)})
</script>

<style scoped>
.auth-page{min-height:100vh;display:flex;align-items:center;justify-content:center;background:var(--ac-bg);padding:20px;position:relative;overflow:hidden;cursor:default}
.particles{position:fixed;inset:0;pointer-events:none;z-index:0;overflow:hidden}
.particle{position:absolute;border-radius:50%;background:linear-gradient(135deg,#B89A8A,#CBB58A);animation:particleFloat linear infinite}
@keyframes particleFloat{0%{transform:translateY(0) rotate(0deg);opacity:0}10%{opacity:1}90%{opacity:1}100%{transform:translateY(-110vh) rotate(720deg);opacity:0}}
.auth-container{display:flex;width:100%;max-width:1000px;min-height:600px;background:var(--ac-bg-elevated);border-radius:var(--ac-radius-xl);box-shadow:var(--ac-shadow-xl);overflow:hidden;position:relative;z-index:2;opacity:0;transform:translateY(30px) scale(.97);transition:opacity .7s cubic-bezier(.22,1,.36,1),transform .7s cubic-bezier(.22,1,.36,1)}
.auth-container.enter-active{opacity:1;transform:translateY(0) scale(1)}

.brand-section{flex:1;background:linear-gradient(145deg,#B89A8A 0%,#9B8B82 40%,#7A8BA5 100%);display:flex;align-items:center;justify-content:center;padding:48px 36px;position:relative;overflow:hidden}
.brand-bg-shapes{position:absolute;inset:0;overflow:hidden;pointer-events:none}
.shape{position:absolute;border-radius:50%;border:1px solid rgba(255,255,255,.08);animation:shapeFloat 22s ease-in-out infinite}
.shape-1{width:260px;height:260px;top:-60px;right:-40px}.shape-2{width:160px;height:160px;bottom:-20px;left:-20px;border-color:rgba(255,255,255,.06);animation-delay:-7s}
.grid-overlay{position:absolute;inset:0;background-image:linear-gradient(rgba(255,255,255,.03) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.03) 1px,transparent 1px);background-size:40px 40px;animation:gridShift 20s linear infinite}
@keyframes gridShift{to{background-position:40px 40px}}
@keyframes shapeFloat{0%,100%{transform:translate(0,0) rotate(0deg)}33%{transform:translate(14px,-18px) rotate(120deg)}66%{transform:translate(-10px,12px) rotate(240deg)}}

.brand-content{text-align:center;color:#fff;position:relative;z-index:1}
.brand-icon{width:78px;height:78px;margin:0 auto 24px;background:rgba(255,255,255,.16);border-radius:22px;display:flex;align-items:center;justify-content:center;backdrop-filter:blur(12px);animation:iconPulse 4s ease-in-out infinite}
@keyframes iconPulse{0%,100%{box-shadow:0 0 0 0 rgba(203,181,138,.2)}50%{box-shadow:0 0 0 14px rgba(203,181,138,0)}}
.brand-title{font-size:26px;font-weight:700;margin-bottom:10px;letter-spacing:2px;display:flex;gap:3px;justify-content:center}
.title-char{display:inline-block;opacity:0;transform:translateY(20px) scale(.8);animation:charReveal .5s ease forwards}
@keyframes charReveal{to{opacity:1;transform:translateY(0) scale(1)}}
.brand-subtitle{font-size:15px;opacity:0;animation:fadeInUp .6s ease forwards 1.1s;margin-bottom:32px}
@keyframes fadeInUp{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)}}

.steps-display{display:flex;align-items:center;justify-content:center;gap:0;position:relative;margin-bottom:24px;opacity:0;animation:fadeInUp .6s ease forwards 1.3s}
.step-node{display:flex;flex-direction:column;align-items:center;gap:8px;z-index:2}
.step-circle{width:34px;height:34px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;transition:all .5s cubic-bezier(.22,1,.36,1);border:2px solid rgba(255,255,255,.25);color:rgba(255,255,255,.6)}
.step-node.step-done .step-circle{background:#CBB58A;border-color:#CBB58A;color:#fff;animation:checkPop .4s cubic-bezier(.68,-.55,.27,1.55)}
.step-node.step-active .step-circle{border-color:#CBB58A;color:#CBB58A;box-shadow:0 0 0 4px rgba(203,181,138,.2),0 0 20px rgba(203,181,138,.15);transform:scale(1.1)}
.step-node.step-upcoming .step-circle{opacity:.4}
.step-label{font-size:11px;opacity:.65;white-space:nowrap;transition:opacity .3s}.step-node.step-active .step-label{opacity:1;font-weight:500;color:#CBB58A}
.step-line{position:absolute;top:17px;left:calc(50% - 72px);width:144px;height:2px;background:rgba(255,255,255,.12);border-radius:1px;z-index:1;transition:width .5s cubic-bezier(.22,1,.36,1);overflow:hidden}
@keyframes checkPop{from{transform:scale(0)}to{transform:scale(1)}}

.tip-card{max-width:260px;margin:0 auto 24px;padding:14px 18px;background:rgba(255,255,255,.09);border-radius:12px;backdrop-filter:blur(10px);display:flex;align-items:center;gap:10px;opacity:0;transform:translateY(10px);transition:all .5s cubic-bezier(.22,1,.36,1)}
.tip-card.tip-visible{opacity:1;transform:translateY(0)}
.tip-icon{width:32px;height:32px;border-radius:9px;background:rgba(203,181,138,.2);display:flex;align-items:center;justify-content:center;flex-shrink:0;color:#CBB58A;font-size:16px}
.tip-card p{font-size:12.5px;opacity:.85;line-height:1.4;text-align:left}

.lock-decor{width:80px;margin:0 auto;opacity:0;animation:fadeInUp .6s ease forwards 1.7s}.lock-svg{width:100%;height:auto}
.lock-animate{animation:unlockSwing .8s ease-in-out}@keyframes unlockSwing{0%{transform:rotate(0deg);transform-origin:51px 40px}30%{transform:rotate(-15deg)}60%{transform:rotate(10deg)}100%{transform:rotate(0deg)}}
.keyhole-glow{animation:keyholeGlow .8s ease-in-out}@keyframes keyholeGlow{0%,100%{r:8;opacity:.3}50%{r:14;opacity:.6}}

.form-section{flex:1;display:flex;align-items:center;justify-content:center;padding:36px}
.theme-toggle-row{display:flex;justify-content:flex-end;margin-bottom:8px}
.form-card{width:100%;max-width:370px;opacity:0;transform:translateX(24px);transition:opacity .55s cubic-bezier(.22,1,.36,1),transform .55s cubic-bezier(.22,1,.36,1)}
.form-card.card-enter{opacity:1;transform:translateX(0)}
.form-header{text-align:center;margin-bottom:28px}.form-header h2{font-size:25px;font-weight:700;color:var(--ac-text-primary);margin-bottom:8px}.form-header p{font-size:14px;color:var(--ac-text-secondary)}
.fields-wrap{display:flex;flex-direction:column}
.field-reveal-enter-active{transition:all .4s cubic-bezier(.22,1,.36,1)}.field-reveal-enter-from{opacity:0;transform:translateY(-10px)}
:deep(.el-input.input-focused .el-input__wrapper){box-shadow:0 0 0 1px #B89A8A inset,0 0 0 3px rgba(184,154,138,.12)!important;transition:box-shadow .35s ease}
:deep(.el-input__wrapper){transition:box-shadow .35s ease,background-color .3s;border-radius:11px!important}

.code-btn{position:relative;width:120px;height:39px;border:1.5px solid #B89A8A;border-radius:11px;background:transparent;color:#B89A8A;font-size:13px;font-weight:500;cursor:pointer;transition:all .35s cubic-bezier(.22,1,.36,1);white-space:nowrap;overflow:hidden}
.code-btn:hover:not(:disabled){background:#B89A8A;color:#fff;transform:translateY(-1px);box-shadow:0 4px 14px rgba(184,154,138,.25)}
.code-btn:disabled{border-color:var(--ac-border-color);color:var(--ac-text-placeholder);cursor:default}
.code-btn.countdown-active{border-color:transparent;background:rgba(184,154,138,.08);color:var(--ac-text-secondary);cursor:default}
.code-btn.pulse-ready{animation:pulseReady 2s ease-in-out infinite}
@keyframes pulseReady{0%,100%{box-shadow:0 0 0 0 rgba(184,154,138,.3)}50%{box-shadow:0 0 0 6px rgba(184,154,138,0)}}
.countdown-ring{position:absolute;inset:-2px;border-radius:13px;border:2px solid transparent;border-top-color:#B89A8A;animation:countSpin 1s linear infinite}
@keyframes countSpin{to{transform:rotate(360deg)}}

.submit-btn{position:relative;width:100%;height:42px;border:none;border-radius:11px;background:linear-gradient(135deg,#B89A8A 0%,#A88A7A 100%);color:#fff;font-size:15px;font-weight:600;cursor:pointer;overflow:hidden;transition:all .35s cubic-bezier(.22,1,.36,1);box-shadow:0 4px 15px rgba(184,154,138,.3)}
.submit-btn:hover:not(:disabled){transform:translateY(-2px);box-shadow:0 8px 25px rgba(184,154,138,.4);background:linear-gradient(135deg,#A88A7A 0%,#987A6A 100%)}
.submit-btn:active:not(:disabled){transform:translateY(0)}.submit-btn.btn-clicked:not(:disabled){transform:scale(.97)}.submit-btn.btn-loading{pointer-events:none}
.btn-text{position:relative;z-index:2}
.btn-ripple{position:absolute;border-radius:50%;background:rgba(255,255,255,.35);width:100px;height:100px;transform:translate(-50%,-50%) scale(0);animation:rippleExpand .6s ease-out forwards;pointer-events:none}
@keyframes rippleExpand{to{transform:translate(-50%,-50%) scale(4);opacity:0}}
.btn-loader{position:absolute;top:50%;left:50%;width:18px;height:18px;margin:-9px 0 0 -9px;border:2px solid rgba(255,255,255,.3);border-top-color:#fff;border-radius:50%;animation:spin .7s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}

.form-footer{text-align:center;margin-top:20px;font-size:14px;color:var(--ac-text-secondary)}.switch-link{color:#B89A8A;text-decoration:none;font-weight:600;margin-left:4px;transition:all .3s}.switch-link:hover{color:#987A6A}

[data-theme="dark"] .auth-page{background:var(--ac-bg)}[data-theme="dark"] .brand-section{background:linear-gradient(145deg,#5E4A48 0%,#524A47 40%,#3D4A5C 100%)}
[data-theme="dark"] .grid-overlay{opacity:.4}
@media(max-width:768px){.brand-section{display:none}.auth-container{max-width:420px}}
</style>
