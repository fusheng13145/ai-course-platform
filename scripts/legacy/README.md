# scripts/legacy

历史/一次性脚本，仅保留可重放，不作为日常入口。

| 脚本 | 说明 |
| --- | --- |
| `generate-seed-data.py` | V5 种子生成器；复用根目录 `scripts/seed_data_templates.py`，输出 `backend/.../seed-demo/V5__rich_seed_data.sql` |
| `generate-cn-showcase-seed.py` | V6 计算机网络示范课生成器；输出 `backend/.../seed-demo/V6__computer_network_showcase.sql`。V6 已应用，禁止为改文案重生成，否则 Flyway 校验和失配 |
| `migrate-uploads.py` | 历史上传目录迁移；默认 dry-run，源 `backend/uploads`，目标 `data/aicourse-platform/storage`，备份 `data/backups/uploads-migration` |

约定：

- `seed_data_templates.py` 仍在 `scripts/` 根：`seed-demo-data.py` 正在使用，不能一起迁入本目录。
- 本目录脚本不进入日常启动、CI 主链或新数据初始化流程。
- 生成器里写入 SQL 的路径注释是迁移前历史文本，不随脚本移动回写，以免改动已应用的 Flyway 文件校验和。
- `test_migrate_uploads.py` 已移除：迁移已完成，脚本仅留档；其余启动器、审计与 CI 守卫测试保留。
