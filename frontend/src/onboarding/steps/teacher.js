export default [
  {
    id: 'welcome',
    title: '这里是教师端',
    desc: '左边是你要用的几件事：出题考试、看成绩、管课堂、用 AI。下面走一遍。',
    scope: 'shell',
  },
  {
    id: 'assess',
    title: '试题与考试',
    desc: '从这里建题、组卷、发布考试。题库和试卷库在它下面两级。',
    anchor: 'nav:t-assess',
    scope: 'shell',
  },
  {
    id: 'grades',
    title: '成绩总览',
    desc: '点这里看全班成绩，导出的表就是这一页上那份。',
    anchor: 'nav:t-grades',
    scope: 'shell',
  },
  {
    id: 'reopen',
    title: '随时回来看',
    desc: '左下角你的账号里放着「新手引导」，随时能重走。',
    anchor: 'side-user',
    scope: 'shell',
  },
  {
    id: 'activate',
    title: '用 AI 出一批题',
    desc: '点这里进 AI 教学中心，选知识点就能生成题目草稿，改完再入库。',
    anchor: 'nav:t-ai',
    scope: 'shell',
    action: { type: 'navigate', to: '/teacher/ai-center' },
    expect: '进入 AI 教学中心，引导结束',
  },
]
