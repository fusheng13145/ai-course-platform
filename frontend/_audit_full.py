#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
全量前端功能接线核查（不抽样）
- 解析 frontend/src/views 下全部 .vue（排除 __tests__）
- 提取：导入的 @/api 模块、调用的 api 函数、Math.random 数据风险、硬编码/示例/mock 风险
- 解析 src/api/*.js 中每个 api 函数对应的 HTTP method+path
- 解析 router/index.js 中每个视图的注册情况
- 解析后端全部 Controller 的 @RequestMapping 端点集合
- 输出：逐页状态 + 断链端点 + 风险页
"""
import os, re, json

FE = r"C:\Users\ASUS\Desktop\大创赛\ai-course-platform\frontend"
BE = r"C:\Users\ASUS\Desktop\大创赛\ai-course-platform\backend"
VIEWS = os.path.join(FE, "src", "views")
APIS = os.path.join(FE, "src", "api")
ROUTER = os.path.join(FE, "src", "router", "index.js")

# ---------- 1. 解析后端端点 ----------
def parse_backend_endpoints():
    eps = set()
    ctrl = os.path.join(BE, "src", "main", "java")
    for root, _, files in os.walk(ctrl):
        for f in files:
            if f.endswith("Controller.java"):
                p = os.path.join(root, f)
                txt = open(p, encoding="utf-8", errors="ignore").read()
                # class-level prefix
                cls_prefix = ""
                m = re.search(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', txt)
                if m:
                    cls_prefix = m.group(1).strip("/")
                # method-level
                for mm in re.finditer(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\s*\(\s*(value\s*=\s*)?["\']([^"\']+)["\']', txt):
                    verb = mm.group(1)
                    path = mm.group(3).strip("/")
                    full = (cls_prefix + "/" + path).strip("/")
                    eps.add(full)
    return eps

# ---------- 2. 解析 api/*.js -> function: (method, path) ----------
REQ_RE = re.compile(r'request\.(get|post|put|delete|patch)\s*\(\s*([\'"`])([^\'"`]+)\2')

def _clean(path):
    path = path.split("?")[0]  # 剥离查询串
    return re.sub(r'\$\{[^}]*\}', '', path).strip("/").rstrip("/")

def parse_api_modules():
    # returns dict: func_name -> list[(method, path)]
    funcs = {}
    for fn in os.listdir(APIS):
        if not fn.endswith(".js") or fn in ("request.js",) or fn.startswith("__"):
            continue
        p = os.path.join(APIS, fn)
        txt = open(p, encoding="utf-8", errors="ignore").read()
        # 按 export const 分块（块=当前 export 到下一个 export/文件尾）
        blocks = list(re.finditer(r'export\s+const\s+(\w+)\s*=', txt))
        for i, b in enumerate(blocks):
            name = b.group(1)
            start = b.end()
            end = blocks[i+1].start() if i+1 < len(blocks) else len(txt)
            body = txt[start:end]
            # 对象风格：xxxApi = { method(...) { return request.get('...') }, ... }
            if re.match(r'\s*\{', body) and name.endswith("Api"):
                # 对象内每个方法：methodName(args) { ... request.VERB('path') ... }
                for mm in re.finditer(r'(\w+)\s*(?:\([^)]*\)|:\s*(?:async\s*)?\([^)]*\)\s*=>|\([^)]*\)\s*\{)', body):
                    pass  # 方法边界解析复杂，改用逐行扫描
                for line in body.splitlines():
                    lm = re.match(r'\s*(?:async\s+)?(\w+)\s*[:(]', line)
                    rm = REQ_RE.search(line)
                    if lm and rm:
                        funcs.setdefault(lm.group(1), []).append(
                            (rm.group(1).upper(), _clean(rm.group(3))))
                # 同时以对象名整体登记（视图可能 alias.method 调用，method 已单独登记）
            else:
                # 箭头函数/普通函数风格：取块内全部 request 调用
                for rm in REQ_RE.finditer(body):
                    funcs.setdefault(name, []).append(
                        (rm.group(1).upper(), _clean(rm.group(3))))
    return funcs

# ---------- 3. 解析 router ----------
def parse_router():
    txt = open(ROUTER, encoding="utf-8", errors="ignore").read()
    # import('@/views/....vue')
    registered = set(re.findall(r"import\('@/views/([^']+\.vue)'\)", txt))
    registered |= set(re.findall(r"import\('@/views/([^']+\.vue)'\)", txt))
    return registered

# ---------- 4. 解析每个 view ----------
def parse_view(path, api_funcs, api_module_funcs):
    txt = open(path, encoding="utf-8", errors="ignore").read()
    # 取 script 块
    m = re.search(r"<script[^>]*>([\s\S]*?)</script>", txt)
    script = m.group(1) if m else txt
    # 导入的 api 模块：默认导入 / 命名导入 / 命名空间导入
    imported = {}   # alias -> mod （默认或 * as 导入，调用形如 alias.func(...)）
    named = set()   # 直接命名导入的函数名，调用形如 func(...)
    for im in re.finditer(r"import\s+(\w+)\s+from\s+['\"]@/api/([\w-]+)['\"]", script):
        imported[im.group(1)] = im.group(2)
    for im in re.finditer(r"import\s*\*\s*as\s+(\w+)\s+from\s+['\"]@/api/([\w-]+)['\"]", script):
        imported[im.group(1)] = im.group(2)
    for im in re.finditer(r"import\s*\{([^}]+)\}\s*from\s+['\"]@/api/([\w-]+)['\"]", script):
        mod = im.group(2)
        for name in im.group(1).split(","):
            name = name.strip().split(" as ")[-1].strip()
            if name:
                if name.endswith("Api"):
                    imported[name] = mod  # 命名导入的对象（xxxApi），按 alias.method 调
                else:
                    named.add(name)
    # 调用的 api 函数：alias.Func( / 直接 Func( / request.METHOD 直接调用
    calls = []
    for alias, mod in imported.items():
        for cm in re.finditer(r"\b" + re.escape(alias) + r"\.(\w+)\s*\(", script):
            calls.append((mod, cm.group(1)))
    for name in named:
        if re.search(r"\b" + re.escape(name) + r"\s*\(", script):
            calls.append(("named", name))
    # 直接 request.xxx('...') 调用（统计 http 调用存在性，不解析具体 path）
    direct_request = len(re.findall(r"\brequest\.(get|post|put|delete|patch)\s*\(", script))
    # 解析为 http path
    resolved = []
    for mod, fname in calls:
        for pair in api_funcs.get(fname, []):
            resolved.append(pair)
    # 风险：Math.random 用于数据（排除动画粒子等需人工判断，先标记）
    math_random = len(re.findall(r"Math\.random", script))
    # 硬编码/示例/mock 风险（仅当出现在数据赋值或返回中，粗略标记）
    fake_kw = len(re.findall(r"示例数据|mockData|Mock\.|generateFallbackExtraction|硬编码|hardcoded|假数据|test data|fake", script))
    return {
        "imported_modules": sorted(set(imported.values())),
        "api_calls": sorted(set(calls)),
        "resolved_http": sorted(set(resolved)),
        "math_random_count": math_random,
        "fake_kw_count": fake_kw,
        "direct_request": direct_request,
        "has_api": len(resolved) > 0 or direct_request > 0,
    }

def main():
    backend_eps = parse_backend_endpoints()
    api_funcs = parse_api_modules()
    router_reg = parse_router()

    rows = []
    for root, _, files in os.walk(VIEWS):
        if "__tests__" in root or "components" in root.split(os.sep):
            continue
        for fn in files:
            if not fn.endswith(".vue"):
                continue
            full = os.path.join(root, fn)
            rel = os.path.relpath(full, VIEWS).replace("\\", "/")
            info = parse_view(full, api_funcs, None)
            # router 注册？
            reg = any(rel == r or rel.endswith(r.split("/")[-1]) for r in router_reg)
            # 更宽松：vue 文件名+路径匹配
            reg2 = rel in router_reg
            info["routed"] = reg2
            info["rel"] = rel
            # 后端端点存在性（容忍 /api 前缀、{id} 与模板占位）
            def norm(p):
                p = p.strip("/")
                p = re.sub(r"\{[^}]*\}", "", p)      # 去 {id}
                p = re.sub(r"\$\{[^}]*\}", "", p)    # 去 ${id}
                p = p.rstrip("/")
                return p
            missing = []
            for method, path in info["resolved_http"]:
                np_ = norm(path)
                hit = any(norm(e) == np_ or norm(e).endswith(np_) or np_.endswith(norm(e)) for e in backend_eps)
                if not hit:
                    missing.append(f"{method} /{path}")
            info["missing_backend"] = missing
            rows.append(info)

    # 统计
    total = len(rows)
    no_api = [r["rel"] for r in rows if not r["has_api"]]
    not_routed = [r["rel"] for r in rows if not r["routed"]]
    math_risk = [(r["rel"], r["math_random_count"]) for r in rows if r["math_random_count"] > 0]
    fake_risk = [(r["rel"], r["fake_kw_count"]) for r in rows if r["fake_kw_count"] > 0]
    broken = [(r["rel"], r["missing_backend"]) for r in rows if r["missing_backend"]]

    out = {
        "total_views": total,
        "no_api_call": no_api,
        "not_routed": not_routed,
        "math_random_risk": math_risk,
        "fake_keyword_risk": fake_risk,
        "broken_endpoint_links": broken,
        "backend_endpoint_count": len(backend_eps),
        "details": rows,
    }
    with open(os.path.join(os.path.dirname(__file__), "_audit_full.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)

    # 控制台摘要
    print(f"总页面数: {total}")
    print(f"后端端点数: {len(backend_eps)}")
    print(f"\n[1] 无任何 API 调用的页面 ({len(no_api)}):")
    for x in no_api: print("   -", x)
    print(f"\n[2] 未在 router 注册的页面 ({len(not_routed)}):")
    for x in not_routed: print("   -", x)
    print(f"\n[3] Math.random 命中页面 ({len(math_risk)}):")
    for x, c in math_risk: print(f"   - {x} (x{c})")
    print(f"\n[4] 伪造/示例/mock 关键词命中页面 ({len(fake_risk)}):")
    for x, c in fake_risk: print(f"   - {x} (x{c})")
    print(f"\n[5] 前端调用但后端缺失的端点 (断链) ({len(broken)}):")
    for x, ms in broken:
        print(f"   - {x}: {ms}")

if __name__ == "__main__":
    main()
