<template>
  <div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本设置" name="basic">
        <div class="content-card" style="max-width:600px">
          <el-form label-width="120px">
            <el-form-item label="站点名称"><el-input v-model="config.siteName" /></el-form-item>
            <el-form-item label="站点描述"><el-input v-model="config.siteDescription" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="每页条数"><el-input-number v-model="config.pageSize" :min="10" :max="100" /></el-form-item>
            <el-form-item label="注册开关"><el-switch v-model="config.allowRegister" /></el-form-item>
            <el-form-item><el-button type="primary">保存设置</el-button></el-form-item>
          </el-form>
        </div>
      </el-tab-pane>
      <el-tab-pane label="系统信息" name="info">
        <div class="content-card">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="系统版本">v1.0.0</el-descriptions-item>
            <el-descriptions-item label="Java版本">25.0.2</el-descriptions-item>
            <el-descriptions-item label="数据库">MySQL 8.0</el-descriptions-item>
            <el-descriptions-item label="服务器运行时间">2天 3小时</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
const activeTab = ref('basic')
const config = reactive({ siteName: 'AI智能课程平台', siteDescription: '基于AI技术的智能学习平台', pageSize: 20, allowRegister: true })
</script>