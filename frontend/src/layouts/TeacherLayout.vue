<template>
  <AppShell :menus="TEACHER_MENUS" role="TEACHER" />
</template>

<script setup>
import AppShell from './AppShell.vue'
import { TEACHER_MENUS } from './menus'
</script>
