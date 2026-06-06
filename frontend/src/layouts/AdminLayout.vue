<template>
  <div class="layout-container">
    <div class="sidebar">
      <div class="logo"><el-icon :size="20"><School /></el-icon><span>管理后台</span></div>
      <el-menu :default-active="activeMenu" router background-color="transparent" text-color="rgba(255,255,255,0.65)" active-text-color="#818CF8">
        <el-menu-item index="/admin/dashboard"><el-icon><Monitor /></el-icon><span>仪表盘</span></el-menu-item>
        <el-menu-item index="/admin/users"><el-icon><User /></el-icon><span>用户管理</span></el-menu-item>
        <el-menu-item index="/admin/courses"><el-icon><Reading /></el-icon><span>课程管理</span></el-menu-item>
        <el-menu-item index="/admin/analytics"><el-icon><TrendCharts /></el-icon><span>数据分析</span></el-menu-item>
        <el-menu-item index="/admin/exams"><el-icon><Document /></el-icon><span>考试管理</span></el-menu-item>
        <el-menu-item index="/admin/grades"><el-icon><TrophyBase /></el-icon><span>成绩管理</span></el-menu-item>
        <el-menu-item index="/admin/notifications"><el-icon><Bell /></el-icon><span>通知管理</span></el-menu-item>
        <el-menu-item index="/admin/questions"><el-icon><QuestionFilled /></el-icon><span>题库管理</span></el-menu-item>
        <el-menu-item index="/admin/permissions"><el-icon><Key /></el-icon><span>权限管理</span></el-menu-item>
        <el-menu-item index="/admin/system"><el-icon><Setting /></el-icon><span>系统设置</span></el-menu-item>
      </el-menu>
    </div>
    <div class="main-area">
      <div class="header">
        <div><span class="page-title">{{ route.meta?.title || "管理后台" }}</span></div>
        <div style="display:flex;align-items:center;gap:12px;">
          <ThemeSwitcher />
          <span style="font-size:14px">{{ auth.userName }}</span>
          <el-tag type="danger" size="small">管理员</el-tag>
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