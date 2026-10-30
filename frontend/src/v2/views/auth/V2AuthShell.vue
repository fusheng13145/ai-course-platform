<template>
  <V2Sprite />
  <div class="auth-v2">
    <header class="av-top">
      <a class="av-brand" href="/">
        <span class="brand-mark">枢</span>
        <span class="av-brand-text">
          <b>云枢教学平台</b>
          <small>YUNSHU / RECORD WORKSPACE</small>
        </span>
      </a>
      <div class="av-actions">
        <button
          class="icon-btn"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          :aria-pressed="theme === 'dark'"
          @click="toggleTheme"
        >
          <V2Icon :name="theme === 'dark' ? 'sun' : 'moon'" />
        </button>
      </div>
    </header>

    <main class="av-main">
      <section class="av-card">
        <aside class="av-story">
          <p class="eyebrow">YUNSHU / TEACHING RECORD / 2026</p>
          <h1>把每一次批阅，<br /><em>留成可复核的记录。</em></h1>
          <p class="lede">
            课程、教学班、提交与成绩在同一条记录线上。AI 只给建议，教师保留最终确认权。
          </p>
          <div class="av-record" aria-hidden="true">
            <div class="av-record-head">
              <span>RECORD / 作业 05</span>
              <span class="chip chip-warn">演示记录</span>
            </div>
            <div class="av-record-row"><span>林知远 · 网络层拥塞控制</span><b>86</b></div>
            <div class="av-record-row"><span>王雨桐 · 网络层拥塞控制</span><b>92</b></div>
            <div class="av-record-row"><span>陈子默 · 网络层拥塞控制</span><b>78</b></div>
            <div class="av-record-bar"><i style="width: 68%"></i></div>
            <div class="av-record-foot">
              <span>AI 建议 18 份</span>
              <span>教师确认后写入成绩</span>
            </div>
          </div>
          <ul class="av-points">
            <li>课程上下文</li>
            <li>教学班名单</li>
            <li>批阅记录</li>
            <li>AI 可追溯</li>
          </ul>
        </aside>
        <section class="av-panel">
          <slot name="form" />
        </section>
      </section>
    </main>

    <footer class="av-foot">
      <span>云枢教学平台 · v2 记录工作台</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import '../../styles/record.css'
import '../../styles/shell-fix.css'
import '../../styles/typography.css'
import '../../styles/auth.css'
import V2Sprite from '../../components/V2Sprite.vue'
import V2Icon from '../../components/V2Icon.vue'
import { useV2Ui } from '../../composables/ui'
import { useV2Root } from '../../composables/useV2Root'

const { theme, toggleTheme } = useV2Ui()
useV2Root(theme)
</script>
