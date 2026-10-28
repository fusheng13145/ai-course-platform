<template>
  <div class="module-grid graph-layout">
    <section class="card graph-card">
      <header class="card-head">
        <div>
          <p class="card-kicker">GRAPH / {{ nodes.length }} NODES · {{ edges.length }} EDGES</p>
          <h2>课程知识图谱</h2>
        </div>
        <span class="chip chip-warn">{{ weakCount }} 个薄弱点</span>
      </header>
      <div class="graph-canvas">
        <svg viewBox="0 0 700 540" role="img" aria-label="课程知识图谱">
          <line
            v-for="(edge, index) in edges"
            :key="`e-${index}`"
            class="graph-edge"
            :x1="position(edge[0]).x"
            :y1="position(edge[0]).y"
            :x2="position(edge[1]).x"
            :y2="position(edge[1]).y"
          />
          <g
            v-for="node in nodes"
            :key="node.id"
            class="graph-node"
            :class="{ weak: node.weak, core: node.kind === 'core' }"
            tabindex="0"
            role="button"
            :aria-label="`${node.label}，掌握度 ${node.mastery}%`"
            @click="select(node)"
            @keydown.enter="select(node)"
          >
            <circle :cx="node.x" :cy="node.y" :r="node.r" />
            <text :x="node.x" :y="node.y">{{ node.label }}</text>
          </g>
        </svg>
      </div>
      <div class="graph-legend">
        <span><i class="lg core"></i>核心概念</span>
        <span><i class="lg weak"></i>薄弱点 &lt; 60%</span>
        <span><i class="lg"></i>已掌握</span>
      </div>
    </section>
    <aside class="card side-card">
      <p class="card-kicker">GRAPH / 节点</p>
      <h2>{{ selected?.label || '选择一个节点' }}</h2>
      <p class="side-body">
        {{
          selected
            ? `掌握度 ${selected.mastery}%。节点来自课程知识点，薄弱点会被推入学习路径。`
            : '点击左侧节点查看掌握度；真实知识图谱接口已存在，本版先展示原型快照。'
        }}
      </p>
      <ul class="side-list">
        <li v-for="item in weakNodes" :key="item.id">
          {{ item.label }} · 掌握度 {{ item.mastery }}%
        </li>
      </ul>
      <p class="caveat">示例数据 · 知识图谱端点尚未接入本页</p>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { WEAK_EDGES, WEAK_NODES } from '../../../data/demoSource'

interface Node {
  id: string
  label: string
  x: number
  y: number
  r: number
  mastery: number
  weak?: boolean
  kind?: string
}

const nodes = WEAK_NODES as unknown as Node[]
const edges = WEAK_EDGES as unknown as Array<[string, string]>
const selected = ref<Node | null>(null)
const weakNodes = computed(() => nodes.filter((node) => node.weak))
const weakCount = computed(() => weakNodes.value.length)

function position(id: string) {
  const node = nodes.find((item) => item.id === id)
  return { x: node?.x || 0, y: node?.y || 0 }
}

function select(node: Node) {
  selected.value = node
}
</script>
