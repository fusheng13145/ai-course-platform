<template>
  <div class="error-page">
    <div class="error-card">
      <h1>403</h1>
      <p>抱歉，您没有权限访问此页面</p>
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