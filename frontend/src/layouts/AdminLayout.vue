<template>
  <AppShell :menus="ADMIN_MENUS" role="ADMIN" notification-path="/admin/message-center" />
</template>

<script setup>
import AppShell from './AppShell.vue'
import { ADMIN_MENUS } from './menus'
</script>
