# 异地备份与 TLS 件方案骨架（A 路径 #12 / #7 / #8）

> **定位**：只给方案骨架与待决项，**不代表已实现、已部署、已验收**。
> 当前状态（2026-11-05）：
> - MySQL 本地定时备份已存在（`mysql-backup` 容器 + `mysql-backups` 卷）；
> - `BACKUP_REMOTE_SYNC` 在备份脚本里只是占位：镜像内没有 `rclone`，实际会打印
>   “rclone not installed”并跳过，**异地副本为 0**；
> - TLS 终止、证书、真实域名均未落地，A 路径 #7/#8 仍挂账。
> 本文不伪造任何演练读数；首次执行后请填 `docs/恢复演练记录模板.md`。

---

## 1. 异地备份：现状与缺口

| 项 | 现状 | 缺口 |
| --- | --- | --- |
| 本地备份 | `mysql-backup` 按计划 `mysqldump` 到 `mysql-backups` 卷 | 无 |
| 异地副本 | 脚本识别 `BACKUP_REMOTE_SYNC`，但镜像无 `rclone`，实际跳过 | **异地副本为 0** |
| 加密 | 无 | 备份包含用户/成绩/附件元数据，异地传输前应加密 |
| 保留策略 | `BACKUP_RETENTION_DAYS`（默认 14） | 异地保留/不可变策略未定 |
| 监控 | 备份容器日志 | 失败告警未接入（Prometheus/Alertmanager） |
| 恢复演练 | 本模板与脚本已备 | **未真实执行** |

## 2. 可选方案（按侵入性从低到高）

### 方案 A：主机侧 rclone + systemd timer（推荐先做）

- 不构建自定义备份镜像；在宿主机安装 `rclone`，定时把 `mysql-backups` 卷目录同步到对象存储。
- 优点：改动最小、可独立升级、失败可见；缺点：多一个主机依赖。
- 骨架步骤：
  1. `rclone config` 创建远端（S3/OSS/B2 均可），凭据放 `~/.config/rclone/rclone.conf`（600）；
  2. 先加密：`age` 或 `gpg` 对 `.sql.gz` 加密后再上传；
  3. `systemd` timer 每日执行 `rclone sync --immutable <vol-path> <remote>:<bucket>/aicourse/mysql/`；
  4. 失败时 `OnFailure=` 触发告警脚本（邮件/Webhook）；
  5. 每月做一次“从异地副本恢复到隔离库”，填 `docs/恢复演练记录模板.md`。

### 方案 B：自定义备份镜像内置 rclone

- 在 `backup/Dockerfile` 基础上装 `rclone` + `age`，由 `mysql-backup` 容器直接上传。
- 优点：备份与上传同一事务域；缺点：镜像需维护、凭据进容器环境、体积增大。
- 需新增 secret：`RCLONE_CONFIG`（base64）或 `RCLONE_REMOTE` + 访问键；不得写进仓库。

### 方案 C：对象存储 SDK 直传

- 备份脚本改用厂商 SDK（boto3/ossutil）。
- 优点：可控错误码与重试；缺点：绑定厂商、脚本复杂度最高。

**先决项**：目标存储、区域、加密密钥托管方式、保留天数、是否要求不可变（WORM）、
谁有权解密。这些未定之前，不应在 `.env` 里填 `BACKUP_REMOTE_SYNC` 制造“已启用”的假象。

## 3. TLS 终止与证书

### 3.1 拓扑

```
浏览器 --HTTPS--> 反向代理（nginx/Caddy） --HTTP(内网)--> frontend:80 / backend:8080
```

- 反向代理必须设置 `X-Forwarded-Proto: https`、`X-Forwarded-For`、`Host`；
  后端 `TRUST_PROXY_HEADER=true` 才认这些头。
- `REQUIRE_HTTPS=true` 与 `COOKIE_SECURE=true` 必须同时成立（见 `deploy/env.production.template`）。
- HSTS 建议：`Strict-Transport-Security: max-age=31536000; includeSubDomains`（确认全站 HTTPS 后再加 preload）。

### 3.2 证书来源（按备案状态分流）

| 场景 | 方案 | 备注 |
| --- | --- | --- |
| 已备案公网域名 | Let's Encrypt（HTTP-01 或 DNS-01） | 自动续期；80 端口需可达 |
| 未备案 / 仅内网 | 内部 CA 或企业证书 | 浏览器需信任链；对外不可宣称“公网 HTTPS 已验收” |
| 纯 IP 访问 | 不建议 | Cookie/Origin 白名单与证书都难满足 |

**当前项目**：未备案，公网域名不可用 → 只能做静态配置检查；运行期验收挂账。

### 3.3 验收口径

- 静态：`python scripts/check-https-ws-config.py --env-file .env --domain <域名>`；
- 运行期：加 `--base-url https://<域名>` 与 `--token <已登录 JWT>`，要求
  HTTPS 健康探针 200、白名单 Origin 的 WS 握手 101、非白名单 Origin 403；
- 没有真实域名/证书/JWT 时脚本返回 `PENDING`（退出码 2），**不得改判为 PASS**。

## 4. 待决项清单（上线前必须逐条有结论）

- [ ] 异地存储选型与账号归属
- [ ] 加密方式与密钥托管（谁持有、如何轮换、离职交接）
- [ ] 保留窗口与不可变要求
- [ ] 备份失败告警通道（邮件/IM/电话）
- [ ] 首次异地恢复演练日期与负责人
- [ ] 域名/备案状态与证书来源
- [ ] 反向代理选型（nginx/Caddy）与配置归属
- [ ] WS Origin 双向验收用的测试账号与 JWT 获取方式

> 结论未定时，A 路径 #7/#8/#12 只能保持“骨架/挂账”，不能标记完成。
