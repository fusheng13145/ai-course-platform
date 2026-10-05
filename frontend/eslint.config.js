import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import vueParser from 'vue-eslint-parser'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: [
      'node_modules/**',
      'dist/**',
      'dist_probe/**',
      'coverage/**',
      'package-lock.json',
      'src/**/*.d.ts',
    ],
  },
  js.configs.recommended,
  pluginVue.configs['flat/recommended'],
  // 等价于旧配置里 @vue/eslint-config-typescript 的 extends: 'plugin:@typescript-eslint/eslint-recommended'：
  // 使用 TS 解析器时必须关闭这些基础规则，否则同一处会被 TS 与 ESLint 双重误报。
  { ...tseslint.configs.eslintRecommended, files: ['**/*.{ts,mts,cts,tsx,vue}'] },
  {
    files: ['src/**/*.{js,ts,vue}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      // .vue 由 vue-eslint-parser 解析模板，脚本块按后缀委派给对应解析器；
      // .js/.ts 走下面的顶层解析器设置。
      parser: vueParser,
      parserOptions: {
        parser: {
          js: 'espree',
          ts: tseslint.parser,
        },
        extraFileExtensions: ['.vue'],
        ecmaVersion: 2022,
        sourceType: 'module',
      },
    },
    plugins: { '@typescript-eslint': tseslint.plugin },
    rules: {
      // 与 Prettier 协作：关闭会与其冲突或过度侵入的规则
      'vue/multi-word-component-names': 'off',
      // shallowOnly：禁止整体替换 prop 引用，允许编辑传入对象/数组的嵌套属性
      // （表单 v-model 绑定父组件 reactive 对象的常见模式，非 prop 重新赋值）
      'vue/no-mutating-props': ['error', { shallowOnly: true }],
      'vue/no-v-html': 'warn', // ⚠ 安全关键规则：v-html 绕过 XSS 防护，仅在有 DOMPurify 清洗的必要时使用
      'vue/no-unused-vars': 'off',
      // 关键质量规则：重新启用（批判性评估报告要求）
      // 保留 error/warn 错误日志（全局错误处理器与 catch 降级路径需要），
      // 禁止 log/info/debug 调试残留；开发调试处保留并加 eslint-disable
      'no-console': ['warn', { allow: ['error', 'warn'] }],
      'no-empty': 'error', // 禁止空代码块（含空 catch），必须显式处理或记录
      // 按 typescript-eslint 官方规范：使用 TS 版本时必须关闭基础规则，否则同一处会被双重误报。
      // @typescript-eslint/no-unused-vars 在本配置下对 .js/.vue/.ts 均生效，覆盖无缺口。
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        // caughtErrors: 'none' 用于保持升级前后的 lint 输出一致——typescript-eslint v6 默认不检查
        // catch 形参，v8 把默认值改为 'all'，直接切换会在 20+ 个既有文件一次性引入 76 条 warning。
        // 该收敛应作为独立任务处理，不混入工具链升级。
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrors: 'none',
          caughtErrorsIgnorePattern: '^_',
        },
      ], // 覆盖全部脚本；下划线前缀表示"有意保留"
      '@typescript-eslint/no-explicit-any': 'warn', // 收敛显式 any（TS/Vue 模板脚本）
      '@typescript-eslint/no-non-null-assertion': 'off',
      '@typescript-eslint/no-unused-expressions': 'off',
      'no-undef': 'off',
      'no-prototype-builtins': 'off',
      'no-case-declarations': 'off',
      'no-constant-condition': 'off',
      'prefer-const': 'warn',
    },
  },
  prettier
)
