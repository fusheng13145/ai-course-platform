#!/usr/bin/env python3
"""Script to restore the complete build_graph.py file."""
import os

current_path = r"d:\dasanxia\LLM\DeepSeek-RAG-Chatbot-main\utils\build_graph.py"

# Read the current truncated file
with open(current_path, "r", encoding="utf-8") as f:
    current_content = f.read()

# The file is truncated at the preclean_text function.
# We need to append the rest of the file.
# Let's find where the truncation happened and append the missing content.

# The last line in the current file is:
# "            # 统计代码特征"
# We need to complete the preclean_text function and add all remaining functions.

missing_content = '''
            for pattern in code_patterns:
                if re.search(pattern, line, re.IGNORECASE):
                    code_match_count += 1
            if code_match_count < 2:
                cleaned_lines.append(line)
                continue
            else:
                continue  # 跳过代码行
        
        # 删除图片/表格引用（如 "如图1-1所示"、"见表2-3"）
        if re.search(r'[如见]图\d+[-_]\d+', line) or re.search(r'[如见]表\d+[-_]\d+', line):
            if not has_bold and not has_emphasis:
                continue
        
        # 删除参考文献引用（如 "[1]"、"［1］"）
        if re.match(r'^\\[\\d+\\]', line) or re.match(r'^［\\d+］', line):
            if not has_bold:
                continue
        
        # 删除URL链接
        if re.match(r'^https?://', line):
            continue
        
        # 删除纯符号行（如 "--------"、"******"）
        if re.match(r'^[\\*\\-\\=\\~\\#\\s]+$', line):
            if not has_bold:
                continue
        
        cleaned_lines.append(line)
    
    cleaned_text = "\\n".join(cleaned_lines)
    
    # ⚠️ 第二步：删除连续的重复行（PDF提取常见问题）
    lines_after_dedup = []
    prev_line = ""
    for line in cleaned_text.split("\\n"):
        if line.strip() != prev_line.strip():
            lines_after_dedup.append(line)
        prev_line = line
    cleaned_text = "\\n".join(lines_after_dedup)
    
    # ⚠️ 第三步：删除过长的段落（可能是PDF提取的乱码合并）
    # 如果某一行超过 2000 字符且不包含任何中文，可能是乱码
    final_lines = []
    for line in cleaned_text.split("\\n"):
        if len(line) > 2000 and not re.search(r'[\\u4e00-\\u9fff]', line):
            continue  # 跳过无中文的超长行（乱码）
        final_lines.append(line)
    cleaned_text = "\\n".join(final_lines)
    
    return cleaned_text


def normalize_entity_name(name):
    """实体名称归一化：将同义实体映射为标准名称"""
    if not name:
        return name
    name = name.strip()
    # 精确匹配
    if name in SYNONYM_DICT:
        return SYNONYM_DICT[name]
    # 大小写不敏感匹配（针对英文缩写）
    name_lower = name.lower()
    for key, value in SYNONYM_DICT.items():
        if key.lower() == name_lower:
            return value
    return name


def _render_empty_graph_html(title, message):
    """渲染空图谱占位HTML"""
    return f"""
    <!DOCTYPE html>
    <html style="height: 100%">
    <head>
        <meta charset="utf-8">
        <style>
            body {{
                height: 100%;
                margin: 0;
                display: flex;
                justify-content: center;
                align-items: center;
                background-color: #fafafa;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            }}
            .empty-state {{
                text-align: center;
                color: #999;
            }}
            .empty-state h3 {{
                font-size: 24px;
                margin-bottom: 10px;
                color: #666;
            }}
            .empty-state p {{
                font-size: 16px;
                color: #999;
            }}
        </style>
    </head>
    <body>
        <div class="empty-state">
            <h3>📭 {title}</h3>
            <p>{message}</p>
        </div>
    </body>
    </html>
    '''


def render_echarts_hierarchical_graph(tree_data):
    """
    渲染层次知识图谱（树形结构），样式与章节知识图谱一致。
    使用 ECharts 树图布局，包含工具栏、交互式图例、路径提示等。
    """
    import json

    # 🛡️ 空数据保护
    if tree_data is None or (isinstance(tree_data, dict) and not tree_data.get("name")):
        return _render_empty_graph_html("📂 层次知识图谱", "暂无层次图谱数据")

    # Neo4j 调色盘 (前5色按层级固定分配)
    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    def add_path(node, path_parts=None):
        """递归注入从根到当前节点的路径字符串"""
        if path_parts is None:
            path_parts = []
        node_name = node.get("name", "")
        current_path = path_parts + [node_name]
        node["_path"] = " → ".join(current_path)
        for child in node.get("children", []):
            add_path(child, current_path)

    def assign_colors(node, level=0):
        """按层级分配固定颜色（4级以上统一第5色）"""
        color_idx = min(level, 4)
        node["itemStyle"] = {{
            "color": neo4j_palette[color_idx],
            "borderColor": "#e0e0e0",
            "borderWidth": 1,
        }}
        for child in node.get("children", []):
            assign_colors(child, level + 1)

    # 深拷贝原始数据并注入颜色与路径
    colored_tree = json.loads(json.dumps(tree_data, ensure_ascii=False))
    assign_colors(colored_tree)
    add_path(colored_tree)

    html_template = """
    <!DOCTYPE html>
    <html style="height: 100%">
    <head>
        <meta charset="utf-8">
        <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
        <style>
            body {{
                height: 100%;
                margin: 0;
                background-color: #ffffff;
                overflow: hidden;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            }}
            .custom-title {{
                position: absolute;
                top: 25px;
                left: 35px;
                color: #444;
                pointer-events: none;
                z-index: 10;
            }}
            .custom-title h2 {{
                margin: 0;
                font-size: 24px;
                letter-spacing: 1px;
                font-weight: 700;
            }}
            .custom-title p {{
                margin: 8px 0 0 0;
                font-size: 14px;
                color: #666;
            }}
            .custom-legend {{
                position: absolute;
                bottom: 35px;
                left: 50%;
                transform: translateX(-50%);
                display: flex;
                gap: 22px;
                background: #ffffff;
                padding: 12px 28px;
                border-radius: 12px;
                border: 1px solid #e0e0e0;
                box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08);
                color: #444;
                font-size: 14px;
                z-index: 10;
                cursor: pointer;
            }}
            .legend-item {{
                display: flex;
                align-items: center;
                gap: 8px;
                user-select: none;
                transition: opacity 0.2s ease;
            }}
            .legend-item:hover {{ opacity: 0.7; }}
            .legend-color {{
                width: 14px;
                height: 14px;
                border-radius: 50%;
            }}
            .legend-item.inactive {{
                opacity: 0.35;
                filter: grayscale(100%);
            }}
            /* 工具栏样式 - 与章节知识图谱一致 */
            .toolbar {{
                position: absolute;
                top: 25px;
                right: 35px;
                display: flex;
                gap: 8px;
                z-index: 10;
                background: #ffffff;
                padding: 6px 10px;
                border-radius: 8px;
                border: 1px solid #e0e0e0;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
            }}
            .toolbar button {{
                background: none;
                border: 1px solid #e0e0e0;
                border-radius: 6px;
                padding: 6px 10px;
                cursor: pointer;
                font-size: 14px;
                color: #555;
                transition: all 0.2s ease;
                display: flex;
                align-items: center;
                gap: 4px;
            }}
            .toolbar button:hover {{
                background-color: #f0f0f0;
                border-color: #ccc;
                color: #333;
            }}
        </style>
    </head>
    <body>
        <div id="container" style="height: 100%; width: 100%;"></div>
       
        <div class="custom-title">
            <h2>知识图谱全景</h2>
            <p>层次结构自动可视化图谱</p>
        </div>

        <!-- 工具栏 - 与章节知识图谱一致 -->
        <div class="toolbar">
            <button onclick="zoomIn()" title="放大">🔍+</button>
            <button onclick="zoomOut()" title="缩小">🔍−</button>
            <button onclick="resetZoom()" title="重置视图">⟲ 重置</button>
            <button onclick="toggleFullscreen()" title="全屏">⛶ 全屏</button>
            <button onclick="refreshGraph()" title="刷新">🔄 刷新</button>
        </div>

        <div class="custom-legend" id="legend">
            <div class="legend-item" data-level="0">
                <div class="legend-color" style="background-color: #68BDF6;"></div>
                <span>核心知识点</span>
            </div>
            <div class="legend-item" data-level="1">
                <div class="legend-color" style="background-color: #6DCE9E;"></div>
                <span>一级章/讲</span>
            </div>
            <div class="legend-item" data-level="2">
                <div class="legend-color" style="background-color: #FFD86E;"></div>
                <span>二级节</span>
            </div>
            <div class="legend-item" data-level="3">
                <div class="legend-color" style="background-color: #FF9800;"></div>
                <span>三级知识点</span>
            </div>
            <div class="legend-item" data-level="4">
                <div class="legend-color" style="background-color: #E91E63;"></div>
                <span>深层细节</span>
            </div>
        </div>

        <script type="text/javascript">
            var dom = document.getElementById("container");
            var myChart = echarts.init(dom);
           
            var originalTreeData = JSON.parse(JSON.stringify(__JSON_DATA_PLACEHOLDER__));

            function markLevels(node, level) {{
                if (!node) return;
                node._level = level || 0;
                if (node.children && node.children.length > 0) {{
                    node.children.forEach(function(child) {{
                        markLevels(child, (level || 0) + 1);
                    }});
                }}
            }}

            function filterTreeByLevels(node, activeLevelsSet) {{
                if (!node) return null;
                var currentLevel = node._level !== undefined ? node._level : 0;
                var checkLevel = currentLevel >= 4 ? 4 : currentLevel;
               
                if (currentLevel !== 0 && !activeLevelsSet.has(checkLevel)) {{
                    return null;
                }}

                var newNode = JSON.parse(JSON.stringify(node));
               
                if (newNode.children && newNode.children.length > 0) {{
                    newNode.children = newNode.children
                        .map(function(child) {{ return filterTreeByLevels(child, activeLevelsSet); }})
                        .filter(Boolean);
                }}
                return newNode;
            }}

            markLevels(originalTreeData);

            var option = {{
                backgroundColor: '#ffffff',
                tooltip: {{
                    trigger: 'item',
                    backgroundColor: '#fff',
                    borderColor: '#e0e0e0',
                    borderWidth: 1,
                    textStyle: {{ color: '#444', fontSize: 14 }},
                    padding: [10, 14],
                    extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;',
                    formatter: function(params) {{
                        if (params.data && params.data._path) {{
                            return '📌 <b>' + params.data.name + '</b><br/>🔗 路径: ' + params.data._path;
                        }}
                        return params.name;
                    }}
                }},
                series: [{{
                    type: 'tree',
                    data: [originalTreeData],
                    layout: 'radial',
                    radius: '72%',
                    roam: true,
                    symbol: 'circle',
                    lineStyle: {{
                        width: 1.5,
                        curveness: 0.1,
                        color: '#A5ABB6',
                        opacity: 0.8
                    }},
                    label: {{
                        position: 'right',
                        align: 'left',
                        fontSize: 13,
                        color: '#444',
                        distance: 10,
                        rotate: 0,
                        overflow: 'truncate',
                        width: 180
                    }},
                    expandAndCollapse: true,
                    animationDuration: 800,
                    animationDurationUpdate: 500,
                    animationEasing: 'cubicOut',
                    initialTreeDepth: 3,
                    emphasis: {{
                        focus: 'ancestor',
                        lineStyle: {{
                            width: 2.5,
                            color: '#A5ABB6',
                            opacity: 1
                        }},
                        itemStyle: {{
                            shadowBlur: 12,
                            shadowColor: 'rgba(0,0,0,0.3)'
                        }}
                    }},
                    levels: [
                        {{
                            symbolSize: 52,
                            label: {{ fontSize: 16, fontWeight: 'bold', color: '#444' }}
                        }},
                        {{
                            symbolSize: 40,
                            label: {{ fontSize: 15, fontWeight: '600', color: '#444' }}
                        }},
                        {{
                            symbolSize: 32,
                            label: {{ fontSize: 14, color: '#444' }}
                        }},
                        {{
                            symbolSize: 26,
                            label: {{ fontSize: 13, color: '#444' }}
                        }},
                        {{
                            symbolSize: 20,
                            label: {{ fontSize: 13, color: '#666' }}
                        }}
                    ]
                }}]
            }};
           
            myChart.setOption(option);

            // 工具栏功能
            function zoomIn() {{
                var cur = myChart.getOption().series[0].zoom || 1;
                myChart.setOption({{ series: [{{ zoom: cur * 1.3 }}] }});
            }}
            function zoomOut() {{
                var cur = myChart.getOption().series[0].zoom || 1;
                myChart.setOption({{ series: [{{ zoom: cur / 1.3 }}] }});
            }}
            function resetZoom() {{
                myChart.dispatchAction({{ type: 'restore' }});
            }}
            function toggleFullscreen() {{
                if (!document.fullscreenElement) {{
                    document.documentElement.requestFullscreen();
                }} else {{
                    document.exitFullscreen();
                }}
            }}
            function refreshGraph() {{
                myChart.setOption(option, true);
            }}

            var legendItems = document.querySelectorAll('.legend-item');
            var activeLevels = new Set([0, 1, 2, 3, 4]);

            legendItems.forEach(function(item) {{
                item.addEventListener('click', function() {{
                    var level = parseInt(this.getAttribute('data-level'));
                   
                    if (activeLevels.has(level)) {{
                        activeLevels.delete(level);
                        this.classList.add('inactive');
                    }} else {{
                        activeLevels.add(level);
                        this.classList.remove('inactive');
                    }}

                    var filteredData = filterTreeByLevels(originalTreeData, activeLevels);
                    option.series[0].data = [filteredData];
                    myChart.setOption(option, true);
                }});
            }});

            window.addEventListener('resize', function() {{ myChart.resize(); }});
        </script>
    </body>
    </html>
    """

    return html_template.replace(
        "__JSON_DATA_PLACEHOLDER__", json.dumps(colored_tree, ensure_ascii=False)
    )


def render_echarts_global_graph(G):
    """
    使用 ECharts 渲染全局/章节知识图谱（力导向图）。
    支持工具栏、关系标签、箭头、交互式图例等。
    """
    import json

    # 🛡️ 空数据保护
    if G is None or G.number_of_nodes() == 0:
        return _render_empty_graph_html("🌐 知识图谱", "暂无图谱数据")

    # Neo4j 调色盘
    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    # 计算节点度数，用于节点大小映射
    degrees = dict(G.degree())
    max_degree = max(degrees.values()) if degrees else 1
    min_degree = min(degrees.values()) if degrees else 0

    # 构建节点数据
    nodes = []
    node_name_to_id = {{}}
    for i, node in enumerate(G.nodes()):
        node_name_to_id[str(node)] = i
        degree = degrees[node]
        # 节点大小：度数越大节点越大（范围 15-50）
        if max_degree > min_degree:
            size = 15 + 35 * (degree - min_degree) / (max_degree - min_degree)
        else:
            size = 25
        # 节点颜色：按度数分配
        color_idx = min(int(degree / max(1, max_degree) * 5), 4)
        nodes.append({{
            "id": i,
            "name": str(node),
            "symbolSize": size,
            "itemStyle": {{
                "color": neo4j_palette[color_idx],
                "borderColor": "#fff",
                "borderWidth": 2,
                "shadowBlur": 6,
                "shadowColor": "rgba(0,0,0,0.15)"
            }},
            "label": {{
                "show": True,
                "fontSize": 12,
                "color": "#444",
                "fontWeight": "bold"
            }},
            "degree": degree,
            "category": color_idx
        }})

    # 构建边数据
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
                edges.append({{
                    "source": source_id,
                    "target": target_id,
                    "label": {{
                        "show": True,
                        "formatter": label,
                        "fontSize": 10,
                        "color": "#666",
                        "backgroundColor": "rgba(255,255,255,0.8)",
                        "padding": [2, 6],
                        "borderRadius": 4,
                        "borderColor": "#e0e0e0",
                        "borderWidth": 1
                    }},
                    "lineStyle": {{
                        "color": "#A5ABB6",
                        "width": 1.5,
                        "opacity": 0.7,
                        "curveness": 0.1
                    }}
                }})

    # 构建图例分类
    categories = [
        {{"name": "核心概念", "itemStyle": {{"color": neo4j_palette[0]}}}},
        {{"name": "重要概念", "itemStyle": {{"color": neo4j_palette[1]}}}},
        {{"name": "一般概念", "itemStyle": {{"color": neo4j_palette[2]}}}},
        {{"name": "次要概念", "itemStyle": {{"color": neo4j_palette[3]}}}},
        {{"name": "细节概念", "itemStyle": {{"color": neo4j_palette[4]}}}},
    ]

    html_template = """
    <!DOCTYPE html>
    <html style="height: 100%">
    <head>
        <meta charset="utf-8">
        <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
        <style>
            body {{
                height: 100%;
                margin: 0;
                background-color: #ffffff;
                overflow: hidden;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            }}
            .toolbar {{
                position: absolute;
                top: 25px;
                right: 35px;
                display: flex;
                gap: 8px;
                z-index: 10;
                background: #ffffff;
                padding: 6px 10px;
                border-radius: 8px;
                border: 1px solid #e0e0e0;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
            }}
            .toolbar button {{
                background: none;
                border: 1px solid #e0e0e0;
                border-radius: 6px;
                padding: 6px 10px;
                cursor: pointer;
                font-size: 14px;
                color: #555;
                transition: all 0.2s ease;
                display: flex;
                align-items: center;
                gap: 4px;
            }}
            .toolbar button:hover {{
                background-color: #f0f0f0;
                border-color: #ccc;
                color: #333;
            }}
            .graph-title {{
                position: absolute;
                top: 25px;
                left: 35px;
                color: #444;
                pointer-events: none;
                z-index: 10;
            }}
            .graph-title h2 {{
                margin: 0;
                font-size: 22px;
                letter-spacing: 1px;
                font-weight: 700;
            }}
            .graph-title p {{
                margin: 6px 0 0 0;
                font-size: 13px;
                color: #888;
            }}
        </style>
    </head>
    <body>
        <div id="container" style="height: 100%; width: 100%;"></div>
        
        <div class="graph-title">
            <h2>知识图谱全景</h2>
            <p>实体关系自动可视化图谱</p>
        </div>

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

            var option = {{
                backgroundColor: '#ffffff',
                tooltip: {{
                    trigger: 'item',
                    backgroundColor: '#fff',
                    borderColor: '#e0e0e0',
                    borderWidth: 1,
                    textStyle: {{ color: '#444', fontSize: 13 }},
                    padding: [8, 12],
                    extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;',
                    formatter: function(params) {{
                        if (params.dataType === 'node') {{
                            return '<b>' + params.name + '</b><br/>关联数量: ' + (params.data.degree || 0);
                        }}
                        return params.name;
                    }}
                }},
                series: [{{
                    type: 'graph',
                    layout: 'force',
                    force: {{
                        repulsion: 500,
                        edgeLength: [150, 300],
                        gravity: 0.1,
                        friction: 0.1,
                        layoutAnimation: true
                    }},
                    roam: true,
                    draggable: true,
                    data: nodes,
                    edges: edges,
                    categories: categories,
                    focusNodeAdjacency: true,
                    edgeSymbol: ['none', 'arrow'],
                    edgeSymbolSize: [0, 10],
                    lineStyle: {{
                        color: 'source',
                        width: 1.5,
                        opacity: 0.7,
                        curveness: 0.1
                    }},
                    label: {{
                        show: true,
                        position: 'right',
                        fontSize: 12,
                        color: '#444',
                        fontWeight: 'bold'
                    }},
                    emphasis: {{
                        focus: 'adjacency',
                        lineStyle: {{
                            width: 3,
                            opacity: 1
                        }}
                    }},
                    animation: true,
                    animationDuration: 1000,
                    animationEasing: 'cubicOut'
                }}]
            }};

            myChart.setOption(option);

            function zoomIn() {{
                var cur = myChart.getOption().series[0].zoom || 1;
                myChart.setOption({{ series: [{{ zoom: cur * 1.3 }}] }});
            }}
            function zoomOut() {{
                var cur = myChart.getOption().series[0].zoom || 1;
                myChart.setOption({{ series: [{{ zoom: cur / 1.3 }}] }});
            }}
            function resetZoom() {{
                myChart.dispatchAction({{ type: 'restore' }});
            }}
            function toggleFullscreen() {{
                if (!document.fullscreenElement) {{
                    document.documentElement.requestFullscreen();
                }} else {{
                    document.exitFullscreen();
                }}
            }}
            function refreshGraph() {{
                myChart.setOption(option, true);
            }}

            window.addEventListener('resize', function() {{ myChart.resize(); }});
        </script>
    </body>
    </html>
    """

    html = html_template.replace("__NODES_DATA__", json.dumps(nodes, ensure_ascii=False))
    html = html.replace("__EDGES_DATA__", json.dumps(edges, ensure_ascii=False))
    html = html.replace("__CATEGORIES_DATA__", json.dumps(categories, ensure_ascii=False))
    return html


def render_learning_path_echarts(G, target_node=None):
    """
    使用 ECharts 渲染学习路径图（力导向图）。
    与 render_echarts_global_graph 样式一致。
    """
    import json

    if G is None or G.number_of_nodes() == 0:
        return _render_empty_graph_html("🗺️ 学习路径", "暂无学习路径数据")

    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    degrees = dict(G.degree())
    max_degree = max(degrees.values()) if degrees else 1
    min_degree = min(degrees.values()) if degrees else 0

    nodes = []
    node_name_to_id = {{}}
    for i, node in enumerate(G.nodes()):
        node_name_to_id[str(node)] = i
        degree = degrees[node]
        if max_degree > min_degree:
            size = 15 + 35 * (degree - min_degree) / (max_degree - min_degree)
        else:
            size = 25
        color_idx = min(int(degree / max(1, max_degree) * 5), 4)
        
        # 目标节点特殊标记
        is_target = target_node and str(node) == target_node
        item_style = {{
            "color": "#E91E63" if is_target else neo4j_palette[color_idx],
            "borderColor": "#fff",
            "borderWidth": 2,
            "shadowBlur": 12 if is_target else 6,
            "shadowColor": "rgba(233,30,99,0.4)" if is_target else "rgba(0,0,0,0.15)"
        }}
        
        nodes.append({{
            "id": i,
            "name": str(node),
            "symbolSize": 35 if is_target else size,
            "itemStyle": item_style,
            "label": {{
                "show": True,
                "fontSize": 13 if is_target else 11,
                "color": "#E91E63" if is_target else "#444",
                "fontWeight": "bold" if is_target else "normal"
            }},
            "degree": degree,
            "category": color_idx
        }})

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
                edges.append({{
                    "source": source_id,
                    "target": target_id,
                    "label": {{
                        "show": True,
                        "formatter": label,
                        "fontSize": 10,
                        "color": "#666",
                        "backgroundColor": "rgba(255,255,255,0.8)",
                        "padding": [2, 6],
                        "borderRadius": 4,
                        "borderColor": "#e0e0e0",
                        "borderWidth": 1
                    }},
                    "lineStyle": {{
                        "color": "#A5ABB6",
                        "width": 1.5,
                        "opacity": 0.7,
                        "curveness": 0.1
                    }}
                }})

    categories = [
        {{"name": "核心概念", "itemStyle": {{"color": neo4j_palette[0]}}}},
        {{"name": "重要概念", "itemStyle": {{"color": neo4j_palette[1]}}}},
        {{"name": "一般概念", "itemStyle": {{"color": neo4j_palette[2]}}}},
        {{"name": "次要概念", "itemStyle": {{"color": neo4j_palette[3]}}}},
        {{"name": "细节概念", "itemStyle": {{"color": neo4j_palette[4]}}}},
    ]

    html_template = """
    <!DOCTYPE html>
    <html style="height: 100%">
    <head>
        <meta charset="utf-8">
        <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
        <style>
            body {{
                height: 100%;
                margin: 0;
                background-color: #ffffff;
                overflow: hidden;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            }}
            .toolbar {{
                position: absolute;
                top: 25px;
                right: 35px;
                display: flex;
                gap: 8px;
                z-index: 10;
                background: #ffffff;
                padding: 6px 10px;
                border-radius: 8px;
                border: 1px solid #e0e0e0;
                box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
            }}
            .toolbar button {{
                background: none;
                border: 1px solid #e0e0e0;
                border-radius: 6px;
                padding: 6px 10px;
                cursor: pointer;
                font-size: 14px;
                color: #555;
                transition: all 0.2s ease;
                display: flex;
                align-items: center;
                gap: 4px;
            }}
            .toolbar button:hover {{
                background-color: #f0f0f0;
                border-color: #ccc;
                color: #333;
            }}
            .graph-title {{
                position: absolute;
                top: 25px;
                left: 35px;
                color: #444;
                pointer-events: none;
                z-index: 10;
            }}
            .graph-title h2 {{
                margin: 0;
                font-size: 22px;
                letter-spacing: 1px;
                font-weight: 700;
            }}
            .graph-title p {{
                margin: 6px 0 0 0;
                font-size: 13px;
                color: #888;
            }}
        </style>
    </head>
    <body>
        <div id="container" style="height: 100%; width: 100%;"></div>
        
        <div class="graph-title">
            <h2>学习路径图</h2>
            <p>知识依赖关系可视化</p>
        </div>

        <div class="toolbar">
            <button onclick="zoomIn()" title="放大">🔍+</button>
            <button onclick="
"""
