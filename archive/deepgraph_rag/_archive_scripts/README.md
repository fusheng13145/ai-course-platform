# 已退役的一次性修补脚本

本目录存放 **已退役的一次性修补脚本，不再维护**。

这些脚本（`append_content*.py`、`create_append*.py`、`fix_build_graph.py`、
`fix_preclean.py`、`restore_build_graph.py`、`test1.py`）是早期开发阶段用来给外部
仓库文件打补丁的一次性工具：它们把目标路径**硬编码**为某台开发机上的绝对路径
（如 `d:\dasanxia\LLM\DeepSeek-RAG-Chatbot-main\utils\build_graph.py`），换个环境即
失效，也不再随主工程演进。

保留仅为追溯历史，请勿直接运行。仍在使用的脚本留在 `../scripts/`
（例如 `append_functions.py`）。
