"""
知识图谱存储后端单元测试（kg_store.py）
======================================
覆盖 MemoryStore 的关系增删、多跳遍历（depth 边界 / 环图 / 孤立节点）、节点显式
id 覆盖幂等与陈旧边清理（缺陷4），以及 **不依赖真实 Neo4j** 的 Neo4jStore 单元测试
（用 fake driver 断言 Cypher 参数化正确、连接被释放）。

运行：
    cd kg_service && python -m pytest -q test_kg_store.py
"""
from __future__ import annotations

import pytest

import kg_store
from neo4j_fake import FakeDriver, FakeGraphDatabase, FakeResult


@pytest.fixture
def mem():
    return kg_store.MemoryStore()


# ================================================================== 关系增删
def test_relation_add_and_delete(mem):
    a = mem.create_node(1, "A", "", "CONCEPT")
    b = mem.create_node(1, "B", "", "CONCEPT")
    rel = mem.create_relation(a["id"], b["id"], "PREREQUISITE")
    assert rel["id"] == 1
    assert len(mem.relations) == 1
    assert mem.delete_relation(rel["id"]) is True
    assert mem.relations == []
    assert mem.delete_relation(999) is False  # 不存在的 id


def test_delete_node_also_drops_incident_edges(mem):
    a = mem.create_node(1, "A", "", "CONCEPT")
    b = mem.create_node(1, "B", "", "CONCEPT")
    mem.create_relation(a["id"], b["id"], "PREREQUISITE")
    assert mem.delete_node(a["id"]) is True
    assert mem.relations == []  # 悬空边随节点删除


# ================================================================== 多跳遍历
def test_relations_of_depth_boundaries(mem):
    # 链 A -> B -> C -> D
    ids = [mem.create_node(1, n, "", "CONCEPT")["id"] for n in "ABCD"]
    for s, t in zip(ids, ids[1:]):
        mem.create_relation(s, t, "PREREQ")

    d1 = mem.relations_of(ids[0], depth=1)
    assert [r["node"]["id"] for r in d1] == [ids[1]]
    assert all(r["depth"] == 1 for r in d1)

    d2 = mem.relations_of(ids[0], depth=2)
    assert {r["node"]["id"] for r in d2} == {ids[1], ids[2]}
    assert max(r["depth"] for r in d2) == 2

    d3 = mem.relations_of(ids[0], depth=3)
    assert {r["node"]["id"] for r in d3} == {ids[1], ids[2], ids[3]}
    assert max(r["depth"] for r in d3) == 3


def test_relations_of_depth_zero_treated_as_one(mem):
    # depth<=0 归一到 1（与实现 max(1, depth) 语义一致），不应返回空或报错
    a = mem.create_node(1, "A", "", "CONCEPT")["id"]
    b = mem.create_node(1, "B", "", "CONCEPT")["id"]
    mem.create_relation(a, b, "PREREQ")
    assert [r["node"]["id"] for r in mem.relations_of(a, depth=0)] == [b]


def test_relations_of_cycle_does_not_hang(mem):
    # 环 A -> B -> C -> A：遍历应靠 seen 去重终止，不死循环
    ids = [mem.create_node(1, n, "", "CONCEPT")["id"] for n in "ABC"]
    mem.create_relation(ids[0], ids[1], "P")
    mem.create_relation(ids[1], ids[2], "P")
    mem.create_relation(ids[2], ids[0], "P")
    rels = mem.relations_of(ids[0], depth=5)
    reached = {r["node"]["id"] for r in rels}
    assert ids[0] not in reached          # 起点自身不回环
    assert reached == {ids[1], ids[2]}    # 每个节点只到达一次


def test_relations_of_isolated_node_returns_empty(mem):
    lone = mem.create_node(1, "孤立", "", "CONCEPT")["id"]
    assert mem.relations_of(lone, depth=3) == []


# ================================================================== 幂等 / 覆盖
def test_explicit_id_node_overwrite_is_idempotent(mem):
    mem.create_node(1, "旧名", "旧内容", "CONCEPT", node_id=100)
    mem.create_node(1, "新名", "新内容", "CONCEPT", node_id=100)
    nodes = [n for n in mem.nodes.values() if n["id"] == 100]
    assert len(nodes) == 1                      # 同 id 覆盖不新增
    assert nodes[0]["name"] == "新名"


def test_explicit_id_overwrite_clears_stale_edges(mem):
    """缺陷4 回归：显式 id 命中已有节点时，覆盖须清理该节点陈旧边，不留悬空关系。"""
    a = mem.create_node(1, "A", "", "CONCEPT", node_id=10)["id"]
    b = mem.create_node(1, "B", "", "CONCEPT")["id"]
    c = mem.create_node(1, "C", "", "CONCEPT")["id"]
    mem.create_relation(a, b, "PREREQ")         # 出边
    mem.create_relation(c, a, "PREREQ")         # 入边
    assert len(mem.relations) == 2

    # 用同一显式 id 覆盖 A
    mem.create_node(1, "A2", "新内容", "CONCEPT", node_id=10)
    assert mem.relations == []                  # 陈旧边全部清除
    assert mem.get_node(a)["name"] == "A2"


# ================================================================== 路径规划
def test_path_plan_multi_hop_and_unreachable(mem):
    ids = [mem.create_node(2, n, "", "CONCEPT")["id"] for n in "ABCD"]
    mem.create_relation(ids[0], ids[1], "P")
    mem.create_relation(ids[1], ids[2], "P")
    plan = mem.path_plan(ids[0], ids[2], max_hops=3)
    assert plan["path"] == ids[0:3]
    assert plan["hops"] == 2
    assert len(plan["edges"]) == 2

    # D 与链不连通 -> 空 path
    assert mem.path_plan(ids[0], ids[3], max_hops=3)["path"] == []
    # 超出 max_hops 范围 -> 空 path
    assert mem.path_plan(ids[0], ids[2], max_hops=1)["path"] == []


# ================================================================== Neo4jStore（fake driver）
def _neo4j_store_with_fake(monkeypatch, respond=None):
    drv = FakeDriver(respond=respond)
    monkeypatch.setattr(kg_store, "_GraphDatabase", FakeGraphDatabase(drv))
    store = kg_store.Neo4jStore("bolt://fake:7687", "neo4j", "secret")
    return store, drv


def test_neo4j_store_requires_driver(monkeypatch):
    monkeypatch.setattr(kg_store, "_GraphDatabase", None)
    with pytest.raises(RuntimeError):
        kg_store.Neo4jStore("bolt://x", "neo4j", "pw")


def test_neo4j_create_node_parameterized_no_interpolation(monkeypatch):
    store, drv = _neo4j_store_with_fake(monkeypatch)
    try:
        store.create_node(7, "进程", "资源分配单位", "CONCEPT", node_id=42)
    finally:
        store.close()

    # DETACH DELETE（覆盖清边）与 CREATE 都以参数传递，不在 Cypher 文本里拼 id
    del_params = drv.queries_containing("DETACH DELETE n")
    assert del_params and del_params[0]["id"] == 42
    create_params = drv.queries_containing("CREATE (n:Kp)")
    assert create_params and create_params[0]["props"]["id"] == 42
    # 任何一条 Cypher 文本都不应出现裸 id 42（防注入 / 参数化正确）
    assert all("42" not in q for q, _ in drv.calls)


def test_neo4j_create_relation_uses_params_and_merge(monkeypatch):
    def respond(query, params):
        if "coalesce(max(r.id)" in query:
            return FakeResult({"rid": 5})
        return FakeResult(None)

    store, drv = _neo4j_store_with_fake(monkeypatch, respond)
    try:
        rel = store.create_relation(3, 9, "PREREQUISITE")
    finally:
        store.close()

    assert rel["id"] == 5
    merge = drv.queries_containing("MERGE (a)-[r:RELATES")
    assert merge == [{"s": 3, "t": 9, "rt": "PREREQUISITE", "rid": 5}]
    assert all("3" not in q and "9" not in q for q, _ in drv.calls
               if "coalesce" not in q)  # 节点 id 走参数，不拼进查询


def test_neo4j_delete_node_and_relation_return_bool(monkeypatch):
    def respond(query, params):
        if "count(n)" in query:
            return FakeResult({"c": 1})
        if "count(r)" in query:
            return FakeResult({"c": 0})
        return FakeResult(None)

    store, drv = _neo4j_store_with_fake(monkeypatch, respond)
    try:
        assert store.delete_node(11) is True
        assert store.delete_relation(12) is False
    finally:
        store.close()
    assert drv.queries_containing("DETACH DELETE n")[0] == {"id": 11}


def test_neo4j_path_plan_bounded_hops_and_params(monkeypatch):
    def respond(query, params):
        if "shortestPath" in query:
            return FakeResult({"ids": [1, 2, 3],
                               "edges": [{"source_id": 1, "target_id": 2,
                                          "relation_type": "P"}]})
        return FakeResult(None)

    store, drv = _neo4j_store_with_fake(monkeypatch, respond)
    try:
        plan = store.path_plan(1, 3, max_hops=99)  # 越界被夹到 1..10
    finally:
        store.close()

    assert plan["path"] == [1, 2, 3]
    assert plan["hops"] == 2
    q, params = [c for c in drv.calls if "shortestPath" in c[0]][0]
    assert params == {"s": 1, "t": 3}             # 起终点走参数
    assert "*..10" in q                           # 跳数上限被 clamp 成 10，非裸 99


def test_neo4j_sessions_and_connection_released(monkeypatch):
    store, drv = _neo4j_store_with_fake(monkeypatch)
    store.get_node(1)
    store.course_nodes(1)
    # 每次会话都应成对 open/close（上下文管理器保证），close() 后连接释放
    assert drv.sessions_opened == drv.sessions_closed
    assert drv.closed is False
    store.close()
    assert drv.closed is True
    assert drv.sessions_opened == drv.sessions_closed


def test_neo4j_relations_of_and_edges_from_iterate_records(monkeypatch):
    node_row = {"id": 2, "name": "B", "type": "CONCEPT", "content": "",
                "course_id": 1, "created_at": "t"}

    def respond(query, params):
        if "RELATES*1.." in query:
            return FakeResult(None, rows=[dict(node_row, rt="P", depth=1)])
        if "a.id IN $ids" in query:
            return FakeResult(None, rows=[{"id": 1, "source_id": 1,
                                           "target_id": 2, "relation_type": "P"}])
        if "course_id: $cid" in query:
            return FakeResult(None, rows=[dict(node_row)])
        return FakeResult(None)

    store, drv = _neo4j_store_with_fake(monkeypatch, respond)
    try:
        rels = store.relations_of(1, depth=2)
        edges = store.edges_from({1})
        nodes = store.course_nodes(1)
    finally:
        store.close()

    assert rels[0]["node"]["id"] == 2 and rels[0]["relation_type"] == "P"
    assert edges == [{"id": 1, "source_id": 1, "target_id": 2, "relation_type": "P"}]
    assert nodes[0]["id"] == 2
    # edges_from 把集合序列化成 list 传参，不拼进查询文本
    assert drv.queries_containing("a.id IN $ids")[0]["ids"] == [1]
