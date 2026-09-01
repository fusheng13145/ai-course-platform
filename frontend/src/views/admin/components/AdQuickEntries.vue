<template>
  <!-- 系统管理快捷入口 -->
  <section class="l1-card animate-in d1">
    <div class="l1-head">
      <span class="l1-ico"
        ><svg
          viewBox="0 0 24 24"
          width="17"
          height="17"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg
      ></span>
      <div>
        <div class="l1-title">系统管理</div>
        <div class="l1-sub">用户权限 / 角色管理 / 系统设置</div>
      </div>
      <div class="l1-extra">
        <router-link to="/admin/users" class="btn btn-primary btn-sm">进入用户管理</router-link>
      </div>
    </div>
    <div class="l1-body">
      <div class="grid-3">
        <div class="l3-block hoverable" @click="$router.push('/admin/users')">
          <div class="l3-title">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"
              />
            </svg>
            用户权限
          </div>
          <div class="entry-desc">平台全部用户账号管理<br />按角色分组折叠 · 权限树配置</div>
          <div class="flex gap-8 mt-8">
            <span class="tag violet">{{ fmt(overview.totalUsers) }} 用户</span
            ><span class="tag green">{{ fmt(overview.roleDistribution?.teacher) }} 教师</span
            ><span class="tag blue">{{ fmt(overview.roleDistribution?.student) }} 学生</span>
          </div>
        </div>
        <div class="l3-block hoverable" @click="$router.push('/admin/permissions')">
          <div class="l3-title">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="m21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4"
              />
            </svg>
            角色管理
          </div>
          <div class="entry-desc">角色权限矩阵配置<br />内置角色 + 自定义角色</div>
          <div class="flex gap-8 mt-8">
            <span class="tag violet">管理员</span><span class="tag blue">教师</span
            ><span class="tag">学生</span><span class="tag outline">自定义</span>
          </div>
        </div>
        <div class="l3-block hoverable" @click="$router.push('/admin/system')">
          <div class="l3-title">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6" />
            </svg>
            系统设置
          </div>
          <div class="entry-desc">基础设置 / 安全策略<br />存储配额 / 在线预览配置</div>
          <div class="flex gap-8 mt-8"><span class="tag green">运行中</span></div>
        </div>
      </div>
    </div>
  </section>

  <!-- 教学管理快捷入口 -->
  <section class="l1-card">
    <div class="l1-head">
      <span class="l1-ico ico-info"
        ><svg
          viewBox="0 0 24 24"
          width="17"
          height="17"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z"
          /></svg
      ></span>
      <div>
        <div class="l1-title">教学管理</div>
        <div class="l1-sub">课程库 / 专业班级 / 排课分配</div>
      </div>
      <div class="l1-extra">
        <router-link to="/admin/courses" class="btn btn-primary btn-sm">课程库总览</router-link>
      </div>
    </div>
    <div class="l1-body">
      <div class="grid-3">
        <div class="l3-block hoverable" @click="$router.push('/admin/courses')">
          <div class="l3-title">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            </svg>
            课程库总览
          </div>
          <div class="entry-text">
            <b>{{ fmt(overview.totalCourses) }}</b> 门 · 活跃
            <b>{{ fmt(overview.activeCourses) }}</b>
          </div>
        </div>
        <div class="l3-block hoverable" @click="$router.push('/admin/classes')">
          <div class="l3-title">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2z" />
            </svg>
            专业班级管理
          </div>
          <div class="entry-text">院系 → 专业 → 班级 三级树</div>
        </div>
        <div class="l3-block hoverable" @click="$router.push('/admin/supervision')">
          <div class="l3-title">
            <svg
              viewBox="0 0 24 24"
              width="14"
              height="14"
              fill="none"
              stroke="currentColor"
              stroke-width="1.9"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
              />
            </svg>
            排课与课程分配
          </div>
          <div class="entry-text">教师 → 课程 → 班级级联</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps({
  overview: { type: Object, default: () => ({}) },
})

function fmt(n) {
  return (n ?? 0).toLocaleString('zh-CN')
}
</script>

<style scoped>
.entry-desc {
  font-size: 12px;
  color: var(--ink-3);
  line-height: 1.6;
}
.entry-text {
  font-size: 12px;
  color: var(--ink-3);
}
.ico-info {
  background: var(--info-soft);
  color: var(--info);
}
</style>
