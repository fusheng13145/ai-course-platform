# 前端硬编码样式计数报告

> 由 `node frontend/scripts/count-hardcoded-style.cjs --write` 生成于 2026-10-27 15:27 UTC。
> 判据本体见 `docs/前端待办项.md` P5-04；基线（2026-10-19 实测，`.vue` 内行数）：
> `border-radius:` 313／6 位十六进制色 175／`rgba(` 110／`box-shadow:` 73／含渐变的文件 22。
> 该基线当年没有留命令，口径按最宽的解释落地：**命中行数**＝该行至少一次命中（含 `var()` 写法），
> **出现次数**＝逐次累计，**其中字面量**＝按 `;` 切段后段内不含 `var(` 的命中次数。
> 三列同表给出，判「硬编码有没有降」看第三列；前两列只用于和历史读数对齐。
> 「处数」＝radius／角位 radius／shadow／hex6／rgba／hsl 六类**字面量**次数合计，不含 gradient（渐变单列）。
> 对某一历史版本重跑同口径：`git archive <commit> frontend | tar -x -C <tmp>` 后加 `--root=<tmp>/frontend`。

### `.vue`（289 个文件）

| 类别 | 命中行数 | 出现次数 | 其中字面量 |
| --- | --- | --- | --- |
| border-radius: | 319 | 319 | 150 |
| border-*-radius:（角位） | 4 | 4 | 4 |
| box-shadow: | 73 | 73 | 19 |
| 6 位十六进制色 | 106 | 181 | 171 |
| rgba( | 104 | 104 | 84 |
| hsl( | 0 | 0 | 0 |
| gradient( | 83 | 83 | 71 |
| 含渐变的文件数 | 22 | — | — |

**Top 10（按 radius／角位 radius／shadow／hex6／rgba／hsl 六类字面量处数合计）**

| 文件 | 处数 |
| --- | --- |
| `src/layouts/components/AppSidebar.vue` | 27 |
| `src/views/teacher/components/TcCourseCard.vue` | 24 |
| `src/views/teacher/Dashboard.vue` | 16 |
| `src/views/auth/Login.vue` | 15 |
| `src/layouts/components/topbar/AtbUserMenu.vue` | 14 |
| `src/views/auth/Register.vue` | 14 |
| `src/views/student/components/SdCourseGrid.vue` | 14 |
| `src/components/charts/BarChart.vue` | 13 |
| `src/views/student/Courses.vue` | 13 |
| `src/views/admin/user/components/UmGroupTable.vue` | 12 |


### `src/styles/**`（3 个文件）

| 类别 | 命中行数 | 出现次数 | 其中字面量 |
| --- | --- | --- | --- |
| border-radius: | 56 | 56 | 14 |
| border-*-radius:（角位） | 0 | 0 | 0 |
| box-shadow: | 39 | 39 | 6 |
| 6 位十六进制色 | 74 | 79 | 73 |
| rgba( | 59 | 65 | 65 |
| hsl( | 0 | 0 | 0 |
| gradient( | 21 | 21 | 15 |
| 含渐变的文件数 | 3 | — | — |

**Top 10（按 radius／角位 radius／shadow／hex6／rgba／hsl 六类字面量处数合计）**

| 文件 | 处数 |
| --- | --- |
| `src/styles/theme.css` | 99 |
| `src/styles/design-system.css` | 40 |
| `src/styles/index.scss` | 19 |


### 令牌消费点（定义面＝`src/styles/**`，消费面＝整个 `frontend`）

| 汇总 | 数 |
| --- | --- |
| 定义到的自定义属性总数 | 176 |
| var() 消费为 0 的属性（除 `--el-*`） | 11 |
| 其中 `--ac-*` 族 | 0 |
| `--el-*` 消费为 0（EP 内部读，不列） | 49 |

**`--ac-*` 别名族逐枚消费数**（P5-05 判「别名族收敛」与 P6-06 判「token 有没有消费点」都引这张表）

| 属性 | 定义处 | var() 消费 | 样式目录外提及 |
| --- | --- | --- | --- |
| `--ac-bg` | 1 | 42 | 173 |
| `--ac-bg-base` | 1 | 3 | 3 |
| `--ac-bg-elevated` | 1 | 109 | 103 |
| `--ac-bg-hover` | 1 | 24 | 21 |
| `--ac-bg-subtle` | 1 | 9 | 9 |
| `--ac-border` | 1 | 195 | 190 |
| `--ac-border-strong` | 1 | 2 | 2 |
| `--ac-danger` | 1 | 33 | 38 |
| `--ac-danger-bg` | 1 | 7 | 6 |
| `--ac-info` | 1 | 2 | 4 |
| `--ac-info-bg` | 1 | 2 | 2 |
| `--ac-link` | 1 | 2 | 4 |
| `--ac-link-hover` | 1 | 2 | 2 |
| `--ac-on-primary` | 1 | 2 | 2 |
| `--ac-primary` | 1 | 111 | 144 |
| `--ac-primary-bg` | 1 | 25 | 23 |
| `--ac-primary-dark` | 1 | 2 | 1 |
| `--ac-primary-light` | 1 | 15 | 12 |
| `--ac-radius-lg` | 1 | 46 | 43 |
| `--ac-radius-md` | 1 | 92 | 84 |
| `--ac-radius-sm` | 1 | 26 | 27 |
| `--ac-radius-xl` | 1 | 9 | 5 |
| `--ac-shadow-lg` | 1 | 5 | 3 |
| `--ac-shadow-md` | 1 | 8 | 8 |
| `--ac-shadow-sm` | 1 | 22 | 22 |
| `--ac-shadow-xl` | 1 | 2 | 2 |
| `--ac-spacing-lg` | 1 | 79 | 78 |
| `--ac-spacing-md` | 1 | 193 | 190 |
| `--ac-spacing-sm` | 1 | 59 | 55 |
| `--ac-spacing-xl` | 1 | 10 | 10 |
| `--ac-spacing-xs` | 1 | 8 | 8 |
| `--ac-spacing-xxl` | 1 | 1 | 1 |
| `--ac-success` | 1 | 31 | 36 |
| `--ac-success-bg` | 1 | 7 | 6 |
| `--ac-text-muted` | 1 | 181 | 177 |
| `--ac-text-primary` | 1 | 176 | 176 |
| `--ac-text-regular` | 1 | 1 | 1 |
| `--ac-text-secondary` | 1 | 122 | 121 |
| `--ac-text-tertiary` | 1 | 14 | 14 |
| `--ac-transition-fast` | 1 | 29 | 27 |
| `--ac-transition-normal` | 1 | 13 | 13 |
| `--ac-warning` | 1 | 27 | 35 |
| `--ac-warning-bg` | 1 | 10 | 9 |

**零消费清单**（「样式目录外提及」＝视图／脚本／e2e 里的引用，用来抓"删了定义但引用还在"）

| 属性 | 定义处 | var() 消费 | 样式目录外提及 |
| --- | --- | --- | --- |
| `--accent-glow` | 1 | 0 | 0 |
| `--bg-deep` | 2 | 0 | 0 |
| `--danger` | 1 | 0 | 3 |
| `--ease-spring` | 1 | 0 | 0 |
| `--glow-brand` | 1 | 0 | 0 |
| `--primary` | 3 | 0 | 1 |
| `--scrim` | 2 | 0 | 0 |
| `--success` | 1 | 0 | 0 |
| `--t-slow` | 1 | 0 | 0 |
| `--t-xslow` | 1 | 0 | 0 |
| `--z-sticky` | 1 | 0 | 0 |
