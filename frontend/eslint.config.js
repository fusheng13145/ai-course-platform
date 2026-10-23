import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import globals from 'globals'
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
        // caughtErrors 保持 typescript-eslint 默认 'all'：catch 形参未使用即告警。
        // 工具链升级期曾临时置为 'none' 以免一次性引入 76 条 warning，该缺口已由
        // DR-22 单独收敛（未用的 catch 形参改为可选 catch {} 绑定）。
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
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
  // ------------------------------------------------------------------
  // e2e/ 覆盖面（2026-10-23，R-21）
  // 背景：lint / format:check 两个 glob 原先只写 src/**，而 CI 的 frontend-quality
  // 跑的正是 npm run lint —— e2e 的 29 个文件（24 ts + 3 cjs + 2 mjs）因此零覆盖，
  // 视觉与引导两批新增的用例从未被任何 lint 门看过。LEDGER 的 P0a-GATECOV-01
  // 已把"e2e eslint 净增 0"收回为空转门读数。
  // 顺序纪律：先让 e2e 在现有规则下清零，再把 package.json 的 glob 扩到 e2e/。
  // 规则强度与 src 保持一致，不新增、不放宽。
  {
    files: ['e2e/**/*.ts'],
    languageOptions: {
      parser: tseslint.parser,
      ecmaVersion: 2022,
      sourceType: 'module',
    },
    plugins: { '@typescript-eslint': tseslint.plugin },
    rules: {
      // TS 自身做未定义符号检查；page.evaluate 回调里还会引用浏览器全局。
      'no-undef': 'off',
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
      'no-empty': 'error',
      // e2e 的 console 是取证输出（逐步 activeElement / 读数 / 探针日志），
      // 不是产品代码里的调试残留；src 侧仍保持 error/warn 白名单。
      'no-console': 'off',
    },
  },
  {
    files: ['e2e/**/*.cjs'],
    languageOptions: { ecmaVersion: 2022, sourceType: 'commonjs', globals: globals.node },
  },
  {
    files: ['e2e/**/*.mjs'],
    // 这些 mjs 是 Playwright 驱动器：文件本身跑在 Node，但 page.evaluate 回调跑在浏览器，
    // 因此两套全局都要认（否则 document / getComputedStyle 会被判 no-undef）。
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.node, ...globals.browser },
    },
    rules: { 'no-console': 'off' },
  },
  prettier
)
