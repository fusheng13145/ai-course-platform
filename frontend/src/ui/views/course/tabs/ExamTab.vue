<template>
  <div>
    <div class="tabs" role="tablist" aria-label="考试视图">
      <button :class="{ 'is-active': sub === 'papers' }" @click="sub = 'papers'">
        {{ role === 'student' ? '考试记录' : '试卷与安排' }}
      </button>
      <button
        v-if="role !== 'student'"
        :class="{ 'is-active': sub === 'proctor' }"
        @click="sub = 'proctor'"
      >
        在线监考
      </button>
    </div>

    <div v-if="sub === 'papers'" class="module-grid">
      <Table :table="table" :kicker="role === 'student' ? 'EXAMS / RECORD' : 'PAPERS / COURSE'" />
      <aside class="card side-card">
        <p class="card-kicker">EXAM / 口径</p>
        <h2>考试与批阅</h2>
        <p class="side-body">
          考试会话与试卷分离：试卷是课程级内容，考试发布到教学班；监考风险只作线索。
        </p>
        <ul class="side-list">
          <li>考中事件由教师核实</li>
          <li>客观题自动判分，主观题教师确认</li>
          <li>成绩以教师提交为准</li>
        </ul>
        <button class="btn btn-ghost btn-block" @click="startExam">开始模拟考试</button>
        <p class="caveat">示例数据 · 考试执行仍在旧版</p>
      </aside>
    </div>

    <div v-else class="module-grid">
      <section class="card proctor-card">
        <div class="proctor-head">
          <span class="live-dot"></span>
          <div>
            <p class="card-kicker">LIVE / SESSION 4007-09</p>
            <h2>计算机网络 · 期中测验</h2>
          </div>
          <b class="proctor-timer">42:18</b>
        </div>
        <div class="proctor-stats">
          <span><b>60</b>考生</span>
          <span><b>58</b>在线</span>
          <span><b>2</b>风险事件</span>
          <span><b>31s</b>故障检测</span>
        </div>
        <div class="proctor-chart">
          <div class="proctor-chart-head"><span>响应耗时 / 5 MIN</span><b>31s</b></div>
          <Sparkline :values="[22, 26, 31, 29, 34, 31, 28]" :width="280" :height="44" />
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>学生</th>
                <th>状态</th>
                <th>切屏</th>
                <th>风险</th>
                <th>最近活动</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in proctorRows" :key="row.name">
                <td>{{ row.name }}</td>
                <td>{{ row.online }}</td>
                <td>{{ row.switches }}</td>
                <td>
                  <span class="chip" :class="`chip-${row.tone}`">{{ row.risk }}</span>
                </td>
                <td>{{ row.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <aside class="card side-card">
        <p class="card-kicker">RISK / 规则</p>
        <h2>监考边界</h2>
        <p class="side-body">切屏、多设备与实时通道异常只作为线索，不自动判定作弊；由教师核实。</p>
        <ul class="side-list">
          <li>教师只监考本人教学班</li>
          <li>事件留痕不可编辑</li>
          <li>管理员只读治理视图</li>
        </ul>
        <p class="caveat">示例数据 · 监考端点已在旧版可用</p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import Table from '../../../components/Table.vue'
import Sparkline from '../../../components/Sparkline.vue'
import { findModule } from '../../../data/modules'
import type { Role, Table as TableType } from '../../../nav'
import { useUi } from '../../../composables/ui'

const props = defineProps<{ role: Role }>()
const ui = useUi()
const sub = ref<'papers' | 'proctor'>('papers')

const table = computed<TableType>(() => {
  const id = props.role === 'student' ? 'exams' : 'papers'
  return (
    findModule(props.role === 'admin' ? 'teacher' : props.role, id)?.table || {
      title: '试卷与考试',
      columns: ['考试', '状态', '时间', '操作'],
      rows: [['计算机网络 · 期中测验', { text: '进行中', chip: 'ok' }, '10-31 09:00', '查看']],
    }
  )
})

const proctorRows = [
  { name: '林知远', online: '在线', switches: '0', risk: '正常', tone: 'ok', time: '14:18' },
  { name: '王雨桐', online: '在线', switches: '1', risk: '切屏', tone: 'warn', time: '14:12' },
  { name: '赵一凡', online: '在线', switches: '1', risk: '多设备', tone: 'danger', time: '14:08' },
  { name: '陈子默', online: '在线', switches: '0', risk: '正常', tone: 'ok', time: '14:06' },
  { name: '周可欣', online: '离线', switches: '0', risk: '已交卷', tone: 'mute', time: '13:58' },
]

function startExam() {
  ui.toast('考试执行仍在旧版（/student/exams）；新版后续批次接入', 'info')
}
</script>
