"""
IREM 候选推断单测
=================
锁定 `irem.infer_implicit_relations` 的**契约**（返回形状、规则可解释性、过滤与排序、
确定性与无副作用），而非"效果"——本项目无标注语料，抽取准确率未知，无法在此断言。

本地运行：
    python -m pytest python-services/kg_service -q
"""

import ast
import inspect

import irem

# 四个入门级知识点：彼此 content 不互相包含，只靠语料共现产出 RELATED
NODES = [
    {"id": 1, "name": "Python基础语法", "content": "包括变量、数据类型与运算符"},
    {"id": 2, "name": "函数与模块", "content": "包括模块化组织代码"},
    {"id": 3, "name": "数据结构", "content": "列表、字典与集合"},
    {"id": 4, "name": "文件与异常", "content": "包括异常处理"},
]

TEXTS = [
    "Python基础语法 是入门内容。函数与模块 用于组织代码。",
    "数据结构 与 文件与异常 是进阶主题。",
    "Python基础语法 与 数据结构 经常一起练习。",
]


def _by_pair(result, source_id, target_id):
    for item in result:
        if item["source_id"] == source_id and item["target_id"] == target_id:
            return item
    return None


def test_related_candidates_have_full_shape():
    """合成语料应产出 RELATED 候选，且字段齐全、分数与证据在约定范围内。"""
    result = irem.infer_implicit_relations(NODES, TEXTS)
    assert result, "四个知识点 + 共现语料应至少产出一条候选"
    assert any(item["relation_type"] == "RELATED" for item in result)

    for item in result:
        assert set(item) == {"source_id", "source_name", "target_id", "target_name",
                             "relation_type", "score", "evidence"}
        assert isinstance(item["score"], float)
        assert 0.0 <= item["score"] <= 1.0
        assert item["score"] == round(item["score"], 3)
        assert item["relation_type"] in {"INCLUDES", "PREREQUISITE", "RELATED"}
        assert item["source_id"] != item["target_id"]
        assert 0 < len(item["evidence"]) <= 80

    pairs = [(item["source_id"], item["target_id"]) for item in result]
    assert len(pairs) == len(set(pairs)), "同一有序对只能输出一次"

    related = _by_pair(result, 1, 3)
    assert related is not None
    assert related["relation_type"] == "RELATED"
    assert 0.3 <= related["score"] <= 0.6
    assert "Python基础语法" in related["evidence"]


def test_includes_when_content_mentions_another_node():
    """A 的 content 里写了 B 的名称 → (A, B, INCLUDES)，分数 ≥ 0.6。"""
    nodes = [
        {"id": 10, "name": "面向对象", "content": "包括封装、继承与多态"},
        {"id": 11, "name": "继承", "content": "子类复用父类结构"},
        {"id": 12, "name": "多态", "content": "同一接口的不同实现"},
    ]
    result = irem.infer_implicit_relations(nodes, ["面向对象 是重要范式。继承 与 多态 密切相关。"])
    item = _by_pair(result, 10, 11)
    assert item is not None
    assert item["relation_type"] == "INCLUDES"
    assert item["score"] >= 0.6
    assert item["evidence"] == "包括封装、继承与多态"
    assert item["source_name"] == "面向对象" and item["target_name"] == "继承"


def test_prerequisite_and_includes_are_not_duplicated():
    """互相包含的同一有序对只保留分数更高的一条（本数据下 PREREQUISITE 更高）。"""
    nodes = [
        {"id": 1, "name": "线性回归", "content": "属于监督学习"},
        {"id": 2, "name": "监督学习", "content": "包括线性回归"},
    ]
    texts = ["监督学习 是基础概念。线性回归 是具体模型。"]
    result = irem.infer_implicit_relations(nodes, texts)

    forward = _by_pair(result, 1, 2)
    backward = _by_pair(result, 2, 1)
    assert forward is not None and forward["relation_type"] == "INCLUDES"
    assert backward is not None and backward["relation_type"] == "PREREQUISITE"
    assert backward["score"] > 0.6
    # 该对已存在包含关系，因此不再额外产出 RELATED
    assert len([r for r in result if {r["source_id"], r["target_id"]} == {1, 2}]) == 2


def test_existing_pairs_filtered_but_reverse_still_allowed():
    """existing_pairs 命中即过滤；反向有序对不受影响。"""
    nodes = [
        {"id": 1, "name": "线性回归", "content": "属于监督学习"},
        {"id": 2, "name": "监督学习", "content": "包括线性回归"},
    ]
    texts = ["监督学习 是基础概念。线性回归 是具体模型。"]

    drop_forward = irem.infer_implicit_relations(nodes, texts, existing_pairs={(1, 2)})
    assert _by_pair(drop_forward, 1, 2) is None
    assert _by_pair(drop_forward, 2, 1) is not None

    drop_backward = irem.infer_implicit_relations(nodes, texts, existing_pairs=[[2, 1]])
    assert _by_pair(drop_backward, 2, 1) is None
    assert _by_pair(drop_backward, 1, 2) is not None


def test_min_score_filters_candidates():
    result = irem.infer_implicit_relations(NODES, TEXTS)
    assert result, "前置条件：默认参数下有候选"

    assert all(item["score"] >= 0.35
               for item in irem.infer_implicit_relations(NODES, TEXTS, min_score=0.35))
    assert irem.infer_implicit_relations(NODES, TEXTS, min_score=0.5) == []


def test_top_n_truncates_and_keeps_ranking():
    full = irem.infer_implicit_relations(NODES, TEXTS)
    assert len(full) >= 2

    rank_keys = [(-item["score"], item["source_id"], item["target_id"]) for item in full]
    assert rank_keys == sorted(rank_keys), "排序须为 score 降序 → source_id 升序 → target_id 升序"

    assert irem.infer_implicit_relations(NODES, TEXTS, top_n=1) == full[:1]
    assert irem.infer_implicit_relations(NODES, TEXTS, top_n=0) == []
    assert irem.infer_implicit_relations(NODES, TEXTS, top_n=None) == full


def test_determinism_across_calls_and_input_order():
    """相同输入两次调用完全一致；节点输入顺序不影响结果（内部按 id 归一）。"""
    first = irem.infer_implicit_relations(NODES, TEXTS)
    second = irem.infer_implicit_relations(NODES, TEXTS)
    assert first == second
    assert irem.infer_implicit_relations(list(reversed(NODES)), TEXTS) == first


def test_invalid_and_degenerate_inputs_return_empty():
    assert irem.infer_implicit_relations([], TEXTS) == []
    assert irem.infer_implicit_relations(NODES, []) == []
    assert irem.infer_implicit_relations([NODES[0]], TEXTS) == []
    assert irem.infer_implicit_relations(None, None) == []
    # 仅 1 个有效知识点（另一条缺 name）
    assert irem.infer_implicit_relations([{"id": 1, "name": "甲"}, {"id": 2, "name": ""}],
                                         ["甲 是概念。"]) == []
    # 两个知识点但语料零共现、content 也为空 → 无候选
    no_content = [{"id": 1, "name": "甲"}, {"id": 2, "name": "乙"}]
    assert irem.infer_implicit_relations(no_content, ["完全无关的一句话。"]) == []


def test_evidence_truncated_to_80_chars():
    long_content = "包括" + "填充" * 60 + "继承"
    nodes = [
        {"id": 1, "name": "面向对象", "content": long_content},
        {"id": 2, "name": "继承", "content": ""},
    ]
    result = irem.infer_implicit_relations(nodes, ["面向对象 与 继承 同属一章。"])
    item = _by_pair(result, 1, 2)
    assert item is not None and item["relation_type"] == "INCLUDES"
    assert len(item["evidence"]) == 80


def test_module_has_no_storage_or_network_side_effects():
    """模块只允许标准库导入：不得引入存储/驱动/网络库，确保离线纯函数。"""
    tree = ast.parse(inspect.getsource(irem))
    imported = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            imported.extend(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom):
            imported.append(node.module or "")

    roots = {name.split(".")[0] for name in imported if name}
    assert roots <= {"re", "__future__"}, f"只允许标准库导入，实际：{sorted(roots)}"
    forbidden = ("kg_store", "kg_service", "neo4j", "pymysql", "sqlite3", "requests", "httpx")
    for name in imported:
        assert not any(token in name for token in forbidden), name
