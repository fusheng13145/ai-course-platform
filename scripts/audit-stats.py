#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""附录 B 统计口径审计脚本（可复跑）。

用途：把《项目手册》附录 B「统计口径权威总表」的核心数据点从源码实测出来，
避免手册数字与仓库实际结构漂移（历史上多次出现两套数字）。

用法：
    python scripts/audit-stats.py            # 打印审计表
    python scripts/audit-stats.py --json     # 输出 JSON（供脚本消费）

口径原则：
  - 每一行都注明口径（文件计数 / 正则计数 / 严格 CREATE TABLE 计数），与手册口径列一一对应；
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
    return re.sub(r"(?m)^\s*(\*|//|/\*).*$", "", text)


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
    d["flyway_migrations"] = len(walk_files(os.path.join(BACK, "src/main/resources/db/migration"), lambda f: f.endswith(".sql")))
    d["flyway_demo_seeds"] = len(walk_files(os.path.join(BACK, "src/main/resources/db/seed-demo"), lambda f: f.endswith(".sql")))

    # ---------------- 注解计数 ----------------
    base = os.path.join(BACK, "src/main/java")
    d["op_operation"] = count_annotations(base, "@Operation")
    d["op_valid"] = count_annotations(base, "@Valid")
    d["op_transactional"] = count_annotations(base, "@Transactional")
    d["op_preauthorize"] = count_annotations(base, "@PreAuthorize")
    d["op_tag"] = count_annotations(base, "@Tag")
    d["op_exception_handler"] = count_annotations(base, "@ExceptionHandler")
    d["op_deprecated"] = count_annotations(base, "@Deprecated")

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
    "flyway_migrations": "Flyway 结构迁移脚本数（db/migration，进生产）",
    "flyway_demo_seeds": "Flyway 演示种子脚本数（db/seed-demo，生产排除）",
    "op_operation": "@Operation（源码计数，排除注释行）",
    "op_valid": "@Valid（源码计数，排除注释行）",
    "op_transactional": "@Transactional（源码计数，排除注释行）",
    "op_preauthorize": "@PreAuthorize（源码计数，排除注释行）",
    "op_tag": "@Tag（源码计数，排除注释行）",
    "op_exception_handler": "@ExceptionHandler（源码计数，排除注释行）",
    "op_deprecated": "@Deprecated（源码计数，排除注释行）",
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
