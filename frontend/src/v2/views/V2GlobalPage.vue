<template>
  <section class="module">
    <V2PageHead :kicker="def.kicker" :title="def.title" :sub="def.desc" :source="source">
      <template #actions>
        <button
          v-for="action in actions"
          :key="action"
          class="btn btn-ghost"
          @click="notReady(action)"
        >
          {{ action }}
        </button>
      </template>
    </V2PageHead>

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
      <span class="e-ico"><V2Icon name="shield" /></span>
      <b>{{ notProvidedCopy.head }}</b>
      <p>{{ notProvidedCopy.detail }}</p>
    </div>

    <ExamGovernanceBody v-else-if="def.special === 'examGovernance'" />
    <AnalyticsTab v-else-if="def.special === 'analytics'" />
    <AiTab v-else-if="def.special === 'assistant'" :role="role" />
    <V2MessagesBody v-else-if="def.special === 'messages'" />
    <component :is="specialBodyComponent" v-else-if="specialBodyComponent" :key="page" />

    <template v-else-if="block">
      <div v-if="block.metrics.length" class="stat-grid kpi-grid">
        <V2Stat v-for="metric in block.metrics" :key="metric.label" :metric="metric" :role="role" />
      </div>
      <div class="module-grid">
        <V2Table v-if="block.table" :table="block.table" :kicker="tableKicker" />
        <aside v-if="block.side" class="card side-card">
          <p class="card-kicker">INSIGHT / 提示</p>
          <h2>{{ block.side.title }}</h2>
          <p class="side-body">{{ block.side.body }}</p>
          <ul class="side-list">
            <li v-for="item in block.side.items" :key="item">{{ item }}</li>
          </ul>
          <p class="caveat">{{ block.note || '示例数据' }}</p>
        </aside>
      </div>
      <p v-if="block.note" class="hint">{{ block.note }}</p>
    </template>

    <div v-else class="card empty-state">
      <span class="e-ico"><V2Icon name="layers" /></span>
      <b>该页尚未迁入新版</b>
      <p>旧版对应页面仍可继续使用；新版会在后续批次补齐。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, type AsyncComponentLoader } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import V2PageHead from '../components/V2PageHead.vue'
import V2Stat from '../components/V2Stat.vue'
import V2Table from '../components/V2Table.vue'
import V2Icon from '../components/V2Icon.vue'
import ExamGovernanceBody from './specials/V2ExamGovernanceBody.vue'
import AnalyticsTab from './course/tabs/AnalyticsTab.vue'
import AiTab from './course/tabs/AiTab.vue'
import V2MessagesBody from './specials/V2MessagesBody.vue'
import { specialBodyLoader } from './specials/registry'
import { notProvidedCopy as copyFor, pageDisposition } from '../notProvided'
import { pageDef, type V2Role } from '../nav'
import { useV2Ui } from '../composables/ui'
import { demoBlock } from '../data/pageData'

const route = useRoute()
const router = useRouter()
const { role, toast } = useV2Ui()

const page = computed(() => String(route.meta.v2page || 'home'))
const def = computed(() => pageDef(role.value as V2Role, page.value))

// 判为不提供／后端缺口的页面：正文只给明确空态，绝不回落示例读数（判据见前端待办项 §11.8.3）。
const notProvidedCopy = computed(() => {
  const disposition = pageDisposition(role.value as V2Role, page.value)
  if (disposition === 'provided') return null
  return copyFor(role.value as V2Role, page.value)
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
  if (page.value === 'academic') {
    // 本期学业档案只提供成绩；错题本 / 学习路径尚无后端与正文，先不挂空页签。
    return [{ id: 'grades', label: '成绩' }]
  }
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

const block = computed(() => demoBlock(role.value as V2Role, page.value, sub.value))
const source = computed<'api' | 'demo' | 'mixed' | undefined>(() => {
  // 「不提供」与「后端尚未提供」既不是接口读数也不是示例读数，不挂来源章。
  if (notProvidedCopy.value) return undefined
  // 显式分支中的考试治理与消息已接真实接口。
  if (page.value === 'messages' || page.value === 'examGovernance') return 'api'
  // 约定 Body 真实存在时模板渲染的是接口正文，不再标「示例数据」；
  // loader 为 null 时才轮到示例块，没有示例块则不挂来源章。
  if (specialBodyComponent.value) return 'api'
  return block.value ? 'demo' : undefined
})
const tableKicker = computed(() => `${role.value.toUpperCase()} / ${page.value.toUpperCase()}`)

const actions = computed(() => {
  // 学业档案的导出端点尚未提供，本页不挂「导出成绩」以免 toast 成演示动作。
  // 收件箱三块由正文自带动作，壳层不再挂「批量确认 / 导出」这类只会 toast 的演示动作。
  if (page.value === 'advising') return ['发送班级通知']
  if (page.value === 'resources') return ['上传资源', '导出']
  return []
})

function tabActive(id: string): boolean {
  return sub.value === id
}

function selectTab(id: string) {
  if (page.value === 'advising') {
    const target = id === 'overview' ? '/v2/teacher/advising' : `/v2/teacher/advising/${id}`
    router.push(target)
    return
  }
  router.replace({ query: { ...route.query, tab: id } })
}

function notReady(label: string) {
  toast(`演示数据：${label}尚未接入`, 'warn')
}
</script>
