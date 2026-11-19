# scripts 索引

> 适用范围：2026-11-19。脚本的运行期数据根为项目 `data/aicourse-platform`，冷备在 `data/backups`。
> 盘点：`scripts/` 共 **46 个文件 / 0.80MB**（排除 `__pycache__`）。
> 约定：日常入口只认本页“日常入口”列出的命令；历史脚本保留可重放，不作为默认流程。

## 1. 日常入口

| 脚本 | 用途 | 备注 |
| --- | --- | --- |
| `start-all.py` | 全栈启动器判定层 | 由 `start.bat` / `start.sh` 薄壳调用；`all` / `backend` / `frontend` / `status` |
| `start-engines.py` | Python 引擎、Neo4j、Ollama 编排 | 由 `start-all.py` 复用，不单独作为入口 |
| `audit-stats.py` | 只读结构统计与密钥形状盘点 | 输出结构性读数，不打印凭据值 |
| `seed-demo-data.py` | 本地开发库补业务数据 | 默认 dry-run；`--apply` 才写；拒绝 prod/远程库 |
| `seed-demo-resources.py` | 补真实 DOCX 资源并写入 MinIO | 默认 dry-run；`--apply` 走教师上传接口，上传后做 SHA-256 校验 |

## 2. 数据、知识图谱与训练

| 脚本 | 用途 |
| --- | --- |
| `extract-kg.py` | IPA 两阶段知识点抽取入口 |
| `extract-relations.py` | IREM 五类关系构建（规则召回 → LLM 判类 → 可选写库） |
| `sync-kg-to-python.py` | 以 MySQL 知识点 ID 为权威，向 kg_service 对齐推送 |
| `seed-rag-corpus.py` | 将版本控制中的 `rag_corpus.json` 重放到 RAG 引擎 |
| `backfill_question_kp.py` | 历史题目知识点回填 |
| `fine_tune_dataset.py` | 讲义/题库 → LoRA 训练样本 |
| `rag_corpus.json` | RAG 语料数据文件，不是可执行脚本 |

## 3. 运维与部署

| 脚本 | 用途 |
| --- | --- |
| `ops/mysql-backup.sh` | Compose `mysql-backup` 服务的定时全量备份入口 |
| `restore-mysql.sh` | 隔离库恢复与关键表核对 |
| `init-app-db-user.sh` | 建库时创建 `aicourse_app` 最小权限账户 |
| `verify-uploads-sha256.sh` | 上传卷抽样/逐文件 SHA-256 校验 |
| `migrate-uploads.py` | 历史上传目录迁移；已完成，保留可重放 |
| `fix-volume-owner.sh` | 存量卷属主检查；默认只检查，`--apply` 才改 |
| `generate-secrets.py` | 密钥轮换生成；只在 `.env` 更新时使用，永不回显值 |
| `build_images.sh` | Compose 镜像构建入口；本阶段 Docker 搁置，仍供 CI 使用 |

## 4. 验收、审计与取证

| 脚本 | 用途 |
| --- | --- |
| `acceptance_check.py` | 换机/发布前的端口、健康端点与必需键检查 |
| `check-deploy-readiness.py` | 部署补件汇总自检入口 |
| `check-required-env.py` | Compose 必填键与自动生成范围检查 |
| `check-https-ws-config.py` | HTTPS / Cookie / WS Origin 静态验收 |
| `audit_orphan_endpoints.py` | Controller 端点 × 前端调用痕迹扫描 |
| `analyze_slow_sql.py` | MyBatis 慢 SQL 日志 Top-N 聚合 |
| `probe-llm-chain.py` | LLM 凭据与多条链路的真实取证 |
| `org_mapping_inventory.py` | 组织旧文本映射清单盘点（只读） |

## 5. E2E 与测试

| 路径 | 用途 |
| --- | --- |
| `run-backend-for-e2e.py` | E2E 常驻后端启动器；`functional` / `secure` 姿态不可混用 |
| `e2e/` | 全栈四件套与网络课程验收脚本 |
| `tests/` | scripts 侧 pytest；日常最小回归 `python -m pytest scripts/tests -q` |

## 6. 历史脚本（保留，不作日常入口）

| 脚本 | 说明 |
| --- | --- |
| `generate-seed-data.py` | V5 种子数据生成引擎；历史 Flyway 种子来源 |
| `seed_data_templates.py` | V5 种子数据课程语料模板 |
| `generate-cn-showcase-seed.py` | V6 计算机网络示范课种子生成器 |

## 7. 新脚本放置约定

- 跨域入口或一次性审计放 `scripts/` 顶层，并在本页登记。
- 长驻/部署运维放 `scripts/ops/`；浏览器与全栈验收放 `scripts/e2e/`；pytest 放 `scripts/tests/`。
- 数字类结论必须带时点、口径与可重放命令；历史脚本不得被描述为当前主流程。
