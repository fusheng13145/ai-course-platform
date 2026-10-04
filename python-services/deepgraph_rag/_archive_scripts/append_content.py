
def normalize_entity_name(name):
    """实体名称归一化：将同义实体映射为标准名称"""
    if not name:
        return name
    name = name.strip()
    if name in SYNONYM_DICT:
        return SYNONYM_DICT[name]
    name_lower = name.lower()
    for key, value in SYNONYM_DICT.items():
        if key.lower() == name_lower:
            return value
    return name


def _render_empty_graph_html(title, message):
    """渲染空图谱占位HTML"""
    return f"""<!DOCTYPE html>
<html style="height: 100%">
<head><meta charset="utf-8">
<style>
body {{ height: 100%; margin: 0; display: flex; justify-content: center; align-items: center; background-color: #fafafa; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }}
.empty-state {{ text-align: center; color: #999; }}
.empty-state h3 {{ font-size: 24px; margin-bottom: 10px; color: #666; }}
.empty-state p {{ font-size: 16px; color: #999; }}
</style></head>
<body><div class="empty-state"><h3>📭 {title}</h3><p>{message}</p></div></body>
</html>"""


def render_echarts_hierarchical_graph(tree_data):
    """
    渲染层次知识图谱（树形结构），样式与章节知识图谱一致。
    使用 ECharts 树图布局，包含工具栏、交互式图例、路径提示等。
    """
    import json

    if tree_data is None or (isinstance(tree_data, dict) and not tree_data.get("name")):
        return _render_empty_graph_html("📂 层次知识图谱", "暂无层次图谱数据")

    neo4j_palette = ["#68BDF6", "#6DCE9E", "#FFD86E", "#FF9800", "#E91E63"]

    def add_path(node, path_parts=None):
        if path_parts is None:
            path_parts = []
        node_name = node.get("name", "")
        current_path = path_parts + [node_name]
        node["_path"] = " → ".join(current_path)
        for child in node.get("children", []):
            add_path(child, current_path)

    def assign_colors(node, level=0):
        color_idx = min(level, 4)
        node["itemStyle"] = {"color": neo4j_palette[color_idx], "borderColor": "#e0e0e0", "borderWidth": 1}
        for child in node.get("children", []):
            assign_colors(child, level + 1)

    colored_tree = json.loads(json.dumps(tree_data, ensure_ascii=False))
    assign_colors(colored_tree)
    add_path(colored_tree)

    html_template = """<!DOCTYPE html>
<html style="height: 100%">
<head>
    <meta charset="utf-8">
    <script src="https://cdn.jsdelivr.net/npm/echarts/dist/echarts.min.js"></script>
    <style>
        body { height: 100%; margin: 0; background-color: #ffffff; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; }
        .custom-title { position: absolute; top: 25px; left: 35px; color: #444; pointer-events: none; z-index: 10; }
        .custom-title h2 { margin: 0; font-size: 24px; letter-spacing: 1px; font-weight: 700; }
        .custom-title p { margin: 8px 0 0 0; font-size: 14px; color: #666; }
        .custom-legend { position: absolute; bottom: 35px; left: 50%; transform: translateX(-50%); display: flex; gap: 22px; background: #ffffff; padding: 12px 28px; border-radius: 12px; border: 1px solid #e0e0e0; box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08); color: #444; font-size: 14px; z-index: 10; cursor: pointer; }
        .legend-item { display: flex; align-items: center; gap: 8px; user-select: none; transition: opacity 0.2s ease; }
        .legend-item:hover { opacity: 0.7; }
        .legend-color { width: 14px; height: 14px; border-radius: 50%; }
        .legend-item.inactive { opacity: 0.35; filter: grayscale(100%); }
        .toolbar { position: absolute; top: 25px; right: 35px; display: flex; gap: 8px; z-index: 10; background: #ffffff; padding: 6px 10px; border-radius: 8px; border: 1px solid #e0e0e0; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
        .toolbar button { background: none; border: 1px solid #e0e0e0; border-radius: 6px; padding: 6px 10px; cursor: pointer; font-size: 14px; color: #555; transition: all 0.2s ease; display: flex; align-items: center; gap: 4px; }
        .toolbar button:hover { background-color: #f0f0f0; border-color: #ccc; color: #333; }
    </style>
</head>
<body>
    <div id="container" style="height: 100%; width: 100%;"></div>
    <div class="custom-title"><h2>知识图谱全景</h2><p>层次结构自动可视化图谱</p></div>
    <div class="toolbar">
        <button onclick="zoomIn()" title="放大">🔍+</button>
        <button onclick="zoomOut()" title="缩小">🔍−</button>
        <button onclick="resetZoom()" title="重置视图">⟲ 重置</button>
        <button onclick="toggleFullscreen()" title="全屏">⛶ 全屏</button>
        <button onclick="refreshGraph()" title="刷新">🔄 刷新</button>
    </div>
    <div class="custom-legend" id="legend">
        <div class="legend-item" data-level="0"><div class="legend-color" style="background-color: #68BDF6;"></div><span>核心知识点</span></div>
        <div class="legend-item" data-level="1"><div class="legend-color" style="background-color: #6DCE9E;"></div><span>一级章/讲</span></div>
        <div class="legend-item" data-level="2"><div class="legend-color" style="background-color: #FFD86E;"></div><span>二级节</span></div>
        <div class="legend-item" data-level="3"><div class="legend-color" style="background-color: #FF9800;"></div><span>三级知识点</span></div>
        <div class="legend-item" data-level="4"><div class="legend-color" style="background-color: #E91E63;"></div><span>深层细节</span></div>
    </div>
    <script type="text/javascript">
        var dom = document.getElementById("container");
        var myChart = echarts.init(dom);
        var originalTreeData = JSON.parse(JSON.stringify(__JSON_DATA_PLACEHOLDER__));

        function markLevels(node, level) {
            if (!node) return;
            node._level = level || 0;
            if (node.children && node.children.length > 0) {
                node.children.forEach(function(child) { markLevels(child, (level || 0) + 1); });
            }
        }

        function filterTreeByLevels(node, activeLevelsSet) {
            if (!node) return null;
            var currentLevel = node._level !== undefined ? node._level : 0;
            var checkLevel = currentLevel >= 4 ? 4 : currentLevel;
            if (currentLevel !== 0 && !activeLevelsSet.has(checkLevel)) { return null; }
            var newNode = JSON.parse(JSON.stringify(node));
            if (newNode.children && newNode.children.length > 0) {
                newNode.children = newNode.children.map(function(child) { return filterTreeByLevels(child, activeLevelsSet); }).filter(Boolean);
            }
            return newNode;
        }

        markLevels(originalTreeData);

        var option = {
            backgroundColor: '#ffffff',
            tooltip: {
                trigger: 'item', backgroundColor: '#fff', borderColor: '#e0e0e0', borderWidth: 1,
                textStyle: { color: '#444', fontSize: 14 }, padding: [10, 14],
                extraCssText: 'box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08); border-radius: 8px;',
                formatter: function(params) {
                    if (params.data && params.data._path) {
                        return '📌 <b>' + params.data.name + '</b><br/>🔗 路径: ' + params.data._path;
                    }
                    return params.name;
                }
            },
            series: [{
                type: 'tree', data: [originalTreeData], layout: 'radial', radius: '72%', roam: true,
                symbol: 'circle',
                lineStyle: { width: 1.5, curveness: 0.1, color: '#A5ABB6', opacity: 0.8 },
                label: { position: 'right', align: 'left', fontSize: 13, color: '#444', distance: 10, rotate: 0, overflow: 'truncate', width: 180 },
                expandAndCollapse: true, animationDuration: 800, animationDurationUpdate: 500, animationEasing: 'cubicOut', initialTreeDepth: 3,
                emphasis: { focus: 'ancestor', lineStyle: { width: 2.5, color: '#A5ABB6', opacity: 1 }, itemStyle: { shadowBlur: 12, shadowColor: 'rgba(0,0,0,0.3)' } },
                levels: [
                    { symbolSize: 52, label: { fontSize: 16, fontWeight: 'bold', color: '#444' } },
                    { symbolSize: 40, label: { fontSize: 15, fontWeight: '600', color: '#444' } },
                    { symbolSize: 32, label: { fontSize: 14, color: '#444' } },
                    { symbolSize: 26, label: { fontSize: 13, color: '#444' } },
                    { symbolSize: 20, label: { fontSize: 13, color: '#666' } }
                ]
            }]
        };

        myChart.setOption(option);

        function zoomIn() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur * 1.3 }] }); }
        function zoomOut() { var cur = myChart.getOption().series[0].zoom || 1; myChart.setOption({ series: [{ zoom: cur / 1.3 }] }); }
        function resetZoom() { myChart.dispatchAction({ type: 'restore' }); }
        function toggleFullscreen() { if (!document.fullscreenElement) { document.documentElement.requestFullscreen(); } else { document.exitFullscreen(); } }
        function refreshGraph() { myChart.setOption(option, true); }

        var legendItems = document.querySelectorAll('.legend-item');
        var activeLevels = new Set([0, 1, 2, 3, 4]);
        legendItems.forEach(function(item) {
            item.addEventListener('click', function() {
                var level = parseInt(this.getAttribute('data-level'));
                if (activeLevels.has(level)) { activeLevels.delete(level); this.classList.add('inactive'); }
                else { activeLevels.add(level); this.classList.remove('inactive'); }
                var filteredData = filterTreeByLevels(originalTreeData, activeLevels);
                option.series[0].data = [filteredData];
                myChart.setOption(option, true);
            });
        });

        window.addEventListener('resize', function() { myChart.resize(); });
    </script>
</body>
</html>"""

    return html_template.replace("__JSON_DATA_PLACEHOLDER__", json.dumps(colored_tree, ensure_ascii=False))
