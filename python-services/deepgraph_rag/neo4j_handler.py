from neo4j import GraphDatabase
from config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD
import networkx as nx
import re
import json



class Neo4jGraphManager:
    def __init__(self):
        self.driver = GraphDatabase.driver(
            NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD)
        )

    def close(self):
        self.driver.close()

    def insert_triplets(self, triplets, source_doc):
        """将提取到的三元组存入 Neo4j，并绑定当前的文件来源标签"""
        with self.driver.session() as session:
            for item in triplets:
                head, relation, tail = item[:3]
                metadata = item[3] if len(item) > 3 and isinstance(item[3], dict) else {}
                query = """
                MERGE (h:Entity {name: $head})
                MERGE (t:Entity {name: $tail})
                MERGE (h)-[r:RELATION {type: $relation, label: $relation, source: $source_doc}]->(t)
                SET r.source_chunks = $source_chunks,
                    r.source_page = $source_page,
                    r.evidence = $evidence
                """
                session.run(
                    query,
                    head=head,
                    tail=tail,
                    relation=relation,
                    source_doc=source_doc,
                    source_chunks=metadata.get("source_chunks", "[]"),
                    source_page=metadata.get("source_page"),
                    evidence=metadata.get("evidence", ""),
                )

    def insert_triplets_from_graph(self, G, source_doc, graph_type="global"):
        """
        将 NetworkX 图中的所有边（三元组）存入 Neo4j。
        graph_type 用于标记图谱类型：'global' 或 'chapter'
        """
        triplets = []
        for u, v, data in G.edges(data=True):
            label = data.get("label", "相关")
            provenance = data.get("source_chunks", [])
            pages = sorted({item.get("page") for item in provenance if item.get("page") is not None})
            triplets.append(
                (
                    str(u),
                    str(label),
                    str(v),
                    {
                        "source_chunks": json.dumps(provenance, ensure_ascii=False),
                        "source_page": pages[0] if pages else None,
                        "evidence": provenance[0].get("text", "") if provenance else "",
                    },
                )
            )
        if triplets:
            self.insert_triplets(triplets, source_doc)

    def clear_source_data(self, source_doc):
        """清除指定文档来源的所有图谱数据（节点和关系）"""
        with self.driver.session() as session:
            # 先删除关系
            query = """
            MATCH ()-[r {source: $source_doc}]->()
            DELETE r
            """
            session.run(query, source_doc=source_doc)
            # 再删除孤立节点（没有关系的节点）
            query = """
            MATCH (n:Entity)
            WHERE NOT (n)--()
            DELETE n
            """
            session.run(query)

    def query_graph_context(self, entities, source_doc):
        """GraphRAG 检索：只匹配带有当前文档 source 标签的关联图谱"""
        unique_entities = list(dict.fromkeys(entity for entity in entities if entity))
        if not unique_entities:
            return ""

        # 一次查询全部实体，避免每个实体都单独进行一次 Neo4j 网络往返。
        query = """
        MATCH (n:Entity)-[r {source: $source_doc}]-(m:Entity)
        WHERE n.name IN $entities
        RETURN DISTINCT n.name AS source, r.label AS rel, m.name AS target
        LIMIT $limit
        """
        with self.driver.session() as session:
            results = session.run(
                query,
                entities=unique_entities,
                source_doc=source_doc,
                limit=len(unique_entities) * 10,
            )
            lines = [
                f"- {record['source']} ({record['rel']}) {record['target']}"
                for record in results
            ]
        return "\n".join(lines)

    def fetch_graph_as_networkx(self, source_doc, limit=150):
        """前端交互可视化：只拉取属于当前文档的图谱结构"""
        G = nx.DiGraph()
        with self.driver.session() as session:
            query = """
            MATCH (n:Entity)-[r {source: $source_doc}]->(m:Entity)
            RETURN n.name AS source, r.label AS rel, m.name AS target
            LIMIT $limit
            """
            results = session.run(query, source_doc=source_doc, limit=limit)
            for record in results:
                G.add_edge(record["source"], record["target"], label=record["rel"])
        return G

    def debug_list_all_sources(self):
        """调试：列出 Neo4j 中所有不同的 source 标签"""
        sources = []
        with self.driver.session() as session:
            query = """
            MATCH ()-[r:RELATION]->()
            RETURN DISTINCT r.source AS source
            """
            results = session.run(query)
            for record in results:
                sources.append(record["source"])
        return sources

    def debug_search_entity(self, keyword):
        """调试：搜索包含关键字的实体（keyword 为空时返回所有实体）"""
        entities = []
        with self.driver.session() as session:
            if keyword:
                query = """
                MATCH (n:Entity)
                WHERE n.name CONTAINS $keyword
                RETURN n.name AS name
                """
                results = session.run(query, keyword=keyword)
            else:
                query = """
                MATCH (n:Entity)
                RETURN n.name AS name
                """
                results = session.run(query)
            for record in results:
                entities.append(record["name"])
        return entities


    def _find_matching_entities(self, session, target_entity, source_doc):
        """
        多策略实体匹配：从精确匹配到模糊匹配，逐步放宽条件
        返回匹配到的实体名称列表
        """
        matched_entities = set()
        
        # 策略1: 精确匹配
        query1 = """
        MATCH (n:Entity)
        WHERE n.name = $target
        RETURN n.name AS name
        """
        results = session.run(query1, target=target_entity)
        for record in results:
            matched_entities.add(record["name"])
        
        # 策略2: CONTAINS 模糊匹配
        if not matched_entities:
            query2 = """
            MATCH (n:Entity)
            WHERE n.name CONTAINS $target
            RETURN n.name AS name
            """
            results = session.run(query2, target=target_entity)
            for record in results:
                matched_entities.add(record["name"])
        
        # 策略3: 分词子串匹配（将用户输入拆分为关键词，分别匹配）
        if not matched_entities:
            # 对中文/英文进行分词
            words = re.findall(r'[\u4e00-\u9fff]+|[A-Za-z]+', target_entity)
            for word in words:
                if len(word) < 2:
                    continue
                query3 = """
                MATCH (n:Entity)
                WHERE n.name CONTAINS $word
                RETURN n.name AS name
                """
                results = session.run(query3, word=word)
                for record in results:
                    matched_entities.add(record["name"])
        
        # 策略4: 如果用户输入包含常见缩写，尝试扩展匹配
        if not matched_entities:
            # 常见计算机缩写映射
            abbreviation_map = {
                "tcp": "传输控制协议", "udp": "用户数据报协议", "ip": "网际协议",
                "http": "超文本传输协议", "https": "超文本传输安全协议",
                "ftp": "文件传输协议", "smtp": "简单邮件传输协议",
                "dns": "域名系统", "dhcp": "动态主机配置协议",
                "arp": "地址解析协议", "icmp": "网际控制报文协议",
                "osi": "OSI参考模型", "tcp/ip": "TCP/IP协议簇",
                "cpu": "中央处理器", "gpu": "图形处理器",
                "ram": "随机存取存储器", "rom": "只读存储器",
                "db": "数据库", "sql": "结构化查询语言",
                "api": "应用程序接口", "ai": "人工智能",
                "ml": "机器学习", "dl": "深度学习",
                "cnn": "卷积神经网络", "rnn": "循环神经网络",
                "lstm": "长短期记忆网络", "nlp": "自然语言处理",
            }
            target_lower = target_entity.lower().strip()
            if target_lower in abbreviation_map:
                expanded = abbreviation_map[target_lower]
                query4 = """
                MATCH (n:Entity)
                WHERE n.name CONTAINS $expanded
                RETURN n.name AS name
                """
                results = session.run(query4, expanded=expanded)
                for record in results:
                    matched_entities.add(record["name"])
        
        return list(matched_entities)


    def _find_matching_entities_all_sources(self, session, target_entity):
        """
        跨所有文档源搜索匹配的实体（整本书搜索）
        不使用 source_doc 过滤，搜索所有 source 中的实体
        """
        matched_entities = set()
        
        # 策略1: 精确匹配（跨所有 source）
        query1 = """
        MATCH (n:Entity)
        WHERE n.name = $target
        RETURN DISTINCT n.name AS name
        """
        results = session.run(query1, target=target_entity)
        for record in results:
            matched_entities.add(record["name"])
        
        # 策略2: CONTAINS 模糊匹配（跨所有 source）
        if not matched_entities:
            query2 = """
            MATCH (n:Entity)
            WHERE n.name CONTAINS $target
            RETURN DISTINCT n.name AS name
            """
            results = session.run(query2, target=target_entity)
            for record in results:
                matched_entities.add(record["name"])
        
        # 策略3: 分词子串匹配（跨所有 source）
        if not matched_entities:
            words = re.findall(r'[\u4e00-\u9fff]+|[A-Za-z]+', target_entity)
            for word in words:
                if len(word) < 2:
                    continue
                query3 = """
                MATCH (n:Entity)
                WHERE n.name CONTAINS $word
                RETURN DISTINCT n.name AS name
                """
                results = session.run(query3, word=word)
                for record in results:
                    matched_entities.add(record["name"])
        
        # 策略4: 缩写扩展匹配（跨所有 source）
        if not matched_entities:
            abbreviation_map = {
                "tcp": "传输控制协议", "udp": "用户数据报协议", "ip": "网际协议",
                "http": "超文本传输协议", "https": "超文本传输安全协议",
                "ftp": "文件传输协议", "smtp": "简单邮件传输协议",
                "dns": "域名系统", "dhcp": "动态主机配置协议",
                "arp": "地址解析协议", "icmp": "网际控制报文协议",
                "osi": "OSI参考模型", "tcp/ip": "TCP/IP协议簇",
                "cpu": "中央处理器", "gpu": "图形处理器",
                "ram": "随机存取存储器", "rom": "只读存储器",
                "db": "数据库", "sql": "结构化查询语言",
                "api": "应用程序接口", "ai": "人工智能",
                "ml": "机器学习", "dl": "深度学习",
                "cnn": "卷积神经网络", "rnn": "循环神经网络",
                "lstm": "长短期记忆网络", "nlp": "自然语言处理",
            }
            target_lower = target_entity.lower().strip()
            if target_lower in abbreviation_map:
                expanded = abbreviation_map[target_lower]
                query4 = """
                MATCH (n:Entity)
                WHERE n.name CONTAINS $expanded
                RETURN DISTINCT n.name AS name
                """
                results = session.run(query4, expanded=expanded)
                for record in results:
                    matched_entities.add(record["name"])
        
        return list(matched_entities)


    def _query_learning_path_all_sources(self, session, entity_name, max_depth=3):
        """
        跨所有文档源查询学习路径（整本书搜索）
        不使用 source_doc 过滤，搜索所有 source 中的前置知识关系
        同时返回每个关系所属的 source_doc 信息
        """
        # 查询所有指向目标的前置路径（反向追溯，不限 source）
        query = """
        MATCH (n:Entity)-[r]->(m:Entity {name: $target})
        RETURN n.name AS source, r.label AS rel, m.name AS target, r.source AS source_doc, 1 AS depth
        """
        results = session.run(query, target=entity_name)
        edges = []
        for record in results:
            edges.append({
                "source": record["source"],
                "relation": record["rel"],
                "target": record["target"],
                "source_doc": record["source_doc"],
                "depth": record["depth"]
            })
        
        # 如果 max_depth > 1，递归查询更深层的前置知识
        if max_depth > 1:
            current_sources = list(set(e["source"] for e in edges))
            all_edges = list(edges)
            visited_nodes = set(current_sources)
            visited_nodes.add(entity_name)
            
            current_depth = 1
            while current_depth < max_depth and current_sources:
                next_sources = []
                for src in current_sources:
                    query_deep = """
                    MATCH (n:Entity)-[r]->(m:Entity {name: $current})
                    RETURN n.name AS source, r.label AS rel, r.source AS source_doc
                    """
                    results = session.run(query_deep, current=src)
                    for record in results:
                        prereq = record["source"]
                        if prereq not in visited_nodes:
                            visited_nodes.add(prereq)
                            next_sources.append(prereq)
                            all_edges.append({
                                "source": prereq,
                                "relation": record["rel"],
                                "target": src,
                                "source_doc": record["source_doc"],
                                "depth": current_depth + 1
                            })
                current_sources = next_sources
                current_depth += 1
            
            return all_edges
        
        return edges



    def _query_learning_path_single_cypher(self, session, entity_name, source_doc, max_depth=3):
        """
        使用 Neo4j 变长路径查询一次性获取所有前置知识（替代递归 BFS）
        查询所有指向目标实体的路径，深度为 1 到 max_depth
        """
        # 查询所有指向目标的前置路径（反向追溯）
        query = """
        MATCH path = (n:Entity)-[r {source: $source_doc}]->(m:Entity {name: $target})
        RETURN n.name AS source, r.label AS rel, m.name AS target, 1 AS depth
        """
        results = session.run(query, source_doc=source_doc, target=entity_name)
        edges = []
        for record in results:
            edges.append({
                "source": record["source"],
                "relation": record["rel"],
                "target": record["target"],
                "depth": record["depth"]
            })
        
        # 如果 max_depth > 1，使用变长路径查询更深层的前置知识
        if max_depth > 1:
            # 收集所有已找到的前置节点
            current_sources = list(set(e["source"] for e in edges))
            all_edges = list(edges)
            visited_nodes = set(current_sources)
            visited_nodes.add(entity_name)
            
            current_depth = 1
            while current_depth < max_depth and current_sources:
                next_sources = []
                for src in current_sources:
                    query_deep = """
                    MATCH (n:Entity)-[r {source: $source_doc}]->(m:Entity {name: $current})
                    RETURN n.name AS source, r.label AS rel
                    """
                    results = session.run(query_deep, source_doc=source_doc, current=src)
                    for record in results:
                        prereq = record["source"]
                        if prereq not in visited_nodes:
                            visited_nodes.add(prereq)
                            next_sources.append(prereq)
                            all_edges.append({
                                "source": prereq,
                                "relation": record["rel"],
                                "target": src,
                                "depth": current_depth + 1
                            })
                current_sources = next_sources
                current_depth += 1
            
            return all_edges
        
        return edges


    def _query_learning_path_from_local_graph(self, target_entity, source_doc, max_depth=3):
        """
        从本地 NetworkX 图谱中查找学习路径（Neo4j 不可用时的回退方案）
        """
        import streamlit as st
        
        kg = st.session_state.get("retrieval_pipeline", {}).get("knowledge_graph")
        if kg is None or kg.number_of_edges() == 0:
            return None
        
        from collections import deque
        
        # 查找匹配的节点
        matched_nodes = []
        target_lower = target_entity.lower()
        for node in kg.nodes():
            node_str = str(node)
            if (target_entity in node_str or node_str in target_entity or 
                target_lower in node_str.lower()):
                matched_nodes.append(node)
        
        if not matched_nodes:
            return None
        
        # BFS 反向查找前置知识
        visited = set()
        queue = deque()
        all_prereqs = {}  # node -> (depth, [path])
        all_edges = []
        
        for matched in matched_nodes:
            # 查找指向 matched 的节点（前置知识）
            for u, v, data in kg.edges(data=True):
                if v == matched:
                    prereq = u
                    rel = data.get("label", "相关")
                    if prereq not in visited:
                        visited.add(prereq)
                        queue.append((prereq, 1, [prereq]))
                        all_prereqs[prereq] = 1
                        all_edges.append({
                            "source": prereq,
                            "relation": rel,
                            "target": matched,
                            "depth": 1
                        })
        
        # BFS 递归
        while queue:
            current_node, depth, path = queue.popleft()
            if depth >= max_depth:
                continue
            
            for u, v, data in kg.edges(data=True):
                if v == current_node:
                    prereq = u
                    rel = data.get("label", "相关")
                    if prereq not in visited:
                        visited.add(prereq)
                        all_prereqs[prereq] = depth + 1
                        queue.append((prereq, depth + 1, path + [prereq]))
                        all_edges.append({
                            "source": prereq,
                            "relation": rel,
                            "target": current_node,
                            "depth": depth + 1
                        })
        
        return {
            "target": target_entity,
            "matched_nodes": matched_nodes,
            "prereqs": all_prereqs,
            "edges": all_edges
        }


    def query_learning_path(self, target_entity, source_doc=None, max_depth=3, search_all_sources=True):
        """
        优化版学习路径推荐：从目标知识点出发，反向查找所有前置知识点。
        
        核心改进：
        1. 多策略实体匹配（精确→模糊→分词→缩写扩展）
        2. 支持跨文档搜索（search_all_sources=True 时搜索整本书/所有文档）
        3. 使用变长路径查询替代递归 BFS，大幅提升效率
        4. 本地 NetworkX 图谱回退方案（Neo4j 不可用时自动切换）
        5. 返回结构化数据（含图谱数据），支持前端可视化
        
        参数:
            target_entity: 目标知识点名称
            source_doc: 文档源名称（为 None 或 search_all_sources=True 时搜索所有来源）
            max_depth: 递归深度
            search_all_sources: 是否跨所有文档源搜索（整本书搜索）
        
        返回结构化的学习路径文本。
        """
        import streamlit as st
        
        # 尝试从 Neo4j 查询
        neo4j_available = True
        try:
            with self.driver.session() as session:
                # 测试连接
                session.run("RETURN 1")
        except Exception:
            neo4j_available = False
        
        all_edges = []
        matched_entities = []
        
        if neo4j_available:
            try:
                with self.driver.session() as session:
                    # 第一步：多策略匹配实体
                    if source_doc and not search_all_sources:
                        # 仅在指定文档源中搜索
                        matched_entities = self._find_matching_entities(session, target_entity, source_doc)
                    else:
                        # 🚀 跨所有文档源搜索（整本书搜索）
                        matched_entities = self._find_matching_entities_all_sources(session, target_entity)
                    
                    if not matched_entities:
                        # 尝试在所有 source 中搜索（跨文档搜索）
                        query_all = """
                        MATCH (n:Entity)
                        WHERE n.name CONTAINS $target
                        RETURN DISTINCT n.name AS name
                        """
                        results = session.run(query_all, target=target_entity)
                        for record in results:
                            matched_entities.append(record["name"])
                    
                    if matched_entities:
                        # 第二步：对每个匹配到的实体查询学习路径
                        for entity in matched_entities:
                            if source_doc and not search_all_sources:
                                edges = self._query_learning_path_single_cypher(
                                    session, entity, source_doc, max_depth
                                )
                            else:
                                # 🚀 跨所有 source 查询学习路径
                                edges = self._query_learning_path_all_sources(
                                    session, entity, max_depth
                                )
                            all_edges.extend(edges)
            except Exception as e:
                print(f"Neo4j 查询失败，尝试本地回退: {e}")
                neo4j_available = False
        
        # 如果 Neo4j 不可用或没查到，尝试从本地图谱回退
        if not all_edges:
            local_result = self._query_learning_path_from_local_graph(
                target_entity, source_doc or "", max_depth
            )
            if local_result:
                matched_entities = local_result["matched_nodes"]
                all_edges = local_result["edges"]
        
        # 如果还是没找到，尝试用大模型推荐相关知识点
        if not all_edges:
            return self._generate_fallback_response(target_entity, source_doc or "")
        
        # 构建结构化输出
        result = self._build_learning_path_output(
            target_entity, matched_entities, all_edges, max_depth
        )
        
        return result



    def _generate_fallback_response(self, target_entity, source_doc):
        """
        当完全找不到匹配时，尝试用大模型推荐相关知识点
        """
        try:
            import requests
            from config import OLLAMA_API_URL, MODEL
            
            prompt = f"""用户想学习「{target_entity}」这个知识点，但在知识图谱中没有找到完全匹配的内容。
请根据你的专业知识，推荐 3-5 个与「{target_entity}」最相关的计算机知识点，
并说明它们之间的关系。这些推荐将帮助用户找到可搜索的替代知识点。

格式要求：
- 推荐知识点1（关联说明）
- 推荐知识点2（关联说明）
...

请直接输出推荐列表，不要有多余的解释。"""
            
            response = requests.post(
                OLLAMA_API_URL,
                json={"model": MODEL, "prompt": prompt, "stream": False,
                      "options": {"temperature": 0.3}}
            )
            advice = response.json().get("response", "")
            advice = re.sub(r"<think>.*?</think>", "", advice, flags=re.DOTALL).strip()
            
            result = f"## 🎯 学习目标：{target_entity}\n\n"
            result += "⚠️ 在知识图谱中未找到完全匹配的知识点。\n\n"
            result += "### 💡 您可能想了解以下相关知识点：\n\n"
            result += advice + "\n\n"
            result += "---\n"
            result += "💡 **提示**：您可以尝试输入上述推荐的知识点名称进行搜索，"
            result += "或先上传包含该知识点的文档并构建章节知识图谱。"
            return result
        except Exception:
            return (f"## 🎯 学习目标：{target_entity}\n\n"
                    f"⚠️ 未在知识图谱中找到「{target_entity}」的相关前置知识。\n\n"
                    f"💡 提示：该知识点可能为基础概念，或需要先上传包含该知识的文档并构建章节知识图谱。")


    def _build_learning_path_output(self, target_entity, matched_entities, all_edges, max_depth):
        """
        构建结构化的学习路径输出，同时返回图谱数据供前端可视化
        """
        # 按深度层级组织
        levels = {}
        prereq_nodes = set()
        
        for edge in all_edges:
            depth = edge["depth"]
            source = edge["source"]
            if depth not in levels:
                levels[depth] = set()
            levels[depth].add(source)
            prereq_nodes.add(source)
        
        # 构建文本输出
        result = f"## 🎯 学习目标：{target_entity}\n\n"
        
        if matched_entities and len(matched_entities) > 1:
            result += f"**🔍 匹配到 {len(matched_entities)} 个相关知识点：** "
            result += "、".join([f"`{e}`" for e in matched_entities]) + "\n\n"
        
        if levels:
            result += "### 📚 推荐学习路径（从基础到进阶）\n\n"
            for level in sorted(levels.keys()):
                level_labels = {
                    1: "📖 第一步：基础知识",
                    2: "🔗 第二步：进阶知识", 
                    3: "🔬 第三步：深入知识",
                    4: "🎯 第四步：高级知识"
                }
                label = level_labels.get(level, f"第{level}步")
                result += f"**{label}**\n"
                for node in sorted(levels[level]):
                    result += f"- ✅ {node}\n"
                result += "\n"
        
        # 显示知识依赖关系
        if all_edges:
            result += "### 🔗 知识依赖关系\n\n"
            # 按深度排序展示
            sorted_edges = sorted(all_edges, key=lambda x: (x["depth"], x["source"]))
            for item in sorted_edges:
                # 如果有 source_doc 信息（跨文档搜索），显示来源
                if "source_doc" in item and item["source_doc"]:
                    # 简化 source_doc 显示（去掉 _chapter_ 后缀）
                    doc_label = item["source_doc"].split("_chapter_")[0]
                    result += f"- `{item['source']}` —[{item['relation']}]→ `{item['target']}` 📖 `{doc_label}`\n"
                else:
                    result += f"- `{item['source']}` —[{item['relation']}]→ `{item['target']}`\n"

        
        # 用大模型生成学习建议
        try:
            import requests
            from config import OLLAMA_API_URL, MODEL
            
            # 按层级排序构建学习路径字符串
            prereq_names = []
            for level in sorted(levels.keys()):
                prereq_names.extend(sorted(levels[level]))
            
            prereq_str = " → ".join(prereq_names + [target_entity]) if prereq_names else target_entity
            
            advice_prompt = f"""你是一位计算机专业的教育专家。请根据以下学习路径，为想要学习「{target_entity}」的学生提供学习建议。

学习路径（从基础到目标）：
{prereq_str}

请给出：
1. 学习顺序建议（先学什么，再学什么）
2. 每个知识点的学习重点
3. 预计学习时长建议

请用简洁、鼓励的语气回答，控制在200字以内。"""
            
            response = requests.post(
                OLLAMA_API_URL,
                json={"model": MODEL, "prompt": advice_prompt, "stream": False}
            )
            advice = response.json().get("response", "")
            advice = re.sub(r"<think>.*?</think>", "", advice, flags=re.DOTALL).strip()
            if advice:
                result += f"\n### 🤖 AI 学习建议\n\n{advice}\n"
        except Exception:
            pass
        
        # 存储图谱数据到 session_state 供可视化使用
        try:
            import networkx as nx
            G = nx.DiGraph()
            G.add_node(target_entity)
            for edge in all_edges:
                G.add_edge(edge["source"], edge["target"], label=edge["relation"])
            st.session_state["learning_path_graph"] = G
            st.session_state["learning_path_target"] = target_entity
        except Exception:
            pass
        
        return result


# 全局单例
neo4j_db = Neo4jGraphManager()


