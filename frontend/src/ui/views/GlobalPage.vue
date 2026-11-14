<template>
  <section class="module">
    <PageHead :title="def.title" :sub="def.desc" />

    <div v-if="tabs.length" class="tabs page-tabs" role="tablist" :aria-label="def.title">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="{ 'is-active': tabActive(tab.id) }"
        @click="selectTab(tab.id)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div
      v-if="notProvidedCopy"
      class="card empty-state"
      role="status"
      data-testid="page-not-provided"
    >
      <span class="e-ico"><Icon name="shield" /></span>
      <b>{{ notProvidedCopy.head }}</b>
      <p>{{ notProvidedCopy.detail }}</p>
    </div>

    <ExamGovernanceBody v-else-if="def.special === 'examGovernance'" />
    <AnalyticsTab v-else-if="def.special === 'analytics'" />
    <MessagesBody v-else-if="def.special === 'messages'" />
    <component :is="specialBodyComponent" v-else-if="specialBodyComponent" :key="page" />

    <!-- 兜底：既没有真实正文、也不是「待扩展」清单里的页面。示例层删除后不该再有这种页面，
         保留它只是防止将来漏配时白屏；文案不指向已下线的旧前端。 -->
    <div v-else class="card empty-state">
      <span class="e-ico"><Icon name="layers" /></span>
      <b>该页暂不提供数据</b>
      <p>本页暂无可展示内容。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, type AsyncComponentLoader } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHead from '../components/PageHead.vue'
import Icon from '../components/Icon.vue'
import AnalyticsTab from './course/tabs/AnalyticsTab.vue'
// ExamGovernanceBody / MessagesBody 都命中 registry 的 `*Body.vue` glob：静态导入会让
// Vite 报 INEFFECTIVE_DYNAMIC_IMPORT（动态导入失效、白赚一个分块）。与约定一致走懒加载。
const ExamGovernanceBody = defineAsyncComponent(() => import('./specials/ExamGovernanceBody.vue'))
const MessagesBody = defineAsyncComponent(() => import('./specials/MessagesBody.vue'))
import { specialBodyLoader } from './specials/registry'
import { notProvidedCopy as copyFor, pageDisposition } from '../notProvided'
import { pageDef, type Role } from '../nav'
import { useUi } from '../composables/ui'

const route = useRoute()
const router = useRouter()
const { role } = useUi()

const page = computed(() => String(route.meta.pageKey || 'home'))
const def = computed(() => pageDef(role.value as Role, page.value))

// 判为不提供／后端缺口的页面：正文只给明确空态，绝不回落示例读数（判据见前端待办项 §11.8.3）。
const notProvidedCopy = computed(() => {
  const disposition = pageDisposition(role.value as Role, page.value)
  if (disposition === 'provided') return null
  return copyFor(role.value as Role, page.value)
})

// 显式分支之外的页面按约定懒加载 <Special>Body.vue；文件未落地则保持 null，走示例块兜底。
const specialBodyComponent = computed(() => {
  const loader = specialBodyLoader(def.value.special)
  return loader ? defineAsyncComponent(loader as AsyncComponentLoader) : null
})

interface TabDef {
  id: string
  label: string
}

const tabs = computed<TabDef[]>(() => {
  // 学业档案的分区页签由 AcademicBody 自己持有（成绩／学习中心／错题本）；
  // 壳层只在页面本身没有正文分区时才出页签条，否则同页会出现两条 tablist。
  if (page.value === 'advising') {
    return [
      { id: 'overview', label: '我的班级' },
      { id: 'profile', label: '班级画像' },
      { id: 'alerts', label: '学生预警' },
      { id: 'notices', label: '班级通知' },
    ]
  }
  return []
})

const sub = computed(() => {
  if (page.value === 'advising') {
    const map: Record<string, string> = {
      advising: 'overview',
      advisingProfile: 'profile',
      advisingAlerts: 'alerts',
      advisingNotices: 'notices',
    }
    return map[page.value] || 'overview'
  }
  return String(route.query.tab || tabs.value[0]?.id || '')
})

// 页头动作区已整体移除：唯一还挂过按钮的两页（advising／resources）现在都是
// 「待扩展」空态，挂按钮只会弹一句「尚未接入」——把不提供的能力说成"快要有了"。

function tabActive(id: string): boolean {
  return sub.value === id
}

function selectTab(id: string) {
  if (page.value === 'advising') {
    const target = id === 'overview' ? '/teacher/advising' : `/teacher/advising/${id}`
    router.push(target)
    return
  }
  router.replace({ query: { ...route.query, tab: id } })
}
</script>
