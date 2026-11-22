#!/usr/bin/env python3
"""Fix the truncated preclean_text function in build_graph.py"""
import os

path = r"d:\dasanxia\LLM\DeepSeek-RAG-Chatbot-main\utils\build_graph.py"

with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Find the truncation point
truncation_marker = "            # 统计代码特征"
pos = content.find(truncation_marker)
if pos == -1:
    print("ERROR: Could not find truncation marker")
    exit(1)

# Find the next function after this
next_func = content.find("\ndef normalize_entity_name")
if next_func == -1:
    print("ERROR: Could not find normalize_entity_name")
    exit(1)

# The content between truncation and next function is the incomplete preclean_text
# We need to replace it with the complete version
before = content[:pos + len(truncation_marker)]
after = content[next_func:]

# The complete rest of preclean_text
complete_rest = r'''
            code_match_count = 0
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
        if re.match(r'^\[\d+\]', line) or re.match(r'^［\d+］', line):
            if not has_bold:
                continue
        
        # 删除URL链接
        if re.match(r'^https?://', line):
            continue
        
        # 删除纯符号行（如 "--------"、"******"）
        if re.match(r'^[\*\-=\~\#\s]+$', line):
            if not has_bold:
                continue
        
        cleaned_lines.append(line)
    
    cleaned_text = "\n".join(cleaned_lines)
    
    # ⚠️ 第二步：删除连续的重复行（PDF提取常见问题）
    lines_after_dedup = []
    prev_line = ""
    for line in cleaned_text.split("\n"):
        if line.strip() != prev_line.strip():
            lines_after_dedup.append(line)
        prev_line = line
    cleaned_text = "\n".join(lines_after_dedup)
    
    # ⚠️ 第三步：删除过长的段落（可能是PDF提取的乱码合并）
    final_lines = []
    for line in cleaned_text.split("\n"):
        if len(line) > 2000 and not re.search(r'[\u4e00-\u9fff]', line):
            continue
        final_lines.append(line)
    cleaned_text = "\n".join(final_lines)
    
    return cleaned_text


'''

new_content = before + complete_rest + after

with open(path, "w", encoding="utf-8") as f:
    f.write(new_content)

print(f"Fixed! File length: {len(new_content)}")
print("preclean_text ends with return:", new_content[new_content.find("def preclean_text"):new_content.find("def normalize_entity_name")].strip().endswith("return cleaned_text"))
