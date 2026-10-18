"""8003 契约护栏：路径与字段名以 `ContractPaths.java:63-70` 与手册 §37 为准。
CI 作业 `python-fine-tune-contract` 守护本文件，演进引擎逻辑时不得破坏端点形状。"""
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import lora_engine as le
import lora_service as svc

P = "/api/v1/fine-tune"


@pytest.fixture(autouse=True)
def isolated(tmp_path, monkeypatch):
    monkeypatch.setenv("FT_DATA_DIR", str(tmp_path / "ftdata"))
    monkeypatch.setenv("FT_ENGINE_READY", "1")   # 不启动训练线程：状态机必须可确定性断言
    monkeypatch.delenv("FT_CORPUS_DIR", raising=False)
    monkeypatch.delenv("FT_PUBLIC_BASE", raising=False)
    le.reset_store()
    yield
    le.reset_store()


@pytest.fixture
def client():
    return TestClient(svc.app)


def _ds(client, course_id=1, n=1):
    return client.post(f"{P}/datasets", json={"course_id": course_id, "samples": [
        {"instruction": f"q{i}", "output": f"a{i}"} for i in range(n)]}).json()


def _start(client, course_id=1, cfg=None, base_model="qwen-7b"):
    ds = _ds(client, course_id)
    body = {"course_id": course_id, "base_model": base_model,
            "config": {"dataset_id": ds["dataset_id"], **(cfg or {})}}
    return client.post(f"{P}/start", json=body), ds


def test_health_reports_engine_truth(client):
    body = client.get(f"{P}/health").json()
    assert body["status"] == "ok"
    assert body["engine_available"] == le.engine_available()   # 真值来自探针，不是声明
    assert "deps_missing" in body and "known_models" in body
    assert body["datasets"] == 0 and body["tasks"] == 0


def test_start_accepts_spec_object_config(client):
    ds = _ds(client, 1)
    r = client.post(f"{P}/start", json={
        "course_id": 1, "base_model": "qwen-7b",
        "config": {"epochs": 3, "learning_rate": 5e-05, "lora_rank": 8, "lora_alpha": 16,
                   "lora_dropout": 0.1, "target_modules": ["q_proj", "v_proj"],
                   "dataset_id": ds["dataset_id"]}})
    assert r.status_code == 200
    body = r.json()
    assert {"task_id", "status", "created_at"} <= set(body)
    assert body["task_id"].startswith("ft_") and body["status"] == "pending"
    assert body["mode"] == "STRUCTURE"      # FT_ENGINE_READY=1：任务记录真实、训练线程未起
    assert body["code"] == 200


def test_start_accepts_java_string_config(client):
    """Java 端 `config` 是 JsonNode.toString()（LLMFineTuneServiceImpl.java:80）——上线即踩的点。"""
    ds = _ds(client, 1)
    r = client.post(f"{P}/start", json={
        "course_id": 1, "base_model": "qwen-7b",
        "config": json.dumps({"epochs": 2, "lora_rank": 4, "dataset_id": ds["dataset_id"]})})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["code"] == 200 and body["status"] == "pending"
    assert le.get_task(body["task_id"])["config"]["lora_rank"] == 4


def test_start_rejects_malformed_config(client):
    r = client.post(f"{P}/start", json={"course_id": 1, "base_model": "qwen-7b",
                                        "config": "{oops"})
    assert r.status_code == 400
    assert "config" in r.json()["detail"]


def test_start_rejects_unregistered_base_model_with_alias_table(client):
    ds = _ds(client, 1)
    r = client.post(f"{P}/start", json={"course_id": 1, "base_model": "gpt-99",
                                        "config": {"dataset_id": ds["dataset_id"]}})
    assert r.status_code == 400
    detail = r.json()["detail"]
    assert "FT_MODEL_MAP" in detail and "qwen-7b" in detail   # 回显登记表，不猜模型


def test_start_without_dataset_degrades_actionably(client):
    r = client.post(f"{P}/start", json={"course_id": 99, "base_model": "qwen-7b"})
    assert r.status_code == 200          # Java 对非 5xx 才原样透传，故降级放 body
    body = r.json()
    assert body["code"] == 503 and body["mode"] == "UNAVAILABLE"
    assert "datasets/build/99" in body["error"] and body["status"] == "rejected"


def test_task_status_roundtrip_pinned_fields(client):
    r, ds = _start(client, 7, {"epochs": 2})
    tid = r.json()["task_id"]
    st = client.get(f"{P}/tasks/{tid}/status").json()
    assert {"task_id", "status", "progress", "current_epoch", "total_epochs",
            "base_model", "dataset_id", "mode"} <= set(st)
    assert st["task_id"] == tid and st["base_model"] == "qwen-7b"
    assert st["dataset_id"] == ds["dataset_id"] and st["total_epochs"] == 2
    assert st["progress"] == 0.0 and st["current_epoch"] == 0
    assert "adapter_dir" not in st and "dir" not in st         # 不泄露文件系统路径


def test_unknown_ids_are_404_everywhere(client):
    for path, verb in ((f"{P}/tasks/ft_19700101_999/status", "get"),
                       (f"{P}/tasks/ft_19700101_999/export", "get"),
                       (f"{P}/tasks/ft_19700101_999", "delete"),
                       (f"{P}/tasks/ft_19700101_999/cancel", "post"),
                       (f"{P}/datasets/ds_19700101_999/samples", "get")):
        assert getattr(client, verb)(path).status_code == 404, path
    assert client.post(f"{P}/infer/ft_19700101_999",
                       json={"prompt": "q"}).status_code == 404


def test_list_tasks_sorted_and_filtered(client):
    a, _ = _start(client, 3)
    b, _ = _start(client, 4)
    a, b = a.json()["task_id"], b.json()["task_id"]
    assert [t["task_id"] for t in client.get(f"{P}/tasks?course_id=3").json()["tasks"]] == [a]
    assert [t["task_id"] for t in client.get(f"{P}/tasks").json()["tasks"]] == [a, b]


def test_cancel_and_delete_roundtrip(client):
    r, _ = _start(client, 5)
    tid = r.json()["task_id"]
    out = client.post(f"{P}/tasks/{tid}/cancel").json()
    assert out["success"] is True and out["status"] == "cancelled"
    again = client.post(f"{P}/tasks/{tid}/cancel").json()
    assert again["success"] is False and again["status"] == "cancelled" and "无法取消" in again["error"]
    deleted = client.delete(f"{P}/tasks/{tid}").json()
    assert deleted["success"] is True and deleted["task_id"] == tid
    assert client.get(f"{P}/tasks/{tid}/status").status_code == 404


def test_export_on_unfinished_task_degrades_instead_of_faking_success(client):
    r, _ = _start(client, 1)
    ex = client.get(f"{P}/tasks/{r.json()['task_id']}/export")
    assert ex.status_code == 200
    body = ex.json()
    assert body["code"] == 503 and body["mode"] == "UNAVAILABLE"
    assert body["download_url"] is None and "尚未完成" in body["error"]
    # 占位导出已从设计里删除：宁给 503，也不在 download_url 上摆一个假链接


def test_infer_degrades_per_contract_without_real_engine(client):
    """没有真实模型就没有真实文本：`/infer` 永不占位（手册 §38）。"""
    r, _ = _start(client, 1)
    resp = client.post(f"{P}/infer/{r.json()['task_id']}",
                       json={"prompt": "什么是过拟合", "params": {"temperature": 0.7}})
    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 503 and body["mode"] == "UNAVAILABLE"
    assert body["answer"] is None and body["tokens_used"] == 0 and body["error"]


def test_infer_accepts_java_string_params(client):
    """Java 端 `params` 同样是字符串（LLMFineTuneServiceImpl.java:129）。"""
    r, _ = _start(client, 1)
    resp = client.post(f"{P}/infer/{r.json()['task_id']}",
                       json={"prompt": "q", "params": json.dumps({"temperature": 0.2})})
    assert resp.status_code == 200 and resp.json()["code"] == 503   # 形状吃下，仍如实降级
    bad = client.post(f"{P}/infer/{r.json()['task_id']}", json={"prompt": "q",
                                                               "params": "{oops"})
    assert bad.status_code == 400 and "params" in bad.json()["detail"]


def test_exported_zip_is_really_downloadable_and_bytes_match(client):
    """§37 的 file_size/download_url 必须是磁盘真值：下载路由 + 实际字节逐处核对。"""
    r, _ = _start(client, 1)
    tid = r.json()["task_id"]
    adir = Path(le.get_task(tid)["adapter_dir"])
    adir.mkdir(parents=True, exist_ok=True)
    (adir / "adapter_config.json").write_text('{"r": 8, "peft_type": "LORA"}', encoding="utf-8")
    (adir / "adapter_model.safetensors").write_bytes(b"\0" * 8192)
    le.update_task(tid, status="completed")

    body = client.get(f"{P}/tasks/{tid}/export").json()
    assert body["code"] == 200 and body["mode"] == "ENGINE" and body["format"] == "peft_lora"
    assert body["download_url"] == f"http://localhost:8003/weights/{tid}.zip"
    assert body["file_size"] == le.format_size(body["size_bytes"])
    assert "local_path" not in body                              # 服务器路径不出契约

    got = client.get(f"/weights/{tid}.zip")
    assert got.status_code == 200 and len(got.content) == body["size_bytes"] > 0
    assert client.get("/weights/ft_19700101_999.zip").status_code == 404


def test_weight_download_route_rejects_path_traversal(client):
    """下载路由只认 `<task_id>.zip` 形状：编码点号与斜杠都不放行。"""
    for evil in ("/weights/%2e%2e%2fdatasets.json", "/weights/..%2Ftasks.json",
                 "/weights/datasets.json", "/weights/a%2Fb.zip"):
        assert client.get(evil).status_code == 404, evil


def test_dataset_upload_jsonl_mixed_shapes_and_samples_endpoint(client):
    jsonl = "\n".join([json.dumps({"instruction": "什么是RR", "output": "时间片轮转调度"}),
                       json.dumps({"question": "什么是FCFS", "answer": "先来先服务"}), ""])
    r = client.post(f"{P}/datasets", json={"course_id": 3, "jsonl": jsonl})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["sample_count"] == 2 and body["dataset_id"].startswith("ds_")
    assert body["source"] == "上传 JSONL" and "dir" not in body
    got = client.get(f"{P}/datasets/{body['dataset_id']}/samples").json()
    assert got["returned"] == 2 and got["sample_count"] == 2 and got["offset"] == 0
    assert got["samples"][1]["output"] == "先来先服务"
    rows = client.get(f"{P}/datasets?course_id=3").json()["datasets"]
    assert [d["dataset_id"] for d in rows] == [body["dataset_id"]]
    assert set(rows[0]) == {"dataset_id", "course_id", "sample_count", "source", "created_at"}


def test_samples_endpoint_pages_with_offset_and_limit(client):
    saved = client.post(f"{P}/datasets", json={"course_id": 6, "samples": [
        {"instruction": f"q{i}", "output": f"a{i}"} for i in range(120)]}).json()
    page = client.get(f"{P}/datasets/{saved['dataset_id']}/samples?offset=100&limit=50").json()
    assert page["returned"] == 20 and page["samples"][0]["instruction"] == "q100"
    over = client.get(f"{P}/datasets/{saved['dataset_id']}/samples?limit=501")
    assert over.status_code == 422                               # pydantic 校验先把关


def test_dataset_upload_errors_carry_one_based_row_no(client):
    bad = client.post(f"{P}/datasets", json={"course_id": 3, "jsonl": "{bad json"})
    assert bad.status_code == 400 and "jsonl 第 1 行" in bad.json()["detail"]
    miss = client.post(f"{P}/datasets", json={"course_id": 3, "samples": [
        {"instruction": "q", "output": "a"}, {"instruction": "只有问"}]})
    assert miss.status_code == 400 and "第 2 条" in miss.json()["detail"]
    empty = client.post(f"{P}/datasets", json={"course_id": 3, "samples": []})
    assert empty.status_code == 400 and "至少需要一条" in empty.json()["detail"]
    neither = client.post(f"{P}/datasets", json={"course_id": 3})
    assert neither.status_code == 400 and "samples" in neither.json()["detail"]


def test_dataset_build_with_inline_source_and_no_body(client):
    built = client.post(f"{P}/datasets/build/3",
                        json={"source": "## 调度\n先来先服务会让长作业饿死。"}).json()
    assert built["success"] is True and built["sample_count"] >= 1
    assert "调用方提供" in built["source"] and built["mode"] == "ENGINE"

    # 无 body：走回落链（FT_CORPUS_DIR 未设 → 仓库内已版本化 scripts/rag_corpus.json）
    fallback = client.post(f"{P}/datasets/build/1").json()
    assert fallback["success"] is True and "rag_corpus.json" in fallback["source"]


def test_dataset_build_without_any_corpus_fails_honestly(client, tmp_path, monkeypatch):
    monkeypatch.setenv("FT_CORPUS_JSON", str(tmp_path / "绝不存在的语料.json"))
    out = client.post(f"{P}/datasets/build/42").json()
    assert out["success"] is False and out["code"] == 503 and out["mode"] == "UNAVAILABLE"
    assert "FT_CORPUS_DIR" in out["error"] and "datasets" in out["error"]


def test_delete_dataset_roundtrip(client):
    ds = _ds(client, 8)
    out = client.delete(f"{P}/datasets/{ds['dataset_id']}").json()
    assert out["success"] is True and out["dataset_id"] == ds["dataset_id"]
    assert client.get(f"{P}/datasets?course_id=8").json()["datasets"] == []


def test_corpus_lists_derivable_documents(client):
    body = client.get(f"{P}/corpus").json()
    assert body["code"] == 200 and body["mode"] == "ENGINE"
    assert any(d["course_id"] == 1 and d["chars"] > 0 for d in body["documents"])
    assert 1 in body["courses"]
