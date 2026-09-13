#!/usr/bin/env python3
"""本地 Mock LLM（OpenAI 兼容），仅用于压测期间构造"健康 AI 基线"。
监听 0.0.0.0:9099，响应 POST /v1/chat/completions（流式/非流式）。
返回合法 OpenAI 格式，使后端 AI 调用成功，从而测得真实"健康态"AI 失败率。
压测结束后此服务可关闭，不影响生产。
"""
import json
import socketserver
import http.server


class Handler(http.server.BaseHTTPRequestHandler):
    def _respond(self):
        try:
            length = int(self.headers.get("Content-Length", 0))
            raw = self.rfile.read(length) if length else b"{}"
            req = json.loads(raw.decode("utf-8", "ignore")) if raw else {}
        except Exception:
            req = {}
        stream = bool(req.get("stream"))

        # 偶发延迟，模拟真实 LLM 网络往返（10~120ms 抖动）
        self.send_response(200)
        if stream:
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            chunk = json.dumps({"choices": [{"delta": {"content": "mock "}}]})
            self.wfile.write(f"data: {chunk}\n\n".encode("utf-8"))
            self.wfile.write(b"data: [DONE]\n\n")
        else:
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            resp = {
                "id": "chatcmpl-mock",
                "object": "chat.completion",
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": "mock response"},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {
                    "prompt_tokens": 6,
                    "completion_tokens": 4,
                    "total_tokens": 10,
                },
            }
            self.wfile.write(json.dumps(resp).encode("utf-8"))

    def do_POST(self):
        if self.path.rstrip("/").endswith("/chat/completions"):
            self._respond()
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    socketserver.ThreadingTCPServer.allow_reuse_address = True
    with socketserver.ThreadingTCPServer(("0.0.0.0", 9099), Handler) as httpd:
        print("mock_llm listening on :9099", flush=True)
        httpd.serve_forever()
