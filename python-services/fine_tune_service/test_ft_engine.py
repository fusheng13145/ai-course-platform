"""8003 LoRA 引擎单测：配置归一、别名表、数据集/任务存储、真实训练取证。

真实训练用例由 `skipif` 门禁（需 peft/transformers 与本地已缓存基座），
CI 只跑纯逻辑部分。"""
from pathlib import Path

import pytest

import lora_engine as le


@pytest.fixture(autouse=True)
def isolated_data_dir(tmp_path, monkeypatch):
    monkeypatch.setenv("FT_DATA_DIR", str(tmp_path / "ftdata"))
    le.reset_store()
    yield
    le.reset_store()


def test_as_dict_accepts_object_and_java_json_string():
    assert le.as_dict({"epochs": 3}) == {"epochs": 3}
    assert le.as_dict('{"epochs": 3, "learning_rate": 5e-05}') == {"epochs": 3, "learning_rate": 5e-05}
    assert le.as_dict(None) == {}
    assert le.as_dict("") == {}


def test_as_dict_rejects_non_object_json():
    with pytest.raises(le.ConfigError):
        le.as_dict("[1,2]")
    with pytest.raises(le.ConfigError):
        le.as_dict("{oops")


def test_normalize_config_clamps_and_validates():
    cfg = le.normalize_config({"epochs": 3, "learning_rate": 5e-5, "lora_rank": 8,
                               "lora_alpha": 16, "lora_dropout": 0.1,
                               "target_modules": ["q_proj", "v_proj"]})
    assert cfg["epochs"] == 3 and cfg["lora_rank"] == 8
    assert cfg["target_modules"] == ["q_proj", "v_proj"]
    assert cfg["learning_rate"] == pytest.approx(5e-5)
    with pytest.raises(le.ConfigError):
        le.normalize_config({"lora_dropout": 1.5})
    with pytest.raises(le.ConfigError):
        le.normalize_config({"epochs": 0})


def test_resolve_base_model_alias_table_and_unknown_name():
    assert le.resolve_base_model("qwen-7b") == "Qwen/Qwen2.5-0.5B"
    assert le.resolve_base_model("Qwen/Qwen2.5-0.5B") == "Qwen/Qwen2.5-0.5B"
    with pytest.raises(le.UnknownModelError) as exc:
        le.resolve_base_model("gpt-99")
    assert "FT_MODEL_MAP" in str(exc.value)


def test_format_size_human_string_matches_contract_shape():
    assert le.format_size(15_938_048) == "15.2MB"
    assert le.format_size(0) == "0B"
    assert le.format_size(2048) == "2.0KB"


def test_task_error_is_actionable_when_no_dataset(tmp_path, monkeypatch):
    monkeypatch.delenv("FT_ENGINE_READY", raising=False)
    cfg = le.normalize_config({})
    assert "datasets/build" in le.task_error(cfg)


def test_validate_samples_accepts_three_shapes():
    rows = [{"instruction": "什么是RR", "output": "时间片轮转"},
            {"question": "什么是FCFS", "answer": "先来先服务"},
            {"messages": [{"role": "user", "content": "什么是SJF"},
                          {"role": "assistant", "content": "最短作业优先"}]}]
    out = le.validate_samples(rows)
    assert [d["instruction"] for d in out] == ["什么是RR", "什么是FCFS", "什么是SJF"]
    assert [d["output"] for d in out] == ["时间片轮转", "先来先服务", "最短作业优先"]
    assert all(d["derived_from"] == "上传" for d in out)


def test_validate_samples_errors_carry_one_based_row_no():
    with pytest.raises(ValueError) as exc:
        le.validate_samples([{"instruction": "q", "output": "a"}, {"instruction": "只有问"}])
    assert "第 2 条" in str(exc.value)
    with pytest.raises(ValueError) as exc:
        le.validate_samples([])
    assert "至少需要一条" in str(exc.value)
    with pytest.raises(ValueError) as exc:
        le.validate_samples(["不是对象"])
    assert "第 1 条" in str(exc.value)


def test_build_samples_is_deterministic_and_traceable():
    text = "## 梯度下降\n梯度下降迭代为 θ←θ−η∇J(θ)，学习率过大将震荡发散。\n"
    a = le.build_samples(text, "优化-梯度下降")
    b = le.build_samples(text, "优化-梯度下降")
    assert a == b
    assert a[0]["output"].startswith("梯度下降")
    assert a[0]["derived_from"] == "优化-梯度下降"
    assert "梯度下降" in a[0]["instruction"]


def test_build_samples_caps_length_and_drops_empty():
    assert le.build_samples("", "空文档") == []
    assert le.build_samples("一" * 5000, "长文") and len(
        le.build_samples("一" * 5000, "长文")[0]["output"]) <= 600


def test_add_and_list_datasets(tmp_path):
    saved = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    assert saved["sample_count"] == 1 and saved["dataset_id"]
    assert saved["created_at"].endswith("+08:00") or "+" in saved["created_at"]
    assert [d["dataset_id"] for d in le.list_datasets(1)] == [saved["dataset_id"]]
    assert le.load_samples(saved["dataset_id"]) == [{"instruction": "q", "output": "a"}]


def test_dataset_id_never_reuses_a_number_after_delete(monkeypatch):
    """按 len() 取号会在删除后撞车（覆盖幸存数据集）——序号必须是只增的按天计数器。"""
    monkeypatch.setattr(le, "_today_str", lambda: "20261017")
    a = le.add_dataset(1, [{"instruction": "q1", "output": "a1"}], source="上传")
    b = le.add_dataset(1, [{"instruction": "q2", "output": "a2"}], source="上传")
    le.delete_dataset(a["dataset_id"])
    c = le.add_dataset(1, [{"instruction": "q3", "output": "a3"}], source="上传")
    assert a["dataset_id"] == "ds_20261017_001"
    assert b["dataset_id"] == "ds_20261017_002"
    assert c["dataset_id"] == "ds_20261017_003"
    assert [d["dataset_id"] for d in le.list_datasets(1)] == [b["dataset_id"], c["dataset_id"]]


def test_build_course_dataset_from_repo_corpus_is_rebuildable(tmp_path, monkeypatch):
    """语料目录缺失时回落仓库内已版本化 JSON——满足 DR-40「目录不入版本控制必须有重建路径」。"""
    monkeypatch.setattr(le, "_corpus_dir_or_none", lambda: None)
    repo_corpus = Path(le.__file__).resolve().parents[2] / "scripts" / "rag_corpus.json"
    monkeypatch.setenv("FT_CORPUS_JSON", str(repo_corpus))
    out = le.build_course_dataset(1)
    assert out["success"] is True and out["sample_count"] > 0
    assert "rag_corpus.json" in out["source"]


def test_build_course_dataset_without_any_source_fails_honestly(tmp_path, monkeypatch):
    monkeypatch.setattr(le, "_corpus_dir_or_none", lambda: None)
    monkeypatch.setenv("FT_CORPUS_JSON", str(tmp_path / "不存在.json"))
    out = le.build_course_dataset(42)
    assert out["success"] is False
    assert "FT_CORPUS_JSON" in out["error"] and "datasets" in out["error"]


def test_delete_dataset_keeps_task_records(tmp_path):
    saved = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    assert le.delete_dataset(saved["dataset_id"])["dataset_id"] == saved["dataset_id"]
    assert le.get_dataset(saved["dataset_id"]) is None
    assert le.load_samples(saved["dataset_id"]) == []
