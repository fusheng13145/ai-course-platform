<template>
  <div class="settings-grid">
    <section v-if="role !== 'admin'" class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">BASIC / 课程</p>
          <h2>基本信息</h2>
        </div>
        <span class="chip chip-ok">{{ approvalLabel(course?.approvalStatus) }}</span>
      </header>
      <div class="setting-form">
        <label><span>课程名称</span><input :value="course?.name || ''" readonly /></label>
        <label><span>课程编号</span><input :value="course?.code || ''" readonly /></label>
        <label><span>学期</span><input :value="course?.semester || ''" readonly /></label>
        <label><span>学分</span><input :value="course?.credits ?? ''" readonly /></label>
        <label><span>分类</span><input :value="course?.category || ''" readonly /></label>
        <label
          ><span>审批状态</span><input :value="approvalLabel(course?.approvalStatus)" readonly
        /></label>
      </div>
      <p class="hint">
        课程编号按学期唯一；课程复制到新学期属于后续能力。修改入口仍在旧版课程详情。
      </p>
    </section>

    <section class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">WORKFLOW / 审批</p>
          <h2>课程状态</h2>
        </div>
      </header>
      <div class="workflow-list">
        <div class="workflow-step is-done">
          <span>01</span><b>教师创建课程</b><small>已完成</small>
        </div>
        <div class="workflow-step is-done">
          <span>02</span><b>院系管理员审批</b
          ><small>{{ course?.approvalStatus || '已通过' }}</small>
        </div>
        <div class="workflow-step is-done">
          <span>03</span><b>发布教学班</b><small>教学班模型待接入</small>
        </div>
      </div>
      <p class="hint">审批记录不可编辑；管理员课程治理页只读。</p>
    </section>

    <section v-if="role !== 'admin'" class="card">
      <header class="card-head">
        <div>
          <p class="card-kicker">CLASSES / 教学班</p>
          <h2>教学班设置</h2>
        </div>
        <span class="chip chip-warn">待接入</span>
      </header>
      <div class="mini-class-list">
        <div v-for="cls in classes" :key="cls.name" class="mini-class">
          <span
            ><b>{{ cls.name }}</b
            ><small>{{ cls.source }}</small></span
          >
          <span
            ><b>{{ cls.students }}</b
            ><small>学生</small></span
          >
        </div>
      </div>
      <p class="hint">教学班是课程下的开课单元；后端 teaching_class 尚未落地，这里沿用原型样本。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { IA } from '../../../data/demoSource'
import { approvalLabel } from '../../../utils'
import type { V2Role } from '../../../nav'

interface CourseLike {
  name?: string
  code?: string
  semester?: string
  credits?: number | null
  category?: string
  approvalStatus?: string
}

const props = defineProps<{ role: V2Role; course: CourseLike | null; courseId: number }>()

const classes = computed(() => {
  const courses = IA.courses as unknown as Array<{
    id: number
    classes: Array<{ name: string; students: number; source: string }>
  }>
  return (courses.find((item) => item.id === props.courseId) || courses[0])?.classes || []
})
</script>
