<template>
  <section class="profile-hero">
    <div class="hero-cover">
      <span class="hero-blob hero-blob--1" />
      <span class="hero-blob hero-blob--2" />
    </div>

    <div class="hero-lower">
      <div class="hero-avatar">
        <el-avatar :size="92" class="hero-avatar__img">
          {{ avatarText }}
        </el-avatar>
        <span class="hero-avatar__status" title="在线" />
      </div>

      <div class="hero-text">
        <div class="hero-name">
          {{ user.fullName || user.username }}
          <el-tag size="small" :class="['role-tag', 'role-tag--' + roleKey]">{{
            roleLabel
          }}</el-tag>
        </div>
        <div class="hero-handle">@{{ user.username }}</div>
        <div class="hero-meta">
          <span><el-icon><Calendar /></el-icon> 加入于 {{ memberSince }}</span>
          <span><el-icon><Clock /></el-icon> 最近登录 {{ lastLogin }}</span>
        </div>
      </div>

      <!-- 关键数据条（在学课程 / 平均分 / 连续学习 / 待办） -->
      <div class="hero-stats">
        <div class="hero-stat">
          <div class="hero-stat__value">{{ stats.courseCount }}</div>
          <div class="hero-stat__label">在学课程</div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat__value">{{ stats.avgScore }}</div>
          <div class="hero-stat__label">平均分</div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat__value">{{ stats.streakDays }}<i>天</i></div>
          <div class="hero-stat__label">连续学习</div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat__value">{{ stats.todoCount }}</div>
          <div class="hero-stat__label">待办事项</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { Calendar, Clock } from '@element-plus/icons-vue'

/**
 * 个人中心顶部封面
 * <p>展示头像、姓名/角色标签、加入时间与最近登录（meta），
 * 以及四项关键数据条（在学课程 / 平均分 / 连续学习 / 待办事项）。</p>
 */
defineProps({
  /** 当前登录用户 */
  user: { type: Object, default: () => ({}) },
  /** 角色键（student/teacher/admin），决定标签配色 */
  roleKey: { type: String, default: 'student' },
  /** 角色中文名 */
  roleLabel: { type: String, default: '学生' },
  /** 头像文字（姓名/用户名首字符） */
  avatarText: { type: String, default: '?' },
  /** 加入时间文案 */
  memberSince: { type: String, default: '—' },
  /** 最近登录文案 */
  lastLogin: { type: String, default: '—' },
  /** 关键数据统计 */
  stats: { type: Object, default: () => ({}) },
})
</script>

<style scoped>
.profile-hero {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  overflow: hidden;
  margin-bottom: var(--ac-spacing-lg);
}
.hero-cover {
  position: relative;
  height: 128px;
  background:
    radial-gradient(circle at 16% 28%, rgba(255, 255, 255, 0.2), transparent 42%),
    radial-gradient(circle at 84% 72%, rgba(255, 255, 255, 0.12), transparent 46%),
    linear-gradient(120deg, var(--ac-primary) 0%, var(--accent-strong) 100%);
  overflow: hidden;
}
.hero-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(2px);
}
.hero-blob--1 {
  width: 180px;
  height: 180px;
  right: -40px;
  top: -70px;
  background: rgba(255, 255, 255, 0.14);
}
.hero-blob--2 {
  width: 120px;
  height: 120px;
  left: 8%;
  bottom: -60px;
  background: rgba(255, 255, 255, 0.1);
}
.hero-lower {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 28px 22px;
  margin-top: -42px;
}
.hero-avatar {
  position: relative;
  flex-shrink: 0;
}
.hero-avatar__img {
  border: 4px solid var(--ac-bg-elevated);
  background: linear-gradient(135deg, var(--ac-primary), var(--accent-strong)) !important;
  color: #fff !important;
  font-size: 34px;
  font-weight: 700;
  box-shadow: var(--ac-shadow-md);
}
.hero-avatar__status {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--ac-success);
  border: 3px solid var(--ac-bg-elevated);
}
.hero-text {
  min-width: 0;
  padding-bottom: 4px;
}
.hero-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}
.hero-handle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ac-text-muted);
}
.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
.hero-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.hero-stats {
  margin-left: auto;
  display: flex;
  gap: 30px;
  padding-bottom: 4px;
}
.hero-stat {
  text-align: center;
  min-width: 56px;
}
.hero-stat__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.1;
  letter-spacing: -0.5px;
}
.hero-stat__value i {
  font-size: 13px;
  font-style: normal;
  font-weight: 500;
  color: var(--ac-text-muted);
  margin-left: 2px;
}
.hero-stat__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

/* 角色标签 */
.role-tag {
  border: none;
  font-weight: 600;
}
.role-tag--student {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
}
.role-tag--teacher {
  background: var(--ac-warning-bg);
  color: var(--ac-warning);
}
.role-tag--admin {
  background: var(--ac-danger-bg);
  color: var(--ac-danger);
}

@media (max-width: 640px) {
  .hero-lower {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
    margin-top: -46px;
  }
  .hero-stats {
    margin-left: 0;
    width: 100%;
    justify-content: space-between;
    gap: 12px;
    padding-top: 14px;
    border-top: 1px solid var(--ac-border);
  }
  .hero-stat {
    min-width: 0;
  }
}
</style>
