#!/usr/bin/env python3
"""Append missing functions to build_graph.py"""
import os

path = r"d:\dasanxia\LLM\DeepSeek-RAG-Chatbot-main\utils\build_graph.py"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Check what's missing
if "def normalize_entity_name" in content:
    print("normalize_entity_name already exists")
else:
    print("Appending normalize_entity_name and remaining functions...")
    
    # The content to append - use a separate file approach
    append_path = r"d:\dasanxia\LLM\DeepSeek-RAG-Chatbot-main\append_content.py"
    with open(append_path, "r", encoding="utf-8") as f:
        append_content = f.read()
    
    with open(path, "a", encoding="utf-8") as f:
        f.write("\n")
        f.write(append_content)
    
    print("Done appending")

print("File length now:", len(open(path, "r", encoding="utf-8").read()))
