<template>
  <div class="layout-container">
    <div class="sidebar">
      <div class="logo"><el-icon :size="20"><School /></el-icon><span>学习平台</span></div>
      <el-menu :default-active="activeMenu" router background-color="transparent" text-color="rgba(255,255,255,0.65)" active-text-color="#818CF8">
        <el-menu-item index="/student/dashboard"><el-icon><Monitor /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="/student/courses"><el-icon><Reading /></el-icon><span>我的课程</span></el-menu-item>
        <el-menu-item index="/student/knowledge-graph"><el-icon><Connection /></el-icon><span>知识图谱</span></el-menu-item>
        <el-menu-item index="/student/learning-path"><el-icon><TrendCharts /></el-icon><span>学习路径</span></el-menu-item>
        <el-menu-item index="/student/ai-assistant"><el-icon><MagicStick /></el-icon><span>AI助手</span></el-menu-item>
        <el-menu-item index="/student/exams"><el-icon><Document /></el-icon><span>模拟考试</span></el-menu-item>
        <el-menu-item index="/student/grades"><el-icon><TrophyBase /></el-icon><span>我的成绩</span></el-menu-item>
        <el-menu-item index="/student/wrong-questions"><el-icon><WarningFilled /></el-icon><span>错题本</span></el-menu-item>
        <el-menu-item index="/student/learning-calendar"><el-icon><Calendar /></el-icon><span>学习日历</span></el-menu-item>
        <el-menu-item index="/student/notifications"><el-icon><Bell /></el-icon><span>通知中心</span></el-menu-item>
        <el-menu-item index="/student/profile"><el-icon><User /></el-icon><span>个人中心</span></el-menu-item>
      </el-menu>
    </div>
    <div class="main-area">
      <div class="header">
        <div><span class="page-title">{{ route.meta?.title || "学习平台" }}</span></div>
        <div style="display:flex;align-items:center;gap:12px;">
          <ThemeSwitcher />
          <span style="font-size:14px">{{ auth.userName }}</span>
          <el-tag type="warning" size="small">学生</el-tag>
          <el-button text type="primary" @click="handleLogout">退出</el-button>
        </div>
      </div>
      <div class="content"><router-view /></div>
    </div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
const route = useRoute(); const router = useRouter(); const auth = useAuthStore()
const activeMenu = computed(() => route.path)
function handleLogout() { auth.logout().then(() => router.push('/login')) }
</script>