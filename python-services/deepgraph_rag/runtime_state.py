"""进程内运行态（本项目自有实现）

替代上游 `utils/persistence.py` / `utils/retriever_pipeline.py` 对 Streamlit
`session_state` 的依赖：8501 是纯 FastAPI 服务，没有脚本运行上下文，
而 Streamlit 的 `session_state` 在非 `streamlit run` 环境下行为未定义。

这里只保留检索链路真正需要的开关集合（HyDE / GraphRAG / 重排 / 上下文条数），
用普通 dict 承载，进程内共享、可被测试直接替换。
"""

from __future__ import annotations

import os
from typing import Any, Dict


def default_state() -> Dict[str, Any]:
    return {
        # HyDE 默认关（与平台检索入口默认值一致）；其余三项默认开
        "enable_hyde": os.getenv("DG_ENABLE_HYDE", "0") == "1",
        "enable_graph_rag": True,
        "enable_reranking": True,
        "max_contexts": 3,
        "answer_mode": "向量检索 + 知识图谱",
        # 当前文档与课程管道由 api_server 在每次请求前写入
        "current_source": "",
        "doc_names": [],
        "documents_loaded": False,
        "retrieval_pipeline": None,
    }


STATE: Dict[str, Any] = default_state()


def reset_state() -> None:
    """测试用：恢复到默认开关集合。"""
    STATE.clear()
    STATE.update(default_state())
