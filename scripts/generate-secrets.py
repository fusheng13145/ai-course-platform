#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 ai-course-platform 所需强随机密钥（本地置产 / 灾备轮换用）。

特性：
- 零第三方依赖，仅用标准库 secrets。
- 默认仅打印到 stdout（不落盘），便于复制粘贴到密钥保管库。
- --write 将覆盖写入仓库根目录 .env 中对应键；.env 已被 .gitignore 忽略，切勿提交。
- 不生成第三方凭据（DeepSeek API Key / 邮件 SMTP 授权码），请到对应平台获取后手动填入。

用法：
  python scripts/generate-secrets.py            # 仅打印
  python scripts/generate-secrets.py --write    # 写入/更新 .env
"""
import argparse
import pathlib
import re
import secrets

REPO = pathlib.Path(__file__).resolve().parents[1]
ENV = REPO / ".env"

# 键 -> 建议随机字节数（secrets.token_urlsafe 输出约 4/3 倍字符）
SPEC = {
    "JWT_SECRET": 48,                  # HS512 建议 >= 32 字节；48 字节 url-safe 冗余足够
    "MYSQL_ROOT_PASSWORD": 18,         # 24 字符左右
    "REDIS_PASSWORD": 18,
    "APP_ADMIN_INITIAL_PASSWORD": 16,  # 满足 8-32 位含大小写+数字+特殊字符
    "APP_ACTUATOR_BASIC_PASSWORD": 16,
    "GRAFANA_ADMIN_PASSWORD": 16,
}


def gen(n_bytes: int) -> str:
    return secrets.token_urlsafe(n_bytes)


def main() -> None:
    ap = argparse.ArgumentParser(description="生成 ai-course-platform 强随机密钥")
    ap.add_argument("--write", action="store_true",
                    help="写入/更新仓库 .env（仅本地，已被 .gitignore 忽略）")
    args = ap.parse_args()

    vals = {k: gen(n) for k, n in SPEC.items()}

    if args.write:
        if not ENV.exists():
            print("!! .env 不存在，请先执行：cp .env.example .env")
            return
        text = ENV.read_text(encoding="utf-8")
        for k, v in vals.items():
            text = re.sub(rf"(?m)^{k}=.*", f"{k}={v}", text)
        ENV.write_text(text, encoding="utf-8")
        print(f"已更新 {ENV} 中的 {len(vals)} 个密钥。重启后端与相关服务以生效。\n")

    print("==== 强密钥（请妥善离线保管，仅用于本地 .env）====")
    for k, v in vals.items():
        print(f"{k:<28} (len={len(v)}): {v[:8]}…{v[-4:]}")


if __name__ == "__main__":
    main()
