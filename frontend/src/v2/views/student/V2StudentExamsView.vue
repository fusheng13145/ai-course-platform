<template>
  <section class="module">
    <V2PageHead :kicker="def.kicker" :title="def.title" :sub="def.desc" source="api" />
    <StudentExamPanel />
  </section>
</template>

<script setup lang="ts">
import V2PageHead from '../../components/V2PageHead.vue'
import StudentExamPanel from '../exam/StudentExamPanel.vue'
import { pageDef } from '../../nav'
import { useV2Ui } from '../../composables/ui'

const { role } = useV2Ui()
const def = pageDef(role.value, 'exams')
</script>
