#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""附录 B 统计口径审计脚本（可复跑）。

用途：把《项目手册》附录 B「统计口径权威总表」的核心数据点从源码实测出来，
避免手册数字与仓库实际结构漂移（历史上多次出现两套数字）。

用法：
    python scripts/audit-stats.py            # 打印审计表
    python scripts/audit-stats.py --json     # 输出 JSON（供脚本消费）

口径原则：
  - 每一行都注明口径（文件计数 / 正则计数 / 严格 CREATE TABLE 行计数 / CREATE TABLE 表名去重），
    与手册口径列一一对应；当前库表数看「全迁移去重」行，
    「index.sql 落后迁移的表数」为 0 表示全量初始化脚本已与迁移对齐（2026-10-17 DR-61 回补后）；
    V1 仍为 48 张（Flyway 校验和冻结、不可回改），不能拿它当当前库表数；
  - 只做静态计数，不做「运行期识别」，故与运行期数字（如实际 @RestController 数）可能略有差异；
  - 计数路径全部相对仓库根，脚本可在任意 CWD 下运行。
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BACK = os.path.join(ROOT, "backend")
FRONT = os.path.join(ROOT, "frontend")


def walk_files(path: str, pred=None) -> list[str]:
    out = []
    for d, _, fs in os.walk(path):
        for f in fs:
            if pred is None or pred(f):
                out.append(os.path.join(d, f))
    return out


def read(path: str) -> str:
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def strip_comment_lines(text: str) -> str:
    """按行剔注释：整行注释丢弃；注释收尾符 `*/` 后若还挂着代码（`*/  @PostMapping(...)`），
    保留该代码段——否则行上的注解会被静默漏计。本函数是全部注解计数的唯一口径入口。"""
    kept = []
    for line in text.splitlines():
        s = line.strip()
        if not s.startswith(("//", "/*", "*")):
            kept.append(line)
            continue
        if s.startswith("//"):
            continue
        tail = s.split("*/", 1)[1] if "*/" in s else ""
        if tail.strip():
            kept.append(tail.strip())
    return "\n".join(kept)


def create_table_names(text: str) -> set:
    """行首 CREATE TABLE 的表名集合；跳过 -- 注释行，避免脚注里的「CREATE TABLE」被误计。"""
    names = set()
    for line in text.splitlines():
        if line.lstrip().startswith("--"):
            continue
        m = re.match(r"\s*CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?", line, re.I)
        if m:
            names.add(m.group(1).lower())
    return names


def count_annotations(base: str, ann: str) -> int:
    total = 0
    for p in walk_files(base, lambda f: f.endswith(".java")):
        total += len(re.findall(re.escape(ann), strip_comment_lines(read(p))))
    return total


def audit() -> dict:
    d: dict = {}

    # ---------------- 后端结构 ----------------
    d["backend_main_java"] = len(walk_files(os.path.join(BACK, "src/main/java"), lambda f: f.endswith(".java")))
    d["backend_test_java"] = len(walk_files(os.path.join(BACK, "src/test/java"), lambda f: f.endswith(".java")))
    d["controller_files"] = len(walk_files(os.path.join(BACK, "src/main/java/com/aicourse/controller"), lambda f: f.endswith(".java")))
    svc = os.path.join(BACK, "src/main/java/com/aicourse/service")
    d["service_top_java"] = len([f for f in os.listdir(svc) if f.endswith(".java")])
    d["service_component_java"] = len(walk_files(os.path.join(svc, "component"), lambda f: f.endswith(".java")))
    d["service_component_domains"] = len([x for x in os.listdir(os.path.join(svc, "component")) if os.path.isdir(os.path.join(svc, "component", x))])
    d["service_impl_java"] = len(walk_files(os.path.join(svc, "impl"), lambda f: f.endswith(".java")))
    d["mapper_java"] = len(walk_files(os.path.join(BACK, "src/main/java/com/aicourse/mapper"), lambda f: f.endswith(".java")))
    ent_dir = os.path.join(BACK, "src/main/java/com/aicourse/entity")
    ent_files = walk_files(ent_dir, lambda f: f.endswith(".java"))
    d["entity_java"] = len(ent_files)
    d["entity_java_excl_enum"] = len([p for p in ent_files
                                      if not re.search(r"\b(enum|interface)\b", read(p))])
    d["dto_star_dto_java"] = len(walk_files(os.path.join(BACK, "src/main/java/com/aicourse/dto"), lambda f: f.endswith("DTO.java")))
    d["vo_star_vo_java"] = len(walk_files(os.path.join(BACK, "src/main/java/com/aicourse/vo"), lambda f: f.endswith("VO.java")))

    # ---------------- 数据表（严格 CREATE TABLE 行计数） ----------------
    index_sql = read(os.path.join(BACK, "src/main/resources/index.sql"))
    v1_sql = read(os.path.join(BACK, "src/main/resources/db/migration/V1__init_schema.sql"))
    d["tables_index_sql"] = len(re.findall(r"(?im)^\s*CREATE TABLE", index_sql))
    d["tables_v1_sql"] = len(re.findall(r"(?im)^\s*CREATE TABLE", v1_sql))
    # 迁移脚本按目录分层（DR-18①：db/migration 进生产，db/seed-demo 演示种子生产排除）
    mig_files = walk_files(os.path.join(BACK, "src/main/resources/db/migration"), lambda f: f.endswith(".sql"))
    d["flyway_migrations"] = len(mig_files)
    d["flyway_demo_seeds"] = len(walk_files(os.path.join(BACK, "src/main/resources/db/seed-demo"), lambda f: f.endswith(".sql")))
    # 全迁移去重表名：V8+ 新表不在 index.sql/V1 内，仅比 index.sql↔V1 会把「48」当成当前库表数
    mig_tables = set()
    for p in mig_files:
        mig_tables |= create_table_names(read(p))
    d["tables_migrations_distinct"] = len(mig_tables)
    index_lag = sorted(mig_tables - create_table_names(index_sql))
    d["tables_index_sql_lag"] = len(index_lag)
    d["tables_index_sql_missing_list"] = ",".join(index_lag) or "-"

    # ---------------- 注解计数 ----------------
    base = os.path.join(BACK, "src/main/java")
    d["op_operation"] = count_annotations(base, "@Operation")
    d["op_valid"] = count_annotations(base, "@Valid")
    d["op_transactional"] = count_annotations(base, "@Transactional")
    d["op_preauthorize"] = count_annotations(base, "@PreAuthorize")
    d["op_tag"] = count_annotations(base, "@Tag")
    d["op_exception_handler"] = count_annotations(base, "@ExceptionHandler")
    d["op_deprecated"] = count_annotations(base, "@Deprecated")

    # ---------------- 方法级 HTTP 操作计数 ----------------
    # 口径：controller/ 目录内的 @GetMapping/@PostMapping/... 出现次数，经 strip_comment_lines
    # （唯一剔注释入口，含「`*/` 后挂代码」修正——468→469 的漏计正来自此）。
    # 这是手册 §15 / B.1「方法级 HTTP 操作」唯一权威来源，不得再用行首锚定的手 grep 复算。
    ctrl = os.path.join(base, "com/aicourse/controller")
    verbs = {"GET": "@GetMapping", "POST": "@PostMapping", "PUT": "@PutMapping",
             "DELETE": "@DeleteMapping", "PATCH": "@PatchMapping"}
    counts = {v: count_annotations(ctrl, a) for v, a in verbs.items()}
    for verb, n in counts.items():
        d["http_" + verb.lower()] = n
    d["http_ops_total"] = sum(counts.values())
    d["http_class_level_mapping"] = count_annotations(ctrl, "@RequestMapping")

    # ---------------- 前端结构 ----------------
    d["frontend_vue"] = len(walk_files(os.path.join(FRONT, "src"), lambda f: f.endswith(".vue")))
    d["frontend_test_ts"] = len(walk_files(os.path.join(FRONT, "src"), lambda f: f.endswith(".test.ts")))
    d["frontend_test_js"] = len(walk_files(os.path.join(FRONT, "src"), lambda f: f.endswith(".test.js")))
    d["frontend_api_modules"] = len(walk_files(os.path.join(FRONT, "src/api"), lambda f: f.endswith(".ts") and not f.endswith(".test.ts")))
    d["frontend_routes"] = len(re.findall(r"\bpath:\s*[\'\"`]", read(os.path.join(FRONT, "src/router/index.ts"))))
    d["frontend_e2e_specs"] = len(glob.glob(os.path.join(FRONT, "e2e/**/*.spec.ts"), recursive=True))

    # ---------------- CI / 监控 ----------------
    d["ci_workflow_files"] = len(glob.glob(os.path.join(ROOT, ".github/workflows/*.yml")))
    ci = read(os.path.join(ROOT, ".github/workflows/ci.yml"))
    # 作业键口径：jobs: 之后的顶层缩进 2 空格的键
    jobs_block = ci.split("\njobs:", 1)[1] if "\njobs:" in ci else ""
    d["ci_jobs"] = len(re.findall(r"(?m)^  ([A-Za-z][\w-]*):\s*$", jobs_block))

    alerts = read(os.path.join(ROOT, "monitoring/prometheus/alert_rules.yml"))
    d["alert_groups"] = len(re.findall(r"(?m)^\s*-\s*name:", alerts))
    d["alert_rules"] = len(re.findall(r"(?m)^\s*-\s*alert:", alerts))

    # Grafana 面板（含 row 内嵌 panels）
    dash = os.path.join(ROOT, "monitoring/grafana/provisioning/dashboards/aicourse-backend.json")
    if os.path.exists(dash):
        g = json.loads(read(dash))
        total = 0
        rows = 0
        for p in g.get("panels", []):
            if p.get("type") == "row":
                rows += 1
                total += len(p.get("panels", []))
            else:
                total += 1
        d["grafana_panels"] = total
        d["grafana_rows"] = rows

    # ---------------- 类型门禁基线 ----------------
    base_file = os.path.join(FRONT, "scripts/typecheck-baseline.json")
    if os.path.exists(base_file):
        bl = json.loads(read(base_file))
        d["typecheck_baseline_errors"] = bl.get("errors")
        d["typecheck_baseline_files"] = bl.get("files")

    # ---------------- 环境变量键位核对（只取键名，绝不读值） ----------------
    # 口径：未注释的 `KEY=` 行，按键名去重；缺失 = example 有而本机 .env 没有。
    # 手册 §24.1 的「N 个键」与 §29 的 .env 补齐项都以本行为准，不再用 ad-hoc grep 计数。
    def env_keys(name: str) -> set:
        p = os.path.join(ROOT, name)
        if not os.path.exists(p):
            return set()
        return {m.group(1) for m in re.finditer(r"(?m)^([A-Z0-9_]+)=", read(p))}

    example_keys = env_keys(".env.example")
    local_keys = env_keys(".env")
    d["env_example_keys"] = len(example_keys)
    d["env_local_keys"] = len(local_keys)
    d["env_local_missing_count"] = len(example_keys - local_keys)
    d["env_local_missing_keys"] = ",".join(sorted(example_keys - local_keys)) or "无"
    d["env_local_extra_keys"] = ",".join(sorted(local_keys - example_keys)) or "无"
    d["env_local_key_names"] = ",".join(sorted(local_keys)) or "无"

    # ---------------- .env 口令强度自检（手册 §29 项 1/2/5/9 的机器判据） -------------
    # 刻意**只输出派生判定**（长度、是否等于已知占位串、留空/未设），绝不在 stdout 或返回值里
    # 出现值本身或其任何前缀——密钥不落终端是本项目既有纪律（start-all.py 的 backend_env 同源）。
    # 长度/格式判据**一律取自仓库既有文档或代码**，本文件不自创标准；无据可引的键只判「非空 + 非占位串」：
    #   MYSQL_ROOT_PASSWORD  ≥16        ← .env.example:23「要求：至少 16 位」
    #   JWT_SECRET            ≥32 字节  ← JwtUtils 的 Keys.hmacShaKeyFor 下限（不足则 jjwt 拒启）
    #   APP_ADMIN_INITIAL_PASSWORD 8–32 ← PasswordPolicy.java（由 AdminSeedPasswordInitializer 启动期强制）；
    #                                     四类字符那半条由 generate-secrets.py 保证，本行不重复判
    #   GRAFANA/APP_ACTUATOR_BASIC/NEO4J/REDIS/DEEPSEEK 无文档化长度下限 → 仅判非空非占位；
    #                                     留空在本项目**是有语义的**（fail-closed / 跳过注入 / 回落内存图），
    #                                     故"留空"只标 EMPTY 不算 FAIL，由 §29 逐项决定何时必须填。
    STRENGTH_RULES = [
        ("MYSQL_ROOT_PASSWORD", 16, None, False),
        ("JWT_SECRET", 32, None, True),
        ("APP_ADMIN_INITIAL_PASSWORD", 8, 32, False),
        ("GRAFANA_ADMIN_PASSWORD", None, None, False),
        ("APP_ACTUATOR_BASIC_PASSWORD", None, None, False),
        ("NEO4J_PASSWORD", None, None, False),
        ("REDIS_PASSWORD", None, None, False),
        ("DEEPSEEK_API_KEY", None, None, False),
    ]
    placeholders = [re.compile(p) for p in re.findall(
        r"(?m)^\s*'''([^']+)'''", read(os.path.join(ROOT, ".gitleaks.toml")))]

    def local_value_map(name: str) -> dict:
        p = os.path.join(ROOT, name)
        if not os.path.exists(p):
            return {}
        out = {}
        for m in re.finditer(r"(?m)^([A-Z0-9_]+)=(.*)$", read(p)):
            out.setdefault(m.group(1), m.group(2))
        return out

    vals = local_value_map(".env")
    verdicts = []
    for key, minlen, maxlen, by_bytes in STRENGTH_RULES:
        if key not in vals:
            verdicts.append(f"{key}=未设键(FAIL)")
            continue
        v = vals[key].strip()
        if not v:
            verdicts.append(f"{key}=EMPTY")
            continue
        if any(px.search(v) for px in placeholders):
            verdicts.append(f"{key}=占位串(FAIL)")
            continue
        size = len(v.encode("utf-8")) if by_bytes else len(v)
        if minlen is not None and size < minlen:
            verdicts.append(f"{key}=短于{minlen}(FAIL,{size})")
        elif maxlen is not None and size > maxlen:
            verdicts.append(f"{key}=长于{maxlen}(FAIL,{size})")
        else:
            verdicts.append(f"{key}=OK({size})")
    d["env_strength"] = " ".join(verdicts)
    d["env_strength_fail_count"] = sum(1 for x in verdicts if "FAIL" in x)

    # example 键的「消费者存在性」：DR-63 的推广——键被列出不等于会被读取。
    # 口径：在代码/配置类文件（排除 md、.gitleaks.toml、模板自身、归档与构建产物）的
    # **非注释行**里找键名；零命中即该键无任何消费者（填了也无效）。
    CONSUMER_EXTS = {".yml", ".yaml", ".java", ".py", ".ts", ".js", ".cjs", ".mjs",
                     ".sh", ".bat", ".hcl", ".sql", ".properties", ".toml", ".conf"}
    CONSUMER_SKIP = {".git", "node_modules", "target", "dist", "venv", ".venv",
                     "__pycache__", "data", ".workbuddy", "archive", "docs", "外部资料"}
    COMMENT_PREFIX = ("#", "//", "*", "rem ", "::", "--", "/*")

    def consumers(key: str) -> int:
        pat = re.compile(r"\b" + key + r"\b")
        return sum(1 for _, line in CONSUMER_LINES if pat.search(line))

    def collect_consumer_lines():
        """一次遍历收齐所有消费者候选文件的非注释行，避免逐键全仓扫描。"""
        out = []
        for dirpath, dirnames, filenames in os.walk(ROOT):
            dirnames[:] = [x for x in dirnames
                           if x not in CONSUMER_SKIP and not x.startswith(".")]
            for fn in filenames:
                if os.path.splitext(fn)[1] not in CONSUMER_EXTS:
                    continue
                if fn in (".env", ".env.example", ".gitleaks.toml"):
                    continue
                p = os.path.join(dirpath, fn)
                try:
                    text = read(p)
                except (OSError, UnicodeDecodeError):
                    continue
                for line in text.splitlines():
                    s = line.strip()
                    if s and not s.startswith(COMMENT_PREFIX):
                        out.append((p, line))
        return out

    CONSUMER_LINES = collect_consumer_lines()
    dead = sorted(k for k in example_keys if consumers(k) == 0)
    d["env_example_dead_keys"] = ",".join(dead) or "无"
    d["env_example_dead_count"] = len(dead)

    # ---------------- SpotBugs 门禁状态 ----------------
    pom = read(os.path.join(BACK, "pom.xml"))
    m = re.search(r"<failOnError>(true|false)</failOnError>", pom)
    d["spotbugs_fail_on_error"] = m.group(1) if m else "未配置"

    return d


LABELS = {
    "backend_main_java": "后端 main .java（文件计数）",
    "backend_test_java": "后端 test .java（文件计数）",
    "controller_files": "controller/ .java",
    "service_top_java": "service/ 顶层 .java（非递归，全部为接口）",
    "service_component_java": "service/component/ .java（9 域子包合计）",
    "service_component_domains": "service/component/ 域子包数",
    "service_impl_java": "service/impl/ .java",
    "mapper_java": "mapper/ .java",
    "entity_java": "entity/ .java（文件计数）",
    "entity_java_excl_enum": "entity/ 实体类（排除 enum/interface）",
    "dto_star_dto_java": "dto/ *DTO.java",
    "vo_star_vo_java": "vo/ *VO.java",
    "tables_index_sql": "数据表（index.sql 严格 CREATE TABLE）",
    "tables_v1_sql": "数据表（V1 迁移严格 CREATE TABLE）",
    "tables_migrations_distinct": "数据表（全迁移 CREATE TABLE 去重＝当前库表数）",
    "tables_index_sql_lag": "index.sql 落后迁移的表数",
    "tables_index_sql_missing_list": "index.sql 缺失表名",
    "flyway_migrations": "Flyway 结构迁移脚本数（db/migration，进生产）",
    "flyway_demo_seeds": "Flyway 演示种子脚本数（db/seed-demo，生产排除）",
    "op_operation": "@Operation（源码计数，排除注释行）",
    "op_valid": "@Valid（源码计数，排除注释行）",
    "op_transactional": "@Transactional（源码计数，排除注释行）",
    "op_preauthorize": "@PreAuthorize（源码计数，排除注释行）",
    "op_tag": "@Tag（源码计数，排除注释行）",
    "op_exception_handler": "@ExceptionHandler（源码计数，排除注释行）",
    "op_deprecated": "@Deprecated（源码计数，排除注释行）",
    "http_get": "方法级 HTTP 操作：GET（@GetMapping，controller/）",
    "http_post": "方法级 HTTP 操作：POST（@PostMapping，controller/）",
    "http_put": "方法级 HTTP 操作：PUT（@PutMapping，controller/）",
    "http_delete": "方法级 HTTP 操作：DELETE（@DeleteMapping，controller/）",
    "http_patch": "方法级 HTTP 操作：PATCH（@PatchMapping，controller/）",
    "http_ops_total": "**方法级 HTTP 操作合计**（手册 §15 / B.1 权威来源）",
    "http_class_level_mapping": "类级 @RequestMapping（controller/ 内出现次数）",
    "frontend_vue": "前端 .vue（文件计数）",
    "frontend_test_ts": "前端 .test.ts",
    "frontend_test_js": "前端 .test.js（应为 0）",
    "frontend_api_modules": "前端 api/ 模块 .ts（含 test 则口径不同）",
    "frontend_routes": "前端路由 path: 条数",
    "frontend_e2e_specs": "Playwright e2e spec 文件数",
    "ci_workflow_files": "CI 工作流文件数",
    "ci_jobs": "ci.yml 作业数（jobs: 下 2 空格键）",
    "alert_groups": "告警规则组数",
    "alert_rules": "告警规则条数",
    "grafana_panels": "Grafana 面板数",
    "grafana_rows": "Grafana row 分组数",
    "typecheck_baseline_errors": "类型门禁基线错误数",
    "typecheck_baseline_files": "类型门禁基线文件数",
    "env_example_keys": ".env.example 生效键数（未注释、去重）",
    "env_local_keys": "本机 .env 键数（仅计数，不读值）",
    "env_local_missing_count": "本机 .env 相对 example 缺失键数",
    "env_local_missing_keys": "本机 .env 缺失的键名",
    "env_local_extra_keys": "本机 .env 独有（example 无）的键名",
    "env_local_key_names": "本机 .env 已有键名（仅键名，用于补齐时判重）",
    "env_example_dead_count": "example 中无任何代码消费者的键数",
    "env_example_dead_keys": "example 中无任何代码消费者的键名",
    "env_strength": "本机 .env 口令强度判定（只出派生结论，不含值）",
    "env_strength_fail_count": "本机 .env 强度不合格项数（占位串或短于下限）",
    "spotbugs_fail_on_error": "SpotBugs failOnError",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true", help="输出 JSON")
    args = ap.parse_args()

    d = audit()
    if args.json:
        print(json.dumps(d, ensure_ascii=False, indent=2))
        return 0

    width = max(len(LABELS.get(k, k)) for k in d)
    print("=" * (width + 12))
    print("附录 B 统计口径审计（实测）")
    print("=" * (width + 12))
    for k in LABELS:
        if k in d:
            print("%-*s  %s" % (width, LABELS[k], d[k]))
    for k in d:
        if k not in LABELS:
            print("%-*s  %s" % (width, k, d[k]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
