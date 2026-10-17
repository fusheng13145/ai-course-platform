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
