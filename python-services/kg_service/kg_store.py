"""
知识图谱存储后端（Neo4j 真实引擎 / 内存基线 双实现）
====================================================
为 kg_service.py 的契约端点提供两种后端：

- Neo4j 后端：持久化真实图数据库（:Kp 节点 + [:RELATES] 关系），路径规划用
  Cypher shortestPath，多跳关系用变长路径；仅操作 :Kp 标签，不影响库内其他图数据。
- 内存后端：与历史「契约基线」行为一致（重启清空），用于 CI（未安装 neo4j 驱动）、
  契约测试隔离与 Neo4j 不可达时的降级。

后端选择（KG_BACKEND）：
  memory            强制内存
  neo4j             强制 Neo4j（连接失败直接报错）
  未设置/auto       尝试 Neo4j（需驱动可导入且 NEO4J_URI/KG_NEO4J_URI 可达），否则内存

环境变量：
  KG_BACKEND, NEO4J_URI（或 KG_NEO4J_URI）, NEO4J_USERNAME, NEO4J_PASSWORD
"""
from __future__ import annotations

import os
import threading
from collections import deque
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

_LOCK = threading.RLock()

try:
    from neo4j import GraphDatabase as _GraphDatabase
except Exception:  # CI 轻量环境无驱动
    _GraphDatabase = None


def _uri() -> str:
    return (os.getenv("KG_NEO4J_URI") or os.getenv("NEO4J_URI") or "").strip()


class MemoryStore:
    name = "memory"

    def __init__(self) -> None:
        self.nodes: Dict[int, Dict[str, Any]] = {}
        self.relations: List[Dict[str, Any]] = []
        self._node_seq = 1
        self._rel_seq = 1

    def reset(self) -> None:
        self.__init__()

    def create_node(self, course_id: int, name: str, content: str, type_: str,
                    node_id: Optional[int] = None) -> dict:
        with _LOCK:
            if node_id is None:
                node_id = self._node_seq
                self._node_seq = max(self._node_seq, node_id) + 1
            else:
                self._node_seq = max(self._node_seq, node_id + 1)
            node = {"id": node_id, "name": name, "type": type_,
                    "content": content, "course_id": course_id,
                    "created_at": _now()}
            self.nodes[node_id] = node
            return dict(node)

    def get_node(self, node_id: int) -> Optional[dict]:
        n = self.nodes.get(node_id)
        return dict(n) if n else None

    def delete_node(self, node_id: int) -> bool:
        with _LOCK:
            existed = self.nodes.pop(node_id, None) is not None
            self.relations = [r for r in self.relations
                              if r["source_id"] != node_id and r["target_id"] != node_id]
            return existed

    def create_relation(self, source_id: int, target_id: int, relation_type: str) -> dict:
        with _LOCK:
            rid = self._rel_seq
            self._rel_seq += 1
            rel = {"id": rid, "source_id": source_id, "target_id": target_id,
                   "relation_type": relation_type}
            self.relations.append(rel)
            return dict(rel)

    def delete_relation(self, relation_id: int) -> bool:
        with _LOCK:
            before = len(self.relations)
            self.relations = [r for r in self.relations if r["id"] != relation_id]
            return len(self.relations) < before

    def relations_of(self, node_id: int, depth: int) -> List[dict]:
        adj = {}
        for r in self.relations:
            adj.setdefault(r["source_id"], []).append(r)
        out: List[dict] = []
        seen = {node_id}
        frontier = [node_id]
        for d in range(1, max(1, depth) + 1):
            nxt: List[int] = []
            for cur in frontier:
                for r in adj.get(cur, []):
                    if r["target_id"] in seen:
                        continue
                    seen.add(r["target_id"])
                    out.append({"node": self.get_node(r["target_id"]) or {"id": r["target_id"]},
                                "relation_type": r["relation_type"], "depth": d})
                    nxt.append(r["target_id"])
            frontier = nxt
            if not frontier:
                break
        return out

    def course_nodes(self, course_id: int) -> List[dict]:
        return [dict(n) for n in self.nodes.values() if n.get("course_id") == course_id]

    def edges_from(self, ids: set) -> List[dict]:
        return [dict(r) for r in self.relations if r["source_id"] in ids]

    def path_plan(self, source_id: int, target_id: int, max_hops: int) -> dict:
        adj: Dict[int, List[int]] = {}
        for r in self.relations:
            adj.setdefault(r["source_id"], []).append(r["target_id"])
            adj.setdefault(r["target_id"], []).append(r["source_id"])
        if source_id == target_id:
            return {"path": [source_id], "edges": [], "hops": 0}
        if source_id not in adj:
            return {"path": [], "edges": [], "hops": 0}
        dist: Dict[int, int] = {source_id: 0}
        prev: Dict[int, Optional[int]] = {source_id: None}
        q = deque([source_id])
        reached = False
        while q:
            cur = q.popleft()
            if cur == target_id:
                reached = True
                break
            if dist[cur] >= max_hops:
                continue
            for nxt in adj.get(cur, []):
                if nxt not in dist:
                    dist[nxt] = dist[cur] + 1
                    prev[nxt] = cur
                    q.append(nxt)
        if not reached:
            return {"path": [], "edges": [], "hops": 0}
        id_path: List[int] = []
        step: Optional[int] = target_id
        while step is not None:
            id_path.append(step)
            step = prev.get(step)
        id_path.reverse()
        edges = []
        for i in range(len(id_path) - 1):
            a, b = id_path[i], id_path[i + 1]
            for r in self.relations:
                if (r["source_id"] == a and r["target_id"] == b) or (
                        r["source_id"] == b and r["target_id"] == a):
                    edges.append({"source_id": r["source_id"], "target_id": r["target_id"],
                                  "relation_type": r["relation_type"]})
                    break
        return {"path": id_path, "edges": edges, "hops": max(len(id_path) - 1, 0)}


class Neo4jStore:
    """真实图数据库后端。仅使用 :Kp 节点与 :RELATES 关系，与库内其他演示图隔离。"""
    name = "neo4j"

    def __init__(self, uri: str, username: str, password: str) -> None:
        if _GraphDatabase is None:
            raise RuntimeError("neo4j 驱动未安装：pip install neo4j")
        self._drv = _GraphDatabase.driver(uri, auth=(username, password))
        with self._drv.session() as s:
            s.run("CREATE INDEX kp_id IF NOT EXISTS FOR (n:Kp) ON (n.id)")
            s.run("CREATE INDEX kp_course IF NOT EXISTS FOR (n:Kp) ON (n.course_id)")
        self._id_lock = threading.Lock()

    def reset(self) -> None:
        # 仅清理本服务自己的数据，不触碰库内其他图
        with self._drv.session() as s:
            s.run("MATCH (n:Kp) DETACH DELETE n")

    def close(self) -> None:
        self._drv.close()

    @staticmethod
    def _row_to_node(rec) -> dict:
        return {"id": rec["id"], "name": rec["name"], "type": rec["type"],
                "content": rec.get("content", ""), "course_id": rec["course_id"],
                "created_at": rec.get("created_at", _now())}

    def create_node(self, course_id: int, name: str, content: str, type_: str,
                    node_id: Optional[int] = None) -> dict:
        with _LOCK, self._id_lock:
            with self._drv.session() as s:
                if node_id is None:
                    node_id = s.run(
                        "MATCH (n:Kp) RETURN coalesce(max(n.id), 0) + 1 AS nid").single()["nid"]
                tx = s.begin_transaction()
                node = {"id": node_id, "name": name, "type": type_,
                        "content": content, "course_id": course_id, "created_at": _now()}
                tx.run("MERGE (n:Kp {id: $id}) SET n = $props",
                       id=node_id, props=node)
                tx.commit()
                return dict(node)

    def get_node(self, node_id: int) -> Optional[dict]:
        with self._drv.session() as s:
            rec = s.run("MATCH (n:Kp {id: $id}) RETURN n.id AS id, n.name AS name, n.type AS type,"
                        " n.content AS content, n.course_id AS course_id, n.created_at AS created_at",
                        id=node_id).single()
        return self._row_to_node(rec) if rec else None

    def delete_node(self, node_id: int) -> bool:
        with self._drv.session() as s:
            res = s.run("MATCH (n:Kp {id: $id}) DETACH DELETE n RETURN count(n) AS c",
                        id=node_id).single()
        return (res["c"] if res else 0) > 0

    def create_relation(self, source_id: int, target_id: int, relation_type: str) -> dict:
        with _LOCK:
            with self._drv.session() as s:
                rid = s.run("MATCH ()-[r:RELATES]->() RETURN coalesce(max(r.id), 0) + 1 AS rid").single()["rid"]
                s.run("""
                    MATCH (a:Kp {id: $s}), (b:Kp {id: $t})
                    MERGE (a)-[r:RELATES {relation_type: $rt}]->(b)
                    ON CREATE SET r.id = $rid
                """, s=source_id, t=target_id, rt=relation_type, rid=rid)
            return {"id": rid, "source_id": source_id, "target_id": target_id,
                    "relation_type": relation_type}

    def delete_relation(self, relation_id: int) -> bool:
        with self._drv.session() as s:
            res = s.run("MATCH ()-[r:RELATES {id: $id}]->() DELETE r RETURN count(r) AS c",
                        id=relation_id).single()
        return (res["c"] if res else 0) > 0

    def relations_of(self, node_id: int, depth: int) -> List[dict]:
        d = max(1, min(int(depth or 1), 6))
        with self._drv.session() as s:
            recs = s.run(f"""
                MATCH path = (a:Kp {{id: $id}})-[:RELATES*1..{d}]->(b:Kp)
                WITH b, relationships(path)[0].relation_type AS rt,
                     min(length(path)) AS depth
                RETURN b.id AS id, b.name AS name, b.type AS type,
                       b.content AS content, b.course_id AS course_id, rt, depth
                ORDER BY depth
            """, id=node_id)
            return [{"node": self._row_to_node(rec), "relation_type": rec["rt"],
                     "depth": rec["depth"]} for rec in recs]

    def course_nodes(self, course_id: int) -> List[dict]:
        with self._drv.session() as s:
            recs = s.run("MATCH (n:Kp {course_id: $cid}) RETURN n.id AS id, n.name AS name,"
                         " n.type AS type, n.content AS content, n.course_id AS course_id,"
                         " n.created_at AS created_at ORDER BY n.id", cid=course_id)
            return [self._row_to_node(r) for r in recs]

    def edges_from(self, ids: set) -> List[dict]:
        """课程节点出发的边（与基线语义一致：source_id ∈ 课程节点集合）。"""
        if not ids:
            return []
        with self._drv.session() as s:
            recs = s.run("""
                MATCH (a:Kp)-[r:RELATES]->(b:Kp)
                WHERE a.id IN $ids
                RETURN r.id AS id, a.id AS source_id, b.id AS target_id,
                       r.relation_type AS relation_type
                ORDER BY id
            """, ids=list(ids))
            return [dict(rec) for rec in recs]

    def path_plan(self, source_id: int, target_id: int, max_hops: int) -> dict:
        hops = max(1, min(int(max_hops or 2), 10))
        with self._drv.session() as s:
            rec = s.run(f"""
                MATCH (a:Kp {{id: $s}}), (b:Kp {{id: $t}})
                MATCH p = shortestPath((a)-[:RELATES*..{hops}]-(b))
                RETURN [n IN nodes(p) | n.id] AS ids,
                       [r IN relationships(p) | {{
                           source_id: startNode(r).id,
                           target_id: endNode(r).id,
                           relation_type: r.relation_type}}] AS edges
            """, s=source_id, t=target_id).single()
        if not rec or not rec["ids"]:
            return {"path": [], "edges": [], "hops": 0}
        return {"path": rec["ids"], "edges": rec["edges"],
                "hops": max(len(rec["ids"]) - 1, 0)}


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


_store: Optional[Any] = None
_store_lock = threading.Lock()


def get_store():
    global _store
    with _store_lock:
        if _store is not None:
            return _store
        backend = (os.getenv("KG_BACKEND") or "auto").strip().lower()
        if backend == "memory":
            _store = MemoryStore()
        elif backend == "neo4j":
            _store = Neo4jStore(_uri(), os.getenv("NEO4J_USERNAME", "neo4j"),
                                os.getenv("NEO4J_PASSWORD", ""))
        else:  # auto：能连则连，不能连退回内存（保证任何环境可启动）
            _store = MemoryStore()
            if _GraphDatabase is not None and _uri():
                try:
                    _store = Neo4jStore(_uri(), os.getenv("NEO4J_USERNAME", "neo4j"),
                                        os.getenv("NEO4J_PASSWORD", ""))
                except Exception:
                    _store = MemoryStore()
        return _store


def backend_name() -> str:
    return get_store().name


def reset_store() -> None:
    """清空当前后端中本服务的数据（测试隔离用；CI/测试请配 KG_BACKEND=memory）。"""
    with _store_lock:
        get_store().reset()
