"""
统一的 LLM 客户端，支持切换 Ollama 本地模型和 DeepSeek API。
支持通过环境变量 LLM_PROVIDER 或运行时 set_provider() 切换。
- "ollama": 使用本地 Ollama 模型（默认）
- "deepseek": 使用 DeepSeek API
"""

import os
import re
import json
import requests
import ssl
import urllib3
from config import OLLAMA_API_URL, MODEL

# 复用 HTTPS 连接，减少连续 DeepSeek 请求的建连开销。
_http_session = requests.Session()

# ============================================================
# SSL 兼容性配置
# 解决 Windows 上 SSL: UNEXPECTED_EOF_WHILE_READING 错误
# ============================================================
# 方式1：创建自定义 SSL Context（跳过部分严格校验）
try:
    _ssl_context = ssl.create_default_context()
    _ssl_context.check_hostname = False
    _ssl_context.verify_mode = ssl.CERT_NONE
except Exception:
    _ssl_context = None

# 方式2：禁用 urllib3 的 SSL 警告
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ============================================================
# DeepSeek API 配置
# ============================================================
DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")  # deepseek-chat = DeepSeek-V4

# LLM 提供商选择（默认从环境变量读取，可通过 set_provider() 运行时修改）
_llm_provider = os.getenv("LLM_PROVIDER", "ollama").lower()


def set_provider(provider):
    """
    运行时切换 LLM 提供商。
    
    参数:
        provider: "ollama" 或 "deepseek"
    """
    global _llm_provider
    provider = provider.lower().strip()
    if provider not in ("ollama", "deepseek"):
        raise ValueError(f"不支持的 LLM 提供商: {provider}，仅支持 'ollama' 或 'deepseek'")
    _llm_provider = provider


def get_provider():
    """获取当前 LLM 提供商"""
    return _llm_provider


def call_llm(prompt, system_prompt="", temperature=0.1, max_tokens=4096):
    """
    统一的 LLM 调用函数。
    
    参数:
        prompt: 用户提示词
        system_prompt: 系统提示词（仅 DeepSeek API 使用）
        temperature: 温度参数
        max_tokens: 最大生成 token 数
    
    返回:
        str: 模型生成的文本
    """
    if _llm_provider == "deepseek":
        return _call_deepseek_api(prompt, system_prompt, temperature, max_tokens)
    else:
        return _call_ollama(prompt, system_prompt, temperature, max_tokens)


def _call_deepseek_api(prompt, system_prompt="", temperature=0.1, max_tokens=4096):
    """调用 DeepSeek API（带 SSL 兼容性和自动重试）"""
    if not DEEPSEEK_API_KEY:
        raise ValueError(
            "DeepSeek API Key 未配置！请在 .env 文件中设置 DEEPSEEK_API_KEY=your_key_here"
        )
    
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})
    
    headers = {
        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "model": DEEPSEEK_MODEL,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": False
    }
    
    # 构建请求参数
    request_kwargs = {
        "url": DEEPSEEK_API_URL,
        "headers": headers,
        "json": payload,
        "timeout": 180,
        "verify": False,  # 禁用 SSL 证书验证（解决 Windows SSL EOF 错误）
    }
    
    # 注意：不要将 SSLContext 对象传给 verify 参数，
    # requests 的 verify 参数只接受 bool 或 str（CA bundle 路径）
    # SSLContext 对象会导致 "stat: path should be string, bytes, os.PathLike or integer, not SSLContext" 错误
    
    # 自动重试机制（最多重试 3 次）
    max_retries = 3
    last_error = None
    
    for attempt in range(max_retries):
        try:
            response = _http_session.post(**request_kwargs)
            response.raise_for_status()
            result = response.json()
            print(f'result:{result}')
            print('*' * 60)
            return result["choices"][0]["message"]["content"].strip()
        except requests.exceptions.SSLError as e:
            last_error = e
            print(f"DeepSeek API SSL 连接失败 (尝试 {attempt+1}/{max_retries}): {e}")
            if attempt < max_retries - 1:
                import time
                time.sleep(2)  # 重试前等待 2 秒
                # 下次重试时尝试不同的 SSL 策略
                request_kwargs["verify"] = False
        except requests.exceptions.RequestException as e:
            last_error = e
            print(f"DeepSeek API 请求失败 (尝试 {attempt+1}/{max_retries}): {e}")
            if hasattr(e, 'response') and e.response is not None:
                print(f"响应内容: {e.response.text}")
            if attempt < max_retries - 1:
                import time
                time.sleep(2)
    
    # 所有重试都失败
    raise last_error


def _call_ollama(prompt, system_prompt="", temperature=0.1, max_tokens=4096):
    """调用本地 Ollama 模型"""
    # Ollama 的 generate API 将 system_prompt 和 prompt 合并
    full_prompt = f"{system_prompt}\n\n{prompt}" if system_prompt else prompt
    
    payload = {
        "model": MODEL,
        "prompt": full_prompt,
        "stream": False,
        "options": {
            "temperature": temperature,
            "num_predict": max_tokens
        }
    }
    
    try:
        response = requests.post(
            OLLAMA_API_URL,
            json=payload,
            timeout=180
        )
        response.raise_for_status()
        result = response.json().get("response", "").strip()
        
        # 清理 DeepSeek 模型的思考标签
        result = re.sub(r"<think>.*?</think>", "", result, flags=re.DOTALL).strip()
        # 清理代码块标记
        result = re.sub(r"```[a-zA-Z]*\n(.*?)```", r"\1", result, flags=re.DOTALL).strip()
        
        return result
    except requests.exceptions.RequestException as e:
        print(f"Ollama 请求失败: {e}")
        raise


def get_provider_name():
    """获取当前使用的 LLM 提供商名称（用于显示）"""
    if _llm_provider == "deepseek":
        return f"DeepSeek API ({DEEPSEEK_MODEL})"
    else:
        return f"Ollama ({MODEL})"
