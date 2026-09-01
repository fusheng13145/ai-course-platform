<template>
  <div class="profile-page fade-up">
    <!-- 顶部封面 + 头像 + 关键数据条 -->
    <ProfileHero
      :user="user"
      :role-key="roleKey"
      :role-label="roleLabel"
      :avatar-text="avatarText"
      :member-since="memberSince"
      :last-login="lastLogin"
      :stats="stats"
    />

    <!-- 内容网格：左主列（课程/动态）+ 右侧列（资料/安全/偏好） -->
    <div class="profile-grid">
      <div class="profile-col profile-col--main">
        <ProfileCourses
          :courses="courses"
          :loading="loading"
          :course-initial="courseInitial"
          :progress-color="progressColor"
          @view-all="goCourses"
          @open="goCourse"
        />
        <ProfileActivity
          :activities="activities"
          :loading="loading"
          @view-todos="goTodos"
          @open="goCourse"
        />
      </div>

      <div class="profile-col profile-col--side">
        <ProfileBasicForm :form="form" :saving="saving" @save="saveProfile" />
        <ProfileSecurityForm
          :password-form="passwordForm"
          :changing="changingPassword"
          @change="changePassword"
        />
        <ProfilePreferences
          v-model:notify-on="notifyOn"
          v-model:email-on="emailOn"
          :theme="theme"
          @set-theme="setTheme"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { useProfile } from '@/composables/useProfile'
import ProfileHero from './components/ProfileHero.vue'
import ProfileCourses from './components/ProfileCourses.vue'
import ProfileActivity from './components/ProfileActivity.vue'
import ProfileBasicForm from './components/ProfileBasicForm.vue'
import ProfileSecurityForm from './components/ProfileSecurityForm.vue'
import ProfilePreferences from './components/ProfilePreferences.vue'

/**
 * 个人中心页面
 * <p>页面壳：编排顶部封面（ProfileHero）、左主列（我的课程 / 学习动态）
 * 与右侧列（基本资料 / 账号安全 / 偏好设置）；数据与表单逻辑由 useProfile 组合式函数承载，
 * 各区块的渲染与交互下沉至对应子组件，本组件仅负责数据装配与路由跳转。</p>
 */
const router = useRouter()
const auth = useAuthStore()
const { theme, setTheme } = useTheme()

const {
  user,
  form,
  passwordForm,
  saving,
  changingPassword,
  loading,
  courses,
  activities,
  stats,
  notifyOn,
  emailOn,
  roleKey,
  roleLabel,
  avatarText,
  memberSince,
  lastLogin,
  courseInitial,
  progressColor,
  loadDashboard,
  saveProfile,
  changePassword,
} = useProfile(() => auth.user)

/* ---- 跳转 ---- */
function goCourses() {
  router.push('/student/courses')
}
function goCourse(c) {
  if (c?.courseId) router.push(`/student/courses/${c.courseId}`)
}
function goTodos() {
  router.push('/student/notifications')
}

onMounted(loadDashboard)
</script>

<style scoped>
.profile-page {
  max-width: 1180px;
  margin: 0 auto;
  padding-bottom: var(--ac-spacing-xl);
}
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: var(--ac-spacing-lg);
  align-items: start;
}
.profile-col {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-lg);
  min-width: 0;
}

/* 入场动画 */
.fade-up {
  animation: fadeUp 0.45s cubic-bezier(0.4, 0, 0.2, 1) both;
}
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 980px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
