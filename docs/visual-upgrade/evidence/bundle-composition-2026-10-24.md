# 前端包体构成分解与六项读数（FE-08 / R-15"先量"半步）

> **时点**：2026-10-24 ｜ **基线**：`main@2ab84b1` 工作树实测（本批 P0 五提交之后，与 FE-01…03 改动无体积语义）
> **性质**：零代码改动的纯测量件（路线图 R-15 判据"构成分解 + 六项读数逐条记录"的"先量"半步；"后治"见方案 FE-11，须拍板重启）
> **复现命令**：`cd frontend && npm run build && npm run size:gate`（读数以 `size:gate` 磁盘字节口径为准）

## 一、口径说明（一个必须钉住的单位陷阱）

- `size:gate`（`scripts/check-bundle-size.cjs`）按**磁盘字节 / 1024** 计（二进制 KiB）；
- `vite build` 控制台打印按 **1000 字节**计（十进制 kB）。
- 同一个 `echarts-*.js`：vite 打印 `824.37 kB`、gate 读 `805.06 KiB`——824.37×1000/1024≈805.05，两口径自洽。**引用读数必须写明口径**，本文一律 KiB。

## 二、六项读数（size:gate 口径，2026-10-24 实测）

| 项 | 实测 KiB | 上限 KiB | 余量 | 2026-10-13 门禁首跑基线 KiB | 漂移 |
| --- | --- | --- | --- | --- | --- |
| echarts（含 zrender） | 805.06 | 900 | +94.94 | 805.06 | **0** |
| element-plus（**含 icons 全量包**） | 735.14 | 830 | +94.86 | 739.31 | −4.17 |
| xlsx | 323.28 | 380 | +56.72 | 323.28 | **0** |
| 其余功能块合计 | 1256.07 | 1300 | +43.93 | 1145.55 | **+110.52** |
| JS 合计（142 块） | 3119.54 | 3400 | +280.46 | 3013.20 | **+106.34** |
| element-plus CSS | 381.60 | 420 | +38.40 | 382 | −0.40 |

（CSS 合计 93 块共 632.04 KiB，其中 EP 一块占 381.60。）

**漂移定性**：三大块（echarts / xlsx）与 EP 基本零变化；JS 合计 +106.34 KiB **几乎全部落在"其余功能块"**（路由懒加载块 + axios +6 等）——即漂移来源是**业务路由代码增长**，不是第三方依赖失控。其余预算余量已收窄到 +43.93 KiB（3.5%），按现有增速约可支撑一个中等批次的功能增长，是 FE-11 治理的量化动因。

## 三、构成分解（142 个 JS 块按 manualChunks 分组）

| 分组 | KiB | 占 JS 合比 | 说明 |
| --- | --- | --- | --- |
| echarts（含 zrender） | 805.06 | 25.8% | 按需注册（8 图表 + 7 组件 + Canvas），独立块随路由懒加载 |
| element-plus | 735.14 | 23.6% | **`id.includes('element-plus')` 同时命中 `@element-plus/icons-vue` ⇒ main.ts:57-59 全局注册的约 300 个图标全部落在本块** |
| xlsx | 323.28 | 10.4% | 被 3 个成绩族 composable 静态 `import *`（useGrades / useTeacherGrades / useCourseGradesTab），随宿主路由整块加载 |
| 路由懒加载块（含 index 入口 49.84） | 1145.78 | 36.7% | 107 个视图块；最大单块 CourseDetail 113.56（其下聚合全部课程 Tab） |
| axios | 48.79 | 1.6% | |
| vue-vendor（vue/router/pinia） | 29.98 | 1.0% | |
| dompurify | 27.43 | 0.9% | |
| vendor（其余 node_modules） | 4.08 | 0.1% | |
| **html2canvas** | **0（块不存在）** | 0 | **`package.json:34` 声明、全仓 src/e2e 零引用 ⇒ 死依赖**（vite.config 的 html2canvas 分组分支为实际不命中的配置遗留） |

Top 12 JS 块：echarts 805.06 ／ element-plus 735.14 ／ xlsx 323.28 ／ CourseDetail 113.56 ／ index 49.84 ／ axios 48.79 ／ AppShell 34.20 ／ vue-vendor 29.98 ／ dompurify 27.43 ／ QuestionManagement 26.44 ／ Exams 24.00 ／ KnowledgeGraph 22.55。第 4 名之后全部 ≤50 KiB——**长尾健康，问题集中在三大块**。

## 四、FE-11"后治"候选清单（读数→动因，本轮不做、待拍板）

1. **icons 按需**：EP 块 735.14 KiB 内含全量 icons；治理须先核 `menus.ts`／`userMenuItems.js` 的**字符串名动态组件解析面**（`resolveComponent`/`<component :is>`），树摇断供会白屏而非报错小。
2. **xlsx 动态 import**：323.28 KiB 只在"导出"动作时需要，3 个 composable 的调用点改 `await import('xlsx')` 可把它移出页面首载。
3. **卸载 html2canvas 死依赖**：零引用零成本（不在产物里），但占 package.json / node_modules / 锁文件面，manualChunks 分支应随删（同源死配置清理先例：批次 4 的 exceljs/pdfjs-dist）。
4. EP 样式全量引入（main.ts:4）系莫兰迪主题覆盖的**有意决策**（方案 §5 N-6），不动。

## 五、边界声明

- 本件只测量不治理；`size:gate` 上限**未动**（棘轮只紧不松，收紧属 FE-11 治理批的评审动作）。
- 读数为单次构建实测，无并行构建方差控制；复现命令见文首。
- `@element-plus/icons-vue` 落点的证据链：`vite.config.ts:105`（`id.includes('element-plus')`）+ `node_modules/@element-plus/icons-vue` 路径含该子串；未做逐字节 dump 反查（如需可于 FE-11 治理批补 `rollup` 模块清单取证）。
