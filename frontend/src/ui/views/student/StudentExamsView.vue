<template>
  <section class="module">
    <PageHead :title="def.title" :sub="def.desc" />
    <StudentExamPanel />
  </section>
</template>

<script setup lang="ts">
import PageHead from '../../components/PageHead.vue'
import StudentExamPanel from '../exam/StudentExamPanel.vue'
import { pageDef } from '../../nav'
import { useUi } from '../../composables/ui'

const { role } = useUi()
const def = pageDef(role.value, 'exams')
</script>
