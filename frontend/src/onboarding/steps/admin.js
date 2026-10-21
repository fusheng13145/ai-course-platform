export default [
  {
    id: 'welcome',
    title: '这里是管理端',
    desc: '左边按"治理—资源—系统"三段排。先看三处，最后一步带你进审计。',
    scope: 'shell',
  },
  {
    id: 'governance',
    title: '治理大盘',
    desc: '全校教学运行状态在这一页，异常从这里的卡片点进去。',
    anchor: 'nav:dashboard',
    scope: 'shell',
  },
  {
    id: 'users',
    title: '用户与账号',
    desc: '开课、换老师、停账号都在这。改角色权限要到下一项。',
    anchor: 'nav:users',
    scope: 'shell',
  },
  {
    id: 'reopen',
    title: '随时回来看',
    desc: '左下角你的账号里有「新手引导」，随时重走。',
    anchor: 'side-user',
    scope: 'shell',
  },
  {
    id: 'activate',
    title: '先看审计',
    desc: '点这里进审计——谁在什么时候改过什么都在这一张表里。',
    anchor: 'nav:audit',
    scope: 'shell',
    action: { type: 'navigate', to: '/admin/logs' },
    expect: '进入操作日志页，引导结束',
  },
]
