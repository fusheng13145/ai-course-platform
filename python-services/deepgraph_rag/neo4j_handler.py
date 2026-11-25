"""Neo4j 图谱存储（本项目自有实现，2026-11-25 阶段3「第三方内容清零」重写）

只提供课程知识图谱链路真正用到的五个动作：

* `insert_triplets_from_graph(G, source_doc, graph_type)` —— 三元组入库
* `clear_source_data(source_doc)`                          —— 按来源清理
* `query_graph_context(entities, source_doc)`             —— 实体关系上下文（GraphRAG）
* `query_learning_path(target, ...)`                      —— 学习路径反向追溯
* `driver`                                                —— 健康检查用的驱动入口

设计取舍：

1. **未配置即降级，不猜口令**：`config.neo4j_configured()` 为假时 `driver` 直接抛错，
   由调用方 catch 后按「图谱不可用」处理；绝不回落到任何默认口令。
2. **图模型只服务本平台**：节点 `(:Concept {name, source_doc})`，关系 `:RELATED`
   带 `label` / `graph_type` / `source_doc` 属性，一个来源一次 MERGE，天然幂等。
3. **本地图兜底**：Neo4j 不可达时学习路径回落到落盘的 NetworkX 图谱，
   保证「图谱能力」不是一句空话，也不会因为数据库没起就让接口整段失效。
"""

from __future__ import annotations

import re
from typing import Any, Dict, List, Optional

import config

_MAX_CONTEXT_LINES = 40


class Neo4jHandler:
    def __init__(self) -> None:
        self._driver: Optional[Any] = None

    # ------------------------------------------------------------ 连接
    @property
    def driver(self) -> Any:
        if not config.neo4j_configured():
            raise RuntimeError(
                "Neo4j 未配置（需 NEO4J_URI + NEO4J_PASSWORD；本项目不内置任何默认口令）"
            )
        if self._driver is None:
            from neo4j import GraphDatabase

            self._driver = GraphDatabase.driver(
                config.NEO4J_URI,
                auth=(config.NEO4J_USERNAME, config.NEO4J_PASSWORD),
            )
        return self._driver

    def close(self) -> None:
        if self._driver is not None:
            try:
                self._driver.close()
            finally:
                self._driver = None

    def available(self) -> bool:
        try:
            with self.driver.session() as session:
                session.run("RETURN 1").consume()
            return True
        except Exception:
            return False

    # ------------------------------------------------------------ 写入
    def insert_triplets_from_graph(
        self, graph: Any, source_doc: str, graph_type: str = "global"
    ) -> int:
        """把 NetworkX 图的三元组写入 Neo4j；返回实际提交的关系条数。"""
        if graph is None:
            return 0
        edges: List[Dict[str, str]] = []
        for u, v, data in graph.edges(data=True):
            source = str(u).strip()
            target = str(v).strip()
            if not source or not target:
                continue
            edges.append(
                {
                    "source": source,
                    "target": target,
                    "label": str((data or {}).get("label") or "相关").strip() or "相关",
                }
            )
        if not edges:
            return 0
        query = (
            "UNWIND $edges AS e "
            "MERGE (a:Concept {name: e.source, source_doc: $source}) "
            "MERGE (b:Concept {name: e.target, source_doc: $source}) "
            "MERGE (a)-[r:RELATED {label: e.label, source_doc: $source}]->(b) "
            "SET r.graph_type = $graph_type"
        )
        with self.driver.session() as session:
            session.run(query, edges=edges, source=source_doc, graph_type=graph_type).consume()
        return len(edges)

    def clear_source_data(self, source_doc: str) -> int:
        """删除某来源（含其章节派生子来源 `<source>_chapter_*`）的全部节点。"""
        if not source_doc:
            return 0
        query = (
            "MATCH (n:Concept) "
            "WHERE n.source_doc = $source OR n.source_doc STARTS WITH ($source + '_') "
            "DETACH DELETE n"
        )
        with self.driver.session() as session:
            summary = session.run(query, source=source_doc).consume()
        return int(getattr(summary.counters, "nodes_deleted", 0) or 0)

    # ------------------------------------------------------------ 读取
    def query_graph_context(self, entities: List[str], source_doc: str) -> str:
        """返回 `实体 --关系--> 实体` 文本行，供问答上下文拼接。"""
        names = [str(e).strip() for e in (entities or []) if str(e).strip()]
        if not names or not source_doc:
            return ""
        query = (
            "UNWIND $names AS name "
            "MATCH (a:Concept {name: name, source_doc: $source})-[r:RELATED]-(b:Concept) "
            "RETURN DISTINCT a.name AS source, r.label AS label, b.name AS target "
            "LIMIT $limit"
        )
        try:
            with self.driver.session() as session:
                records = list(
                    session.run(query, names=names, source=source_doc, limit=_MAX_CONTEXT_LINES)
                )
        except Exception:
            return ""
        lines: List[str] = []
        for record in records:
            source = record.get("source")
            target = record.get("target")
            if not source or not target:
                continue
            lines.append(f"{source} --{record.get('label') or '相关'}--> {target}")
        skipped = dict.fromkeys(lines)
        return "\n".join(list(skipped)[:_MAX_CONTEXT_LINES])

    def query_learning_path(
        self,
        target_entity: str,
        source_doc: Optional[str] = None,
        max_depth: int = 3,
        search_all_sources: bool = True,
    ) -> str:
        """以目标概念为终点做反向追溯，输出「先修 → 后续」的学习路径文本。"""
        target = (target_entity or "").strip()
        if not target:
            return ""
        depth = max(1, min(int(max_depth or 3), 6))
        records: List[Any] = []
        try:
            with self.driver.session() as session:
                if search_all_sources or not source_doc:
                    query = (
                        f"MATCH path=(pre:Concept)-[:RELATED*1..{depth}]->(t:Concept {{name: $target}}) "
                        "RETURN [n IN nodes(path) | n.name] AS names, "
                        "[r IN relationships(path) | r.label] AS labels, length(path) AS depth "
                        "ORDER BY depth DESC LIMIT 60"
                    )
                    records = list(session.run(query, target=target))
                else:
                    query = (
                        f"MATCH path=(pre:Concept {{source_doc: $source}})-[:RELATED*1..{depth}]"
                        "->(t:Concept {name: $target, source_doc: $source}) "
                        "RETURN [n IN nodes(path) | n.name] AS names, "
                        "[r IN relationships(path) | r.label] AS labels, length(path) AS depth "
                        "ORDER BY depth DESC LIMIT 60"
                    )
                    records = list(session.run(query, target=target, source=source_doc))
        except Exception:
            records = []
        if records:
            return self._format_paths(records)
        return self._local_learning_path(target, source_doc, depth)

    # ------------------------------------------------------------ 内部
    @staticmethod
    def _format_paths(records: List[Any]) -> str:
        lines: List[str] = ["学习路径（从先修到目标，Neo4j 反向追溯）："]
        seen: List[str] = []
        for record in records:
            names = list(record.get("names") or [])
            labels = list(record.get("labels") or [])
            if not names:
                continue
            steps: List[str] = [str(names[0])]
            for index, label in enumerate(labels):
                if index + 1 >= len(names):
                    break
                steps.append(f" --{label or '相关'}--> {names[index + 1]}")
            line = "".join(steps)
            if line not in seen:
                seen.append(line)
                lines.append(f"{len(seen)}. {line}")
        return "\n".join(lines)

    def _local_learning_path(self, target: str, source_doc: Optional[str], depth: int) -> str:
        """Neo4j 不可达时回落落盘图谱：BFS 反向收集祖先概念。"""
        from utils import persistence

        graphs: List[Any] = []
        if source_doc:
            graph = persistence.load_global_graph(source_doc)
            if graph is not None:
                graphs.append(graph)
        if not graphs:
            for name in persistence.list_documents():
                graph = persistence.load_global_graph(name)
                if graph is not None:
                    graphs.append(graph)
        for index, graph in enumerate(graphs, start=1):
            if target not in graph:
                continue
            levels: List[List[str]] = [[target]]
            visited = {target}
            current = [target]
            for _ in range(depth):
                nxt: List[str] = []
                for node in current:
                    neighbours = (
                        graph.predecessors(node)
                        if graph.is_directed()
                        else graph.neighbors(node)
                    )
                    for neighbour in neighbours:
                        if neighbour not in visited:
                            visited.add(neighbour)
                            nxt.append(str(neighbour))
                if not nxt:
                    break
                levels.append(nxt)
                current = nxt
            if len(levels) == 1:
                continue
            lines = [
                "学习路径（Neo4j 不可用，回落本地图谱）：",
                f"目标概念：{target}",
            ]
            for level_index in range(len(levels) - 1, 0, -1):
                lines.append(
                    f"第 {len(levels) - level_index} 层先修："
                    + "、".join(levels[level_index][:10])
                )
            return "\n".join(lines)
        return f"未在知识图谱中找到概念「{target}」的学习路径"


neo4j_db = Neo4jHandler()
