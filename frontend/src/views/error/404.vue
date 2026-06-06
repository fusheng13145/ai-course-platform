<template>
  <div class="error-page">
    <div class="error-card">
      <h1>404</h1>
      <p>页面不存在</p>
      <el-button type="primary" @click="$router.push('/login')">返回首页</el-button>
    </div>
  </div>
</template>
<style scoped>
.error-page { height:100vh; display:flex; align-items:center; justify-content:center; background:#f0f2f5; }
.error-card { text-align:center; }
.error-card h1 { font-size:120px; color:#409eff; margin:0; line-height:1; }
.error-card p { font-size:16px; color:#909399; margin:16px 0 24px; }
</style>