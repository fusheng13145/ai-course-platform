/* eslint-env node */
module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module'
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    '@vue/eslint-config-typescript',
    'prettier'
  ],
  rules: {
    // 与 Prettier 协作：关闭会与其冲突或过度侵入的规则
    'vue/multi-word-component-names': 'off',
    'vue/no-v-html': 'warn', // ⚠ 安全关键规则：v-html 绕过 XSS 防护，仅在有 DOMPurify 清洗的必要时使用
    'vue/no-unused-vars': 'off',
    // 关键质量规则：重新启用（批判性评估报告要求）
    'no-console': 'warn', // 生产错误不再静默 console，改用用户可见反馈/日志服务；开发调试处保留并加 eslint-disable
    'no-empty': 'error', // 禁止空代码块（含空 catch），必须显式处理或记录
    // 按 typescript-eslint 官方规范：使用 TS 版本时必须关闭基础规则，否则同一处会被双重误报。
    // @typescript-eslint/no-unused-vars 在本配置下对 .js/.vue/.ts 均生效，覆盖无缺口。
    'no-unused-vars': 'off',
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' }], // 覆盖全部脚本；下划线前缀表示"有意保留"
    '@typescript-eslint/no-explicit-any': 'warn', // 收敛显式 any（TS/Vue 模板脚本）
    '@typescript-eslint/no-non-null-assertion': 'off',
    '@typescript-eslint/no-unused-expressions': 'off',
    'no-undef': 'off',
    'no-prototype-builtins': 'off',
    'no-case-declarations': 'off',
    'no-constant-condition': 'off',
    'prefer-const': 'warn'
  },
  ignorePatterns: ['node_modules', 'dist', 'package-lock.json', 'src/**/*.d.ts']
}
