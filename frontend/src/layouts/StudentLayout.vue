<template>
  <AppShell :menus="STUDENT_MENUS" role="STUDENT" />
</template>

<script setup>
import AppShell from './AppShell.vue'
import { STUDENT_MENUS } from './menus'
</script>
