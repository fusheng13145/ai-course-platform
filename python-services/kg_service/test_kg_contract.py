"""
知识图谱服务（8001）契约冒烟测试
========================================
验证 docs/项目手册.md（第八部分）关键端点的契约正确性，与 Java 端
`KnowledgeGraphServiceImpl` / `HttpKnowledgeGraphProvider` 的下游调用对齐。
使用 FastAPI TestClient 内嵌运行，不依赖外部端口，CI 中稳定快速。

本测试守卫**契约（端点路径 + JSON 形状）**；演进存储后端（kg_store.py：
Neo4j 真实引擎 / 内存基线）时须保持这些端点与响应结构不变。

本地运行：
    pip install -r requirements.txt pytest
    pytest -q test_kg_contract.py
"""

import os

import pytest
from fastapi.testclient import TestClient

import kg_store
import neo4j_fake
from kg_service import app


@pytest.fixture(autouse=True)
def memory_backend(monkeypatch):
    """契约用例强制内存后端并逐例重置，保证 CI/本地结果确定且不触碰 Neo4j。"""
    monkeypatch.setenv("KG_BACKEND", "memory")
    kg_store._store = None
    kg_store.get_store().reset()
    yield
    kg_store._store = None


def _neo4j_store_or_skip():
    """返回可连通的 Neo4jStore；驱动缺失/服务不可达时跳过（CI 恒跳过）。"""
    if kg_store._GraphDatabase is None:
        pytest.skip("neo4j 驱动未安装，跳过真实图引擎用例")
    try:
        store = kg_store.Neo4jStore(
            kg_store._uri() or "bolt://127.0.0.1:7687",
            os.getenv("NEO4J_USERNAME", "neo4j"), os.getenv("NEO4J_PASSWORD", ""))
        store.get_node(-1)  # 触发一次真实查询验证连通
    except Exception as e:
        pytest.skip(f"本地 Neo4j 不可用：{e}")
    return store


def test_health():
    with TestClient(app) as c:
        resp = c.get("/api/v1/graph/health")
        assert resp.status_code == 200


def test_node_create_and_fetch():
    with TestClient(app) as c:
        created = c.post(
            "/api/v1/graph/nodes",
            json={"course_id": 1, "name": "线性回归", "content": "最小二乘", "type": "CONCEPT"},
        )
        assert created.status_code == 200
        nid = created.json()["id"]

        fetched = c.get(f"/api/v1/graph/nodes/{nid}")
        assert fetched.status_code == 200
        assert fetched.json()["name"] == "线性回归"


def test_relation_create_and_node_relations():
    with TestClient(app) as c:
        a = c.post("/api/v1/graph/nodes", json={"course_id": 1, "name": "A"}).json()
        b = c.post("/api/v1/graph/nodes", json={"course_id": 1, "name": "B"}).json()
        rel = c.post(
            "/api/v1/graph/relations",
            json={"source_id": a["id"], "target_id": b["id"], "relation_type": "PREREQUISITE"},
        )
        assert rel.status_code == 200

        rels = c.get(f"/api/v1/graph/nodes/{a['id']}/relations")
        assert rels.status_code == 200
        assert any(r["relation_type"] == "PREREQUISITE" for r in rels.json()["relations"])


def test_path_plan_shortest_hops():
    with TestClient(app) as c:
        a = c.post("/api/v1/graph/nodes", json={"course_id": 2, "name": "X"}).json()
        b = c.post("/api/v1/graph/nodes", json={"course_id": 2, "name": "Y"}).json()
        c.post(
            "/api/v1/graph/relations",
            json={"source_id": a["id"], "target_id": b["id"], "relation_type": "PREREQUISITE"},
        )
        plan = c.post(
            "/api/v1/graph/path/plan",
            json={"source_id": a["id"], "target_id": b["id"], "max_hops": 3},
        )
        assert plan.status_code == 200
        body = plan.json()
        assert body["path"] == [a["id"], b["id"]]
        assert body["hops"] == 1


def test_ingest_and_subgraph():
    with TestClient(app) as c:
        c.post("/api/v1/graph/nodes", json={"course_id": 3, "name": "N1"})
        c.post("/api/v1/graph/nodes", json={"course_id": 3, "name": "N2"})

        ing = c.post("/api/v1/graph/ingest", json={"course_id": 3})
        assert ing.status_code == 200
        assert ing.json()["status"] == "ok"
        # 旧契约调用（仅 course_id）不得写入任何数据
        assert ing.json()["upserted_nodes"] == 0

        sub = c.post("/api/v1/graph/subgraph", json={"course_id": 3, "max_hops": 2})
        assert sub.status_code == 200
        body = sub.json()
        assert body["status"] == "ok"
        assert len(body["nodes"]) == 2


def _snapshot(course_id: int) -> dict:
    return {
        "course_id": course_id,
        "nodes": [
            {"id": 1001, "course_id": course_id, "name": "变量", "content": "存储数据的命名单元", "type": "CONCEPT"},
            {"id": 1002, "course_id": course_id, "name": "数据类型", "content": "对值的分类", "type": "CONCEPT"},
            {"id": 1003, "course_id": course_id, "name": "运算符", "content": "对值进行计算", "type": "CONCEPT"},
        ],
        "relations": [
            {"id": 2001, "source_id": 1001, "target_id": 1002, "relation_type": "PREREQUISITE"},
            {"id": 2002, "source_id": 1002, "target_id": 1003, "relation_type": "PREREQUISITE"},
        ],
    }


def test_ingest_snapshot_writes_nodes_and_relations():
    """快照摄取必须真实写图：节点可查、关系可走、路径可算。"""
    with TestClient(app) as c:
        body = c.post("/api/v1/graph/ingest", json=_snapshot(11)).json()
        assert body["status"] == "ok"
        assert body["upserted_nodes"] == 3
        assert body["upserted_relations"] == 2
        assert body["synced_nodes"] == 3

        sub = c.post("/api/v1/graph/subgraph",
                     json={"course_id": 11, "max_hops": 2}).json()
        assert {n["id"] for n in sub["nodes"]} == {1001, 1002, 1003}
        assert {n["name"] for n in sub["nodes"]} == {"变量", "数据类型", "运算符"}
        assert len(sub["edges"]) == 2

        plan = c.post("/api/v1/graph/path/plan",
                      json={"source_id": 1001, "target_id": 1003, "max_hops": 2}).json()
        assert plan["path"] == [1001, 1002, 1003]


def test_ingest_snapshot_is_idempotent_and_prunes_stale_nodes():
    """重复摄取不产生重复数据；快照收缩后陈旧节点与其边必须被清理。"""
    with TestClient(app) as c:
        c.post("/api/v1/graph/ingest", json=_snapshot(12))
        again = c.post("/api/v1/graph/ingest", json=_snapshot(12)).json()
        assert again["upserted_nodes"] == 3
        assert again["upserted_relations"] == 2
        assert again["synced_nodes"] == 3

        shrunk = _snapshot(12)
        shrunk["nodes"] = shrunk["nodes"][:2]
        shrunk["relations"] = shrunk["relations"][:1]
        res = c.post("/api/v1/graph/ingest", json=shrunk).json()
        assert res["synced_nodes"] == 2
        assert res["pruned_nodes"] == 1
        assert res["upserted_relations"] == 1

        sub = c.post("/api/v1/graph/subgraph",
                     json={"course_id": 12, "max_hops": 2}).json()
        assert {n["id"] for n in sub["nodes"]} == {1001, 1002}
        assert len(sub["edges"]) == 1


def test_ingest_snapshot_does_not_touch_other_courses():
    """摄取只以 course_id 为界：其他课程的节点/边不得被清理或篡改。"""
    with TestClient(app) as c:
        c.post("/api/v1/graph/ingest", json=_snapshot(13))
        other = {
            "course_id": 14,
            "nodes": [{"id": 2001, "course_id": 14, "name": "别的课程", "content": "", "type": "CONCEPT"}],
            "relations": [],
        }
        c.post("/api/v1/graph/ingest", json=other)
        c.post("/api/v1/graph/ingest", json=_snapshot(13))

        sub = c.post("/api/v1/graph/subgraph",
                     json={"course_id": 14, "max_hops": 1}).json()
        assert [n["name"] for n in sub["nodes"]] == ["别的课程"]


def test_course_graph():
    with TestClient(app) as c:
        c.post("/api/v1/graph/nodes", json={"course_id": 4, "name": "C1"})
        c.post("/api/v1/graph/nodes", json={"course_id": 4, "name": "C2"})

        resp = c.get("/api/v1/graph/course/4")
        assert resp.status_code == 200
        assert len(resp.json()["nodes"]) == 2


# ------------------------------------------------------------------
# 入参边界（缺陷3）：max_hops / depth / course_id 越界必须 422，而不是被静默夹取
# ------------------------------------------------------------------
def test_path_plan_max_hops_bounds():
    with TestClient(app) as c:
        a = c.post("/api/v1/graph/nodes", json={"course_id": 5, "name": "A"}).json()
        b = c.post("/api/v1/graph/nodes", json={"course_id": 5, "name": "B"}).json()
        for bad in (0, -1, 6):
            resp = c.post("/api/v1/graph/path/plan",
                          json={"source_id": a["id"], "target_id": b["id"], "max_hops": bad})
            assert resp.status_code == 422, f"max_hops={bad} 应 422"
        ok = c.post("/api/v1/graph/path/plan",
                    json={"source_id": a["id"], "target_id": b["id"], "max_hops": 5})
        assert ok.status_code == 200


def test_relations_depth_bounds_and_course_id():
    with TestClient(app) as c:
        n = c.post("/api/v1/graph/nodes", json={"course_id": 6, "name": "N"}).json()
        for bad in (0, 6):
            resp = c.get(f"/api/v1/graph/nodes/{n['id']}/relations", params={"depth": bad})
            assert resp.status_code == 422, f"depth={bad} 应 422"
        # course_id 必须为正整数（ge=1）
        bad = c.post("/api/v1/graph/nodes", json={"course_id": 0, "name": "X"})
        assert bad.status_code == 422


# ------------------------------------------------------------------
# 显式节点 ID 覆盖语义（缺陷4）：同 id 覆盖时清理陈旧边，不遗留悬空关系
# ------------------------------------------------------------------
def test_explicit_id_overwrite_clears_stale_edges():
    with TestClient(app) as c:
        a = c.post("/api/v1/graph/nodes",
                   json={"course_id": 7, "name": "A", "id": 500}).json()
        b = c.post("/api/v1/graph/nodes", json={"course_id": 7, "name": "B"}).json()
        c.post("/api/v1/graph/relations",
               json={"source_id": a["id"], "target_id": b["id"], "relation_type": "PREREQ"})
        assert any(r["relation_type"] == "PREREQ"
                   for r in c.get(f"/api/v1/graph/nodes/{a['id']}/relations").json()["relations"])

        # 用同一显式 id 覆盖 A -> 陈旧边应被清理
        c.post("/api/v1/graph/nodes",
               json={"course_id": 7, "name": "A2", "content": "新内容", "id": 500})
        after = c.get(f"/api/v1/graph/nodes/{a['id']}/relations").json()
        assert after["relations"] == []
        assert after["node"]["name"] == "A2"


# ------------------------------------------------------------------
# 删除端点契约（D7-4 补漏）：两个 DELETE 端点此前仅在 neo4j 真实用例里间接覆盖
# （CI 恒 skip），HTTP 层契约是盲区。此处锁定其始终返回 200 + {"success": true} 的形状
# 与幂等语义，使 Java 端 HttpKnowledgeGraphProvider 删除调用不会因 404 而误判失败。
# ------------------------------------------------------------------
def test_delete_node_contract_and_idempotency():
    with TestClient(app) as c:
        n = c.post("/api/v1/graph/nodes", json={"course_id": 8, "name": "D"}).json()

        # 删除成功：契约形状固定为 {"success": true}，且删除后原节点不可取（404）
        deleted = c.delete(f"/api/v1/graph/nodes/{n['id']}")
        assert deleted.status_code == 200
        assert deleted.json() == {"success": True}
        assert c.get(f"/api/v1/graph/nodes/{n['id']}").status_code == 404

        # 幂等：删除不存在的节点仍返回 200 + success，不抛 404
        again = c.delete(f"/api/v1/graph/nodes/{n['id']}")
        assert again.status_code == 200
        assert again.json() == {"success": True}


def test_delete_relation_contract_and_idempotency():
    with TestClient(app) as c:
        a = c.post("/api/v1/graph/nodes", json={"course_id": 9, "name": "A"}).json()
        b = c.post("/api/v1/graph/nodes", json={"course_id": 9, "name": "B"}).json()
        rel = c.post("/api/v1/graph/relations",
                     json={"source_id": a["id"], "target_id": b["id"],
                           "relation_type": "PREREQUISITE"}).json()
        # 关系 id 由创建响应返回（node_relations 列表仅含 node/relation_type/depth，不含 id）
        rel_id = rel["id"]

        deleted = c.delete(f"/api/v1/graph/relations/{rel_id}")
        assert deleted.status_code == 200
        assert deleted.json() == {"success": True}
        # 删除后该节点的 PREREQUISITE 关系应已清空
        rels_after = c.get(f"/api/v1/graph/nodes/{a['id']}/relations").json()["relations"]
        assert all(r["relation_type"] != "PREREQUISITE" for r in rels_after)

        # 幂等：删除不存在的关系仍返回 200 + success
        again = c.delete(f"/api/v1/graph/relations/{rel_id}")
        assert again.status_code == 200
        assert again.json() == {"success": True}


# ------------------------------------------------------------------
# Neo4jStore 契约（fake driver，无需真实 Neo4j，恒实跑）：
# 固化 CRUD/关系/路径端点走 Neo4j 后端时的响应形状与 Cypher 参数化，
# 使原本"因缺驱动/连不上而恒 skip"的用例具备确定性的可回归断言。
# 真实连接 smoke 见下方 test_neo4j_store_crud_relations_and_path（无 Neo4j 时仍 skip）。
# ------------------------------------------------------------------
def test_neo4j_store_backend_contract_with_fake_driver(monkeypatch):
    node_seq = {"v": 100}

    def respond(query, params):
        if "coalesce(max(n.id)" in query:
            node_seq["v"] += 1
            return neo4j_fake.FakeResult({"nid": node_seq["v"]})
        if "coalesce(max(r.id)" in query:
            return neo4j_fake.FakeResult({"rid": 1})
        if "count(n)" in query:
            return neo4j_fake.FakeResult({"c": 1})
        if "shortestPath" in query:
            return neo4j_fake.FakeResult({"ids": [1, 2],
                                          "edges": [{"source_id": 1, "target_id": 2,
                                                     "relation_type": "PREREQ"}]})
        return neo4j_fake.FakeResult(None)

    drv = neo4j_fake.FakeDriver(respond=respond)
    monkeypatch.setattr(kg_store, "_GraphDatabase", neo4j_fake.FakeGraphDatabase(drv))
    store = kg_store.Neo4jStore("bolt://fake:7687", "neo4j", "pw")
    try:
        a = store.create_node(9, "进程", "资源分配单位", "CONCEPT")
        b = store.create_node(9, "线程", "调度单位", "CONCEPT")
        rel = store.create_relation(a["id"], b["id"], "PREREQ")
        plan = store.path_plan(a["id"], b["id"], max_hops=3)

        assert a["id"] and b["id"] and a["id"] != b["id"]
        assert rel["source_id"] == a["id"] and rel["relation_type"] == "PREREQ"
        assert plan["path"] == [1, 2] and plan["hops"] == 1
        # 契约：所有 id 都以参数传递，Cypher 文本里不得出现裸 id
        assert all(str(a["id"]) not in q for q, _ in drv.calls)
    finally:
        store.close()
    assert drv.closed is True
    assert drv.sessions_opened == drv.sessions_closed  # 连接/会话被释放


# ------------------------------------------------------------------
# 真实 Neo4j 引擎验证（本地已启动 Neo4j 时执行；CI 无驱动自动跳过）
# 仅操作 :Kp 标签并自行清理，不影响 Neo4j 库内其他图数据
# ------------------------------------------------------------------
def test_neo4j_store_crud_relations_and_path():
    store = _neo4j_store_or_skip()
    created = []
    try:
        a = store.create_node(999, "OS-进程", "进程是资源分配单位", "CONCEPT")
        b = store.create_node(999, "OS-线程", "线程是调度单位", "CONCEPT")
        d = store.create_node(999, "OS-调度", "CPU 调度", "CONCEPT")
        created = [a["id"], b["id"], d["id"]]
        store.create_relation(a["id"], b["id"], "PREREQUISITE")
        store.create_relation(b["id"], d["id"], "PREREQUISITE")

        assert store.get_node(a["id"])["name"] == "OS-进程"
        rels = store.relations_of(a["id"], depth=2)
        assert {r["node"]["id"] for r in rels} == {b["id"], d["id"]}
        assert any(r["depth"] == 2 for r in rels)

        plan = store.path_plan(a["id"], d["id"], max_hops=3)
        assert plan["path"] == [a["id"], b["id"], d["id"]]
        assert plan["hops"] == 2
        assert len(plan["edges"]) == 2

        ns = store.course_nodes(999)
        assert {n["id"] for n in ns} == set(created)
        es = store.edges_from({n["id"] for n in ns})
        assert len(es) == 2

        # 与内存后端的路径规划结果一致（双实现等价性）
        mem = kg_store.MemoryStore()
        ma = mem.create_node(999, "A", "", "CONCEPT")
        mb = mem.create_node(999, "B", "", "CONCEPT")
        md = mem.create_node(999, "D", "", "CONCEPT")
        mem.create_relation(ma["id"], mb["id"], "PREREQUISITE")
        mem.create_relation(mb["id"], md["id"], "PREREQUISITE")
        mp = mem.path_plan(ma["id"], md["id"], 3)
        assert mp["hops"] == plan["hops"] == 2

        for nid in created:
            assert store.delete_node(nid) is True
        assert store.course_nodes(999) == []
    finally:
        for nid in created:
            store.delete_node(nid)
        store.close()


# ---------------- 服务间内部认证（X-Internal-Key，2026-11-07 验收整改批） ----------------

def test_internal_api_key_guard(monkeypatch):
    """INTERNAL_API_KEY 非空时：业务端点缺头 401、对头放行，health 豁免。"""
    monkeypatch.setenv("INTERNAL_API_KEY", "guard-key-1")
    with TestClient(app) as c:
        missing = c.get("/api/v1/graph/course/1")
        assert missing.status_code == 401
        ok = c.get("/api/v1/graph/course/1", headers={"X-Internal-Key": "guard-key-1"})
        assert ok.status_code == 200
        health = c.get("/api/v1/graph/health")
        assert health.status_code == 200


def test_internal_api_key_disabled_by_default(monkeypatch):
    """INTERNAL_API_KEY 为空（默认）：不校验，直连行为不变。"""
    monkeypatch.delenv("INTERNAL_API_KEY", raising=False)
    with TestClient(app) as c:
        resp = c.get("/api/v1/graph/course/1")
        assert resp.status_code == 200
