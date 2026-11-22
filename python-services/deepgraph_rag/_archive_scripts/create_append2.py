#!/usr/bin/env python3
"""Create append_content2.py with remaining missing functions"""
import os

content = r'''

def render_echarts_global_graph(G):
    """
    使用 ECharts 渲染全局/章节知识图谱（力导向图）。
    支持工具栏、关系标签、箭头、交互式图例等。
    """
    import json

    if G is None or G.number_of_nodes() == 0:
        return _render_empty_graph_html("🌐 知识图谱", "暂无图谱数据")

    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    degrees = dict(G.degree())
    max_degree = max(degrees.values()) if degrees else 1
    min_degree = min(degrees.values()) if degrees else 0

    nodes = []
    node_name_to_id = {}
    for i, node in enumerate(G.nodes()):
        node_name_to_id[str(node)] = i
        degree = degrees[node]
        if max_degree > min_degree:
            degree_range = max_degree - min_degree
            size = 15 + 35 * (degree - min_degree) / degree_range if degree_range else 25
        else:
            size = 25
        color_idx = min(int(degree / max(1, max_degree) * 5), 4)
        nodes.append({
            "id": i, "name": str(node), "symbolSize": size,
            "itemStyle": {"color": neo4j_palette[color_idx], "borderColor": "#fff", "borderWidth": 2, "shadowBlur": 6, "shadowColor": "rgba(0,0,0,0.15)"},
            "label": {"show": True, "fontSize": 12, "color": "#444", "fontWeight": "bold"},
            "degree": degree, "category": color_idx
        })

    edges = []
    edge_label_set = set()
    for u, v, data in G.edges(data=True):
        label = data.get("label", "相关") if data else "相关"
        source_id = node_name_to_id.get(str(u))
        target_id = node_name_to_id.get(str(v))
        if source_id is not None and target_id is not None:
            edge_key = f"{source_id}-{target_id}"
            if edge_key not in edge_label_set:
                edge_label_set.add(edge_key)
                edges.append({
                    "source": source_id, "target": target_id,
                    "label": {"show": True, "formatter": label, "fontSize": 10, "color": "#666",
                              "backgroundColor": "rgba(255,255,255,0.8)", "padding": [2, 6],
                              "borderRadius": 4, "borderColor": "#e0e0e0", "borderWidth": 1},
                    "lineStyle": {"color": "#A5ABB6", "width": 1.5, "opacity": 0.7, "curveness": 0.1}
                })

    categories = [
        {"name": "核心概念", "itemStyle": {"color": neo4j_palette[0]}},
        {"name": "重要概念", "itemStyle": {"color": neo4j_palette[1]}},
        {"name": "一般概念", "itemStyle": {"color": neo4j_palette[2]}},
        {"name": "次要概念", "itemStyle": {"color": neo4j_palette[3]}},
        {"name": "细节概念", "itemStyle": {"color": neo4j_palette[4]}},
    ]

    html_template = """<!DOCTYPE html>
<html style="height: 100%">
<head>
    <meta charset="utf-8">
    <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
    <style>
        body { height: 100%; margin: 0; background-color: #ffffff; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        .toolbar { position: absolute; top: 25px; right: 35px; display: flex; gap: 8px; z-index: 10; background: #ffffff; padding: 6px 10px; border-radius: 8px; border: 1px solid #e0e0e0; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
        .toolbar button { background: none; border: 1px solid #e0e0e0; border-radius: 6px; padding: 6px 10px; cursor: pointer; font-size: 14px; color: #555; transition: all 0.2s ease; display: flex; align-items: center; gap: 4px; }
        .toolbar button:hover { background-color: #f0f0f0; border-color: #ccc; color: #333; }
        .graph-title { position: absolute; top: 25px; left: 35px; color: #444; pointer-events: none; z-index: 10; }
        .graph-title h2 { margin: 0; font-size: 22px; letter-spacing: 1px; font-weight: 700; }
        .graph-title p { margin: 6px 0 0 0; font-size: 13px; color: #888; }
    </style>
</head>
<body>
    <div id="container" style="height: 100%; width: 100%;"></div>
    <div class="graph-title"><h2>知识图谱全景</h2><p>实体关系自动可视化图谱</p></div>
    <div class="toolbar">
        <button onclick="zoomIn()" title="放大">🔍+</button>
        <button onclick="zoomOut()" title="缩小">🔍−</button>
        <button onclick="resetZoom()" title="重置视图">⟲ 重置</button>
        <button onclick="toggleFullscreen()" title="全屏">⛶ 全屏</button>
        <button onclick="refreshGraph()" title="刷新">🔄 刷新</button>
    </div>
    <script type="text/javascript">
        var dom = document.getElementById("container");
        var myChart = echarts.init(dom);
        var nodes = __NODES_DATA__;
        var edges = __EDGES_DATA__;
        var categories = __CATEGORIES_DATA__;
        var option = {
            backgroundColor: '#ffffff',
            tooltip: { trigger: 'item', backgroundColor: '#fff', borderColor: '#e0e0e0', borderWidth: 1, textStyle: { color: '#444', fontSize: 13 }, padding: [8, 12], extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;', formatter: function(params) { if (params.dataType === 'node') { return '<b>' + params.name + '</b><br/>关联数量: ' + (params.data.degree || 0); } return params.name; } },
            series: [{ type: 'graph', layout: 'force', force: { repulsion: 500, edgeLength: [150, 300], gravity: 0.1, friction: 0.1, layoutAnimation: true }, roam: true, draggable: true, data: nodes, edges: edges, categories: categories, focusNodeAdjacency: true, edgeSymbol: ['none', 'arrow'], edgeSymbolSize: [0, 10], lineStyle: { color: 'source', width: 1.5, opacity: 0.7, curveness: 0.1 }, label: { show: true, position: 'right', fontSize: 12, color: '#444', fontWeight: 'bold' }, emphasis: { focus: 'adjacency', lineStyle: { width: 3, opacity: 1 } }, animation: true, animationDuration: 1000, animationEasing: 'cubicOut' }]
        };
        myChart.setOption(option);
        function zoomIn() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur * 1.3 }] }); }
        function zoomOut() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur / 1.3 }] }); }
        function resetZoom() { myChart.dispatchAction({ type: 'restore' }); }
        function toggleFullscreen() { if (!document.fullscreenElement) { document.documentElement.requestFullscreen(); } else { document.exitFullscreen(); } }
        function refreshGraph() { myChart.setOption(option, true); }
        window.addEventListener('resize', function() { myChart.resize(); });
    </script>
</body>
</html>"""

    html = html_template.replace("__NODES_DATA__", json.dumps(nodes, ensure_ascii=False))
    html = html.replace("__EDGES_DATA__", json.dumps(edges, ensure_ascii=False))
    html = html.replace("__CATEGORIES_DATA__", json.dumps(categories, ensure_ascii=False))
    return html


def render_learning_path_echarts(G, target_node=None):
    """使用 ECharts 渲染学习路径图（力导向图）。"""
    import json

    if G is None or G.number_of_nodes() == 0:
        return _render_empty_graph_html("🗺️ 学习路径", "暂无学习路径数据")

    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    degrees = dict(G.degree())
    max_degree = max(degrees.values()) if degrees else 1
    min_degree = min(degrees.values()) if degrees else 0

    nodes = []
    node_name_to_id = {}
    for i, node in enumerate(G.nodes()):
        node_name_to_id[str(node)] = i
        degree = degrees[node]
        if max_degree > min_degree:
            degree_range = max_degree - min_degree
            size = 15 + 35 * (degree - min_degree) / degree_range if degree_range else 25
        else:
            size = 25
        color_idx = min(int(degree / max(1, max_degree) * 5), 4)
        is_target = target_node and str(node) == target_node
        item_style = {"color": "#E91E63" if is_target else neo4j_palette[color_idx], "borderColor": "#fff", "borderWidth": 2, "shadowBlur": 12 if is_target else 6, "shadowColor": "rgba(233,30,99,0.4)" if is_target else "rgba(0,0,0,0.15)"}
        nodes.append({
            "id": i, "name": str(node), "symbolSize": 35 if is_target else size, "itemStyle": item_style,
            "label": {"show": True, "fontSize": 13 if is_target else 11, "color": "#E91E63" if is_target else "#444", "fontWeight": "bold" if is_target else "normal"},
            "degree": degree, "category": color_idx
        })

    edges = []
    edge_label_set = set()
    for u, v, data in G.edges(data=True):
        label = data.get("label", "相关") if data else "相关"
        source_id = node_name_to_id.get(str(u))
        target_id = node_name_to_id.get(str(v))
        if source_id is not None and target_id is not None:
            edge_key = f"{source_id}-{target_id}"
            if edge_key not in edge_label_set:
                edge_label_set.add(edge_key)
                edges.append({
                    "source": source_id, "target": target_id,
                    "label": {"show": True, "formatter": label, "fontSize": 10, "color": "#666",
                              "backgroundColor": "rgba(255,255,255,0.8)", "padding": [2, 6],
                              "borderRadius": 4, "borderColor": "#e0e0e0", "borderWidth": 1},
                    "lineStyle": {"color": "#A5ABB6", "width": 1.5, "opacity": 0.7, "curveness": 0.1}
                })

    categories = [
        {"name": "核心概念", "itemStyle": {"color": neo4j_palette[0]}},
        {"name": "重要概念", "itemStyle": {"color": neo4j_palette[1]}},
        {"name": "一般概念", "itemStyle": {"color": neo4j_palette[2]}},
        {"name": "次要概念", "itemStyle": {"color": neo4j_palette[3]}},
        {"name": "细节概念", "itemStyle": {"color": neo4j_palette[4]}},
    ]

    html_template = """<!DOCTYPE html>
<html style="height: 100%">
<head>
    <meta charset="utf-8">
    <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
    <style>
        body { height: 100%; margin: 0; background-color: #ffffff; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        .toolbar { position: absolute; top: 25px; right: 35px; display: flex; gap: 8px; z-index: 10; background: #ffffff; padding: 6px 10px; border-radius: 8px; border: 1px solid #e0e0e0; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
        .toolbar button { background: none; border: 1px solid #e0e0e0; border-radius: 6px; padding: 6px 10px; cursor: pointer; font-size: 14px; color: #555; transition: all 0.2s ease; display: flex; align-items: center; gap: 4px; }
        .toolbar button:hover { background-color: #f0f0f0; border-color: #ccc; color: #333; }
        .graph-title { position: absolute; top: 25px; left: 35px; color: #444; pointer-events: none; z-index: 10; }
        .graph-title h2 { margin: 0; font-size: 22px; letter-spacing: 1px; font-weight: 700; }
        .graph-title p { margin: 6px 0 0 0; font-size: 13px; color: #888; }
    </style>
</head>
<body>
    <div id="container" style="height: 100%; width: 100%;"></div>
    <div class="graph-title"><h2>学习路径图</h2><p>知识依赖关系可视化</p></div>
    <div class="toolbar">
        <button onclick="zoomIn()" title="放大">🔍+</button>
        <button onclick="zoomOut()" title="缩小">🔍−</button>
        <button onclick="resetZoom()" title="重置视图">⟲ 重置</button>
        <button onclick="toggleFullscreen()" title="全屏">⛶ 全屏</button>
        <button onclick="refreshGraph()" title="刷新">🔄 刷新</button>
    </div>
    <script type="text/javascript">
        var dom = document.getElementById("container");
        var myChart = echarts.init(dom);
        var nodes = __NODES_DATA__;
        var edges = __EDGES_DATA__;
        var categories = __CATEGORIES_DATA__;
        var option = {
            backgroundColor: '#ffffff',
            tooltip: { trigger: 'item', backgroundColor: '#fff', borderColor: '#e0e0e0', borderWidth: 1, textStyle: { color: '#444', fontSize: 13 }, padding: [8, 12], extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;', formatter: function(params) { if (params.dataType === 'node') { return '<b>' + params.name + '</b><br/>关联数量: ' + (params.data.degree || 0); } return params.name; } },
            series: [{ type: 'graph', layout: 'force', force: { repulsion: 500, edgeLength: [150, 300], gravity: 0.1, friction: 0.1, layoutAnimation: true }, roam: true, draggable: true, data: nodes, edges: edges, categories: categories, focusNodeAdjacency: true, edgeSymbol: ['none', 'arrow'], edgeSymbolSize: [0, 10], lineStyle: { color: 'source', width: 1.5, opacity: 0.7, curveness: 0.1 }, label: { show: true, position: 'right', fontSize: 12, color: '#444', fontWeight: 'bold' }, emphasis: { focus: 'adjacency', lineStyle: { width: 3, opacity: 1 } }, animation: true, animationDuration: 1000, animationEasing: 'cubicOut' }]
        };
        myChart.setOption(option);
        function zoomIn() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur * 1.3 }] }); }
        function zoomOut() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur / 1.3 }] }); }
        function resetZoom() { myChart.dispatchAction({ type: 'restore' }); }
        function toggleFullscreen() { if (!document.fullscreenElement) { document.documentElement.requestFullscreen(); } else { document.exitFullscreen(); } }
        function refreshGraph() { myChart.setOption(option, true); }
        window.addEventListener('resize', function() { myChart.resize(); });
    </script>
</body>
</html>"""

    html = html_template.replace("__NODES_DATA__", json.dumps(nodes, ensure_ascii=False))
    html = html.replace("__EDGES_DATA__", json.dumps(edges, ensure_ascii=False))
    html = html.replace("__CATEGORIES_DATA__", json.dumps(categories, ensure_ascii=False))
    return html


def build_chapter_knowledge_graph(chapter_name, chapter_text, progress_callback=None):
    """
    构建章节知识图谱。
    使用 LLM 从章节文本中提取三元组关系，构建知识图谱。
    """
    import streamlit as st

    if progress_callback:
        progress_callback("start", f"开始构建【{chapter_name}】的知识图谱", f"章节文本长度: {len(chapter_text)} 字符")

    # 文本预清洗
    if progress_callback:
        progress_callback("preclean", "正在清洗文本...", f"原始文本长度: {len(chapter_text)}")
    cleaned_text = preclean_text(chapter_text)
    if progress_callback:
        progress_callback("preclean", "文本清洗完成", f"清洗后文本长度: {len(cleaned_text)}")

    # 使用 LLM 提取三元组
    if progress_callback:
        progress_callback("extract", "正在使用 LLM 提取知识三元组...", "")

    # 构建提示词
    prompt = f"""请从以下文本中提取关键的知识三元组（实体-关系-实体），用于构建知识图谱。

要求：
1. 提取重要的概念、术语、定义、功能、特性等实体及其关系
2. 关系词请使用以下标准关系之一：包含、属于、定义、功能、具有、导致、依赖、相关、应用、示例、对比、优势、劣势
3. 每个三元组格式为：(实体1, 关系, 实体2)
4. 尽量提取高质量、有意义的三元组
5. 避免提取过于宽泛或模糊的关系

文本内容：
{cleaned_text[:3000]}

请以JSON格式输出，格式为：{{"triples": [["实体1", "关系", "实体2"], ...]}}
"""

    try:
        response = call_llm(prompt)
        if progress_callback:
            progress_callback("parse", "正在解析 LLM 返回结果...", "")

        # 尝试解析 JSON
        triples = []
        try:
            result = json.loads(response)
            triples = result.get("triples", [])
        except:
            # 尝试从文本中提取 JSON
            import re
            json_match = re.search(r'\{.*"triples".*\}', response, re.DOTALL)
            if json_match:
                try:
                    result = json.loads(json_match.group())
                    triples = result.get("triples", [])
                except:
                    pass

        if not triples:
            if progress_callback:
                progress_callback("error", "未能从 LLM 响应中提取到三元组", "请检查 LLM 服务是否正常")
            return None

        # 构建 NetworkX 图
        G = nx.Graph()
        for subj, rel, obj in triples:
            subj = normalize_entity_name(subj.strip())
            obj = normalize_entity_name(obj.strip())
            rel = normalize_relation(rel.strip())
            if subj and obj and rel:
                G.add_edge(subj, obj, label=rel)

        # 合并概念变体
        G = merge_concept_variants_in_graph(G)

        if progress_callback:
            progress_callback("complete", f"知识图谱构建完成", f"包含 {G.number_of_nodes()} 个实体, {G.number_of_edges()} 条关系")

        return G

    except Exception as e:
        if progress_callback:
            progress_callback("error", f"构建知识图谱时出错: {str(e)}", "")
        return None
'''

path = r"d:\dasanxia\LLM\DeepSeek-RAG-Chatbot-main\append_content2.py"
with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print(f"Written {len(content)} chars to append_content2.py")
