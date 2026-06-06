<template>
  <div>
    <div class="content-card" style="margin-bottom:20px">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px">
        <h3 style="margin:0">{{ currentMonth }} 学习日历</h3>
        <div><el-button @click="prevMonth">〈</el-button><el-button @click="nextMonth">〉</el-button></div>
      </div>
      <el-calendar v-model="currentDate">
        <template #date-cell="{ data }">
          <div :class="{ 'has-study': studyDays.includes(data.day) }" style="padding:4px">
            {{ data.day.split('-')[2] }}
            <div v-if="studyDays.includes(data.day)" style="font-size:10px;color:#409eff;line-height:1">●</div>
          </div>
        </template>
      </el-calendar>
    </div>
    <div class="content-card">
      <h4 style="margin:0 0 12px">学习统计</h4>
      <el-row :gutter="16">
        <el-col :span="6" v-for="s in stats" :key="s.label">
          <div style="text-align:center;padding:16px;background:#f5f7fa;border-radius:8px">
            <div style="font-size:24px;font-weight:600;color:#409eff">{{ s.value }}</div>
            <div style="font-size:13px;color:#909399;margin-top:4px">{{ s.label }}</div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>
<script setup>
import { ref, computed } from 'vue'
import dayjs from 'dayjs'
const currentDate = ref(new Date())
const currentMonth = computed(() => dayjs(currentDate.value).format('YYYY年MM月'))
const studyDays = ref(["2026-06-01", "2026-06-02", "2026-06-03", "2026-06-05", "2026-06-06"])
const stats = [{ label: "本月学习天数", value: 15 }, { label: "总学习时长", value: "42h" }, { label: "完成课程", value: 2 }, { label: "最高连续", value: 7 }]
function prevMonth() { currentDate.value = dayjs(currentDate.value).subtract(1, 'month').toDate() }
function nextMonth() { currentDate.value = dayjs(currentDate.value).add(1, 'month').toDate() }
</script>
<style scoped>
.has-study { font-weight: 600; }
</style>