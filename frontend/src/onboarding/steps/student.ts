import type { OnboardingStep } from './types'

const steps: OnboardingStep[] = [
  {
    id: 'welcome',
    title: '这里是学生端',
    desc: '课程、作业、考试和 AI 助手都在左边那条栏里。下面四步，每步点一下高亮的地方就能走完。',
    scope: 'shell',
  },
  {
    id: 'ai',
    title: 'AI 助手',
    desc: '上课没听懂的地方直接在这里问，它会带着这门课的知识图谱来回答。',
    anchor: 'nav:ai-assistant',
    scope: 'shell',
    expect: '进入 AI 助手页',
  },
  {
    id: 'graph',
    title: '知识图谱',
    desc: '点这里看这门课的知识点关系图，能看出你哪一块还没掌握。',
    anchor: 'nav:graph',
    scope: 'shell',
  },
  {
    id: 'reopen',
    title: '随时回来看',
    desc: '这里就是你的账号，点开有「新手引导」，忘了路径随时回来重走一遍。',
    anchor: 'side-user',
    scope: 'shell',
  },
  {
    id: 'activate',
    title: '先加入一门课',
    desc: '点这里，输入老师给的邀请码。加入之后这门课的作业、考试和进度才会出现。',
    anchor: 'join-course',
    scope: 'page',
    action: { type: 'click-anchor' },
    expect: '打开加入课程弹窗，引导结束',
  },
]

export default steps
