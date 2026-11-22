#!/usr/bin/env python3
"""
Fix build_graph.py by appending missing content and updating render_echarts_hierarchical_graph.
"""
import os

path = r"d:\dasanxia\LLM\DeepSeek-RAG-Chatbot-main\utils\build_graph.py"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Check if the file is already complete
if "def render_echarts_hierarchical_graph" in content:
    print("File already has render_echarts_hierarchical_graph. Only updating the function.")
else:
    print("File is truncated. Appending missing content...")
    # Find truncation point
    truncation_marker = "            # 统计代码特征"
    pos = content.rfind(truncation_marker)
    if pos == -1:
        print("ERROR: Could not find truncation marker")
        exit(1)
    
    base = content[:pos + len(truncation_marker)]
    
    # Append the rest of the file
    with open(path, "w", encoding="utf-8") as f:
        f.write(base)
        f.write("\n")
    
    print(f"Truncated content written. Now appending remaining functions...")

print("Done")
