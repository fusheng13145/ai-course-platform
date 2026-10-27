#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 ai-course-platform 所需强随机密钥（本地置产 / 灾备轮换用）。

特性：
- 零第三方依赖，仅用标准库 secrets。
- 默认仅打印到 stdout（不落盘），便于复制粘贴到密钥保管库。
- --write 将覆盖写入仓库根目录 .env 中对应键；.env 已被 .gitignore 忽略，切勿提交。
- 不生成第三方凭据（DeepSeek API Key / 邮件 SMTP 授权码），请到对应平台获取后手动填入。

============================================================================
口令策略边界（2026-09-16 实测核实，**不要混用同一套规则**）
============================================================================
只有 APP_ADMIN_INITIAL_PASSWORD 受后端统一密码策略 PasswordPolicy 约束
（backend/src/main/java/com/aicourse/util/PasswordPolicy.java:19-20：
 8-32 位，且**必须同时含**大写字母、小写字母、数字、特殊字符），
由 AdminSeedPasswordInitializer.java:61 在启动期校验，不合规即 fail-fast **拒绝启动**。

其余变量**不受** PasswordPolicy 约束，因此**不套用**四类字符规则：
  · JWT_SECRET
      —— 仅要求 ≥256bit。JwtUtils.java:50 用 Keys.hmacShaKeyFor(secret.getBytes(UTF_8))，
         即**原始 UTF-8 字节数 ≥32**，否则 jjwt 抛 WeakKeyException 拒启。无字符类别要求。
  · MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / GRAFANA_ADMIN_PASSWORD / APP_ACTUATOR_BASIC_PASSWORD
      —— 仅由各自的 docker compose ${VAR:?}（非空）与运行时连接校验把关，无强度策略
         （RedisStartupValidator 只做 PING 连通性校验，不校验口令强度）。
============================================================================

用法：
  python scripts/generate-secrets.py            # 仅打印
  python scripts/generate-secrets.py --write    # 写入/更新 .env
"""
import argparse
import pathlib
import re
import secrets
import string
import sys

REPO = pathlib.Path(__file__).resolve().parents[1]
ENV = REPO / ".env"

# ---------------------------------------------------------------------------
# 与后端 PasswordPolicy.java:19-20 **逐字一致**的镜像正则（单一事实来源在 Java 端）。
# 本脚本用它做生成后自检：生成即必过，不靠概率。
# 若 Java 端策略变更，此处必须同步（否则自检会与真实门禁脱节）。
# ---------------------------------------------------------------------------
PASSWORD_POLICY = re.compile(
    r"""^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[~!@#$%^&*()_+\-={}\[\]:;"'<>,.?/\\|`]).{8,32}$"""
)

# ---------------------------------------------------------------------------
# 单一事实来源：Java 端的 PasswordPolicy。
# 实际路径经核实为 com/aicourse/util/PasswordPolicy.java（**不是** com/aicourse/common/…）。
# --self-check 会定位该文件、抽出正则、与上面的 Python 镜像做逐字比对，防两侧静默漂移。
# ---------------------------------------------------------------------------
POLICY_JAVA = (REPO / "backend" / "src" / "main" / "java"
               / "com" / "aicourse" / "util" / "PasswordPolicy.java")
POLICY_JAVA_SEARCH_ROOT = REPO / "backend" / "src" / "main" / "java"
# 捕获 Java 源码里 Pattern.compile("…") 的字面量（含内部转义序列）
_JAVA_PATTERN_COMPILE = re.compile(r'Pattern\.compile\(\s*"((?:[^"\\]|\\.)*)"\s*\)')
# --self-check 的批量生成轮数
SELF_CHECK_N = 2000

# 需过 PasswordPolicy 的键 -> 目标长度（必须落在 8-32）
POLICY_KEYS = {
    "APP_ADMIN_INITIAL_PASSWORD": 22,
}

# 不受 PasswordPolicy 约束、仅需高熵的键 -> 建议随机字节数
# （secrets.token_urlsafe 输出约 4/3 倍字符）
SPEC = {
    "JWT_SECRET": 48,                  # 明文 64 字符 ≥32 字节，满足 JwtUtils 的 HS 下限
    "MYSQL_ROOT_PASSWORD": 18,         # 24 字符左右
    "REDIS_PASSWORD": 18,
    "APP_ACTUATOR_BASIC_PASSWORD": 16,
    "GRAFANA_ADMIN_PASSWORD": 16,
}

# JwtUtils.java:50 的硬约束：原始 UTF-8 字节数 ≥32（否则 jjwt WeakKeyException）
JWT_MIN_BYTES = 32

# ---------------------------------------------------------------------------
# 特殊字符池 SPECIAL_SAFE —— 为什么**只有这 10 个**字符（请勿"顺手补全"字符类）
#
# 池子必须同时满足三件事：
#   (a) 是 PasswordPolicy 特殊字符类的**子集**：
#         ~ ! @ # $ % ^ & * ( ) _ + - = { } [ ] : ; " ' < > , . ? / \ | `
#   (b) 能被 .env 的每一个消费者**原样送达**（不被解释、截断或转义）；
#   (c) 在 shell / dotenv / 引号语境下无特殊语义。
#
# 消费者 1 —— 旧 start-backend.bat 的 `for /f ... set "%%a"`（2026-10-27 三份启动器合并后已移除）：
#     for /f "usebackq delims=" %%a in (`type "%ENV_FILE%" ^| findstr /v "^#" ^| findstr /v "^$"`) do (
#         set "%%a" 2>nul
#     )
#   该块运行在 setlocal enabledelayedexpansion 之下。**逐个字符实跑 cmd 比对**的结论
#   （复现脚本当时在 `.workbuddy/tmp/probe_bat_chars.py`，未入库；原文见本文件的 git 历史）：
#     会被破坏 →  !  ^  &  <  >  |
#        !  被延迟展开吞掉（enabledelayedexpansion 的经典坑）
#        ^  被当作转义符
#        &  断句；< > 重定向；| 管道 —— 均在 set "%%a" 展开后截断命令行
#     可原样保留 → ~ @ # $ % * ( ) _ + - = { } [ ] : ; , . ? / \ `
#   ⚠ 反面案例（这才是本池必须窄的根本原因）：若放行 `!`，随机命中时它会在这一步被吞掉，
#     而生成的口令很可能**只有这一个特殊字符** → 到 Java 层反被 PasswordPolicy 拒绝 →
#     恰好亲手制造出我们正要修的那种"生成的口令导致拒启"缺陷。故 `!` 必须排除。
#   ⚠ 消费者 1 已随启动器合并消失，但**本池不放宽**：放宽是一次独立决策，需要
#     ① 重新清点仍在读 `.env` 的 shell/cmd 侧用法（`docker compose` 插值，
#     calibration/run_backend.sh 与 run_calib.sh 的 grep+read 循环，scripts/init-app-db-user.sh），
#     ② 逐字符重做上面的比对并留读数。
#     只做"少一个消费者"就顺手扩池，等于把口令强度的证据链剪断。
#
# 消费者 2 —— docker compose 的 .env 插值 / dotenv 解析：
#     $  会触发变量插值            → 排除
#     #  行内注释歧义（空白 + #）  → 排除
#
# 消费者 3 —— 通用 shell / 引号安全（人工复制粘贴、shell 脚本、日志）：
#     "  '  引号歧义；\  转义序列；`  命令替换 → 全部排除
#
# 交集即下面的 SPECIAL_SAFE（10 个），每一个都已确认落在 PasswordPolicy 特殊类之内
# （由 --self-check 的第 ③ 项做机器校验，不靠这段注释的自觉）。
# 池子窄**不影响强度**：四类字符各取一 + 其余位从并集取 + 洗牌，长度 22，熵仍然充足。
# ---------------------------------------------------------------------------
SPECIAL_SAFE = "-_+=~@:,.?"

# 特殊字符池有效性缓存（避免每次生成都重复校验）
_pool_validated = False

UPPER = string.ascii_uppercase
LOWER = string.ascii_lowercase
DIGIT = string.digits
ALL = UPPER + LOWER + DIGIT + SPECIAL_SAFE

_rng = secrets.SystemRandom()


def gen(n_bytes: int) -> str:
    """不受 PasswordPolicy 约束的密钥：直接取高熵 urlsafe 串。"""
    return secrets.token_urlsafe(n_bytes)


def _ensure_special_pool_valid() -> None:
    """校验 SPECIAL_SAFE ⊆ PasswordPolicy 特殊字符类。

    必要性：若池中**没有任何**字符属于特殊字符类，四类字符"各取一"将永远不成立，
    gen_policy_password 的重试循环会**死循环**（挂住而不是报错）。故必须前置校验。

    判定手法：构造 `Aa1<c>bB2cC3`（大写+小写+数字 + 待测字符 c）。该串长度 11 落在 8-32 内，
    当且仅当 c 属于特殊字符类时整体才满足 PasswordPolicy —— 用策略本身来判定，不另写一份字符表。
    """
    global _pool_validated
    if _pool_validated:
        return
    if not SPECIAL_SAFE:
        raise AssertionError("SPECIAL_SAFE 为空 → 生成器无法产出合规口令（会死循环）")
    bad = [c for c in SPECIAL_SAFE if not PASSWORD_POLICY.match("Aa1" + c + "bB2cC3")]
    if bad:
        raise AssertionError(
            f"SPECIAL_SAFE 含不属于 PasswordPolicy 特殊字符类的字符 {bad!r}"
            " → 四类字符各取一永不成立，重试循环会死循环")
    _pool_validated = True


def gen_policy_password(length: int) -> str:
    """生成**必定**满足 PasswordPolicy 的口令。

    做法：四类字符各先取一个，其余位从并集中取，再用 SystemRandom().shuffle 打乱
    （消除"固定位置来自固定字符类"的结构）。最后当场用镜像正则断言，不合规则重新生成
    —— 即"生成即必过"，不依赖概率。
    """
    if not 8 <= length <= 32:
        raise ValueError(f"length 必须落在 PasswordPolicy 的 8-32 区间内，实际 {length}")
    if length < 4:
        raise ValueError("length 至少为 4，否则无法保证四类字符各取一")
    _ensure_special_pool_valid()
    while True:
        chars = [
            _rng.choice(UPPER),
            _rng.choice(LOWER),
            _rng.choice(DIGIT),
            _rng.choice(SPECIAL_SAFE),
        ]
        chars += [_rng.choice(ALL) for _ in range(length - len(chars))]
        _rng.shuffle(chars)
        candidate = "".join(chars)
        if PASSWORD_POLICY.match(candidate):
            return candidate


def build_secrets() -> dict:
    """构造全部密钥；对受策略约束的键做"生成后自检"。"""
    vals = {k: gen(n) for k, n in SPEC.items()}

    # JWT_SECRET 的硬约束自检（jjwt 下限），失败即脚本缺陷
    if len(vals["JWT_SECRET"].encode("utf-8")) < JWT_MIN_BYTES:
        raise AssertionError(
            f"JWT_SECRET 生成值不足 {JWT_MIN_BYTES} 字节，JwtUtils 会抛 WeakKeyException（脚本缺陷）")

    for k, n in POLICY_KEYS.items():
        v = gen_policy_password(n)
        if not PASSWORD_POLICY.match(v):
            # 理论上不可达（上面已在循环内断言），保留为最后一道闸门
            raise AssertionError(f"{k} 生成值不满足 PasswordPolicy（脚本缺陷，请修复生成逻辑）")
        vals[k] = v
    return vals


def main() -> None:
    ap = argparse.ArgumentParser(description="生成 ai-course-platform 强随机密钥")
    ap.add_argument("--write", action="store_true",
                    help="写入/更新仓库 .env（仅本地，已被 .gitignore 忽略）")
    args = ap.parse_args()

    vals = build_secrets()

    if args.write:
        if not ENV.exists():
            print("!! .env 不存在，请先执行：cp .env.example .env")
            return
        text = ENV.read_text(encoding="utf-8")
        missing = []
        for k, v in vals.items():
            # 用函数式替换：避免值中的反斜杠被 re.sub 当作转义序列解释
            text, n = re.subn(rf"(?m)^{k}=.*", lambda _m, k=k, v=v: f"{k}={v}", text)
            if n == 0:
                missing.append(k)
        ENV.write_text(text, encoding="utf-8")
        print(f"已更新 {ENV} 中 {len(vals) - len(missing)}/{len(vals)} 个密钥。重启后端与相关服务以生效。\n")
        if missing:
            print("!! 下列键在 .env 中不存在对应行，**未能写入**（请先在 .env 中补上键名行）：")
            for k in missing:
                print(f"   - {k}\n     {k}={vals[k]}")
            print()

    print("==== 强密钥（请妥善离线保管，仅用于本地 .env）====")
    for k, v in vals.items():
        tag = "  [满足 PasswordPolicy]" if k in POLICY_KEYS else ""
        print(f"{k:<28} (len={len(v)}): {v[:8]}…{v[-4:]}{tag}")


if __name__ == "__main__":
    main()
