"""start-backend.bat 的第 5 步守卫：一键拉起引擎，但不因为引擎失败而阻断 Java 启动。

为什么用源码扫描而不是执行 .bat：本机无法在单测里可靠地起/杀 7687/8001/8002 与 8080
（那正是项目主手动的部分），而 DR-37/DR-38 两次实测事故都落在**文件形态**上——
非 ASCII 字节会让 chcp 65001 之前的 cmd 按 GBK 解析整行、`echo` 出密钥值会直接进日志。
形态能静态判定，就静态判定；进程与端口的真实行为由 `scripts/tests/test_start_engines.py`
加真跑取证。

运行：仓库根执行 `python -m pytest scripts/tests -q`。
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BAT = ROOT / "start-backend.bat"


def _lines(data: bytes) -> list[str]:
    return data.decode("ascii", errors="strict").replace("\r\n", "\n").split("\n")


def step_block(text: str, start: str, end: str) -> str:
    """取 :start 到 :end 之间的脚本体。"""
    head = text.index(start)
    tail = text.index(end, head)
    return text[head:tail]


def echoes_secret(text: str) -> bool:
    """有 echo 行打印了 DEEPSEEK_API_KEY 的**完整值**。

    形状判定用的 `!DEEPSEEK_API_KEY:~0,3!` 只截前缀，允许；`%DEEPSEEK_API_KEY%` /
    整名的 `!DEEPSEEK_API_KEY!` 出现在 echo 之后即视为泄密。
    """
    for line in text.replace("\r\n", "\n").split("\n"):
        low = line.strip().lower()
        if not low.startswith("echo"):
            continue
        if "%DEEPSEEK_API_KEY%" in line or "!DEEPSEEK_API_KEY!" in line:
            return True
    return False


def exits_inside(block: str) -> bool:
    """该步骤体内是否有 `exit /b`——有就是失败即中断，与「引擎可选、不阻塞」相违。"""
    return any("exit /b" in line.lower() for line in block.split("\n"))


def test_launcher_step5_launches_the_engines():
    text = BAT.read_text(encoding="ascii")
    body = step_block(text, ":step5_check_engines", ":step6_start_backend")
    # 一键语义：第 5 步真的调用启动器，而不只是打印手工命令
    assert "start-engines.py" in body
    assert "call " in body  # call 才能把 batch 脚本/多命令解释器的返回值收回来
    # 保留手工兜底：找不到 python 时仍要能照着命令自己起
    assert "uvicorn" in body


def test_launcher_step5_never_blocks_the_java_start():
    body = step_block(BAT.read_text(encoding="ascii"), ":step5_check_engines", ":step6_start_backend")
    # 引擎起不来只上报；Java 侧有本地降级提供器（§34），不该因此退出
    assert exits_inside(body) is False
    assert "errorlevel 1 goto step6" not in body


def test_launcher_is_pure_ascii_with_crlf_endings():
    """DR-37：非 ASCII 字节在 chcp 之前会被按 GBK 解析，整行命令乱掉。"""
    data = BAT.read_bytes()
    assert max(data) < 128
    assert data.count(b"\r\n") >= 10
    assert data.replace(b"\r\n", b"").count(b"\n") == 0
    # 阴性对照：真把中文写进去就该被判非 ASCII（证明上面不是恒真断言）
    assert max(data + "启动".encode("utf-8")) > 127


def test_launcher_never_prints_the_api_key_value():
    assert echoes_secret(BAT.read_text(encoding="ascii")) is False
    # 阴性对照：只有前缀形状判定是允许的，整值 echo 必须被判出
    assert echoes_secret('echo key is %DEEPSEEK_API_KEY%\r\n') is True
    assert echoes_secret('if not "!DEEPSEEK_API_KEY:~0,3!"=="sk-" goto x\r\n') is False
