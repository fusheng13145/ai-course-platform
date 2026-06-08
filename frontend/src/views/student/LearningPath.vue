<template>
  <div>
    <div class="content-card" style="margin-bottom:var(--ac-spacing-md)">
      <h3 class="section-title">我的学习路径</h3>
      <el-timeline v-if="pathNodes.length > 0">
        <el-timeline-item 
          v-for="item in pathNodes" 
          :key="item.id" 
          :type="item.completed ? 'success' : item.current ? 'primary' : 'info'" 
          :timestamp="item.time"
        >
          {{ item.title }}
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无学习路径，请先生成" />
    </div>
    <div class="content-card">
      <h4 class="section-subtitle">推荐路径</h4>
      <el-row :gutter="16" v-if="paths.length > 0">
        <el-col :span="8" v-for="p in paths" :key="p.id" style="margin-bottom:12px">
          <el-card shadow="hover">
            <h4 class="card-title">{{ p.name }}</h4>
            <p class="card-desc">{{ p.count }} 个知识点</p>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无推荐路径" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import learningPathApi from '@/api/learningPath'

const pathNodes = ref([])
const paths = ref([])

onMounted(async () => {
  try {
    const res = await learningPathApi.getMyPaths()
    const pathList = res.data || res || []
    if (pathList.length > 0) {
      paths.value = pathList.map(p => ({
        id: p.id,
        name: p.pathName || '学习路径',
        count: p.totalStages || 0
      }))
      if (pathList[0].id) {
        const nodesRes = await learningPathApi.getPathNodes(pathList[0].id)
        const nodes = nodesRes.data || nodesRes || []
        pathNodes.value = nodes.map(n => ({
          id: n.id,
          title: n.title || n.description || '学习节点',
          time: n.completedAt || '进行中',
          completed: n.status === 'COMPLETED',
          current: n.status === 'IN_PROGRESS'
        }))
      }
    }
  } catch (e) {
    console.error('Failed to load learning paths:', e)
  }
})
</script>

<style scoped>
.section-title {
  margin: 0 0 var(--ac-spacing-md);
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.section-subtitle {
  margin: 0 0 var(--ac-spacing-md);
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.card-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.card-desc {
  margin: 0;
  font-size: 13px;
  color: var(--ac-text-secondary);
}
</style>