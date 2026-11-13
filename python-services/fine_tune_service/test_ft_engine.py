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


def test_base_model_snapshot_prefers_local_dir_so_load_never_touches_hub(tmp_path, monkeypatch):
    """缓存命中必须给出**本地目录**：按仓库 ID 加载时 transformers 仍会发一次 Hub revision 探测。"""
    # huggingface_hub 属 requirements-engine.txt（transformers 家族）的传递依赖；
    # CI 契约护栏只装 requirements.txt，与「真实训练用例缺重依赖即 skip」同一口径跳过，
    # 不为一个用例往轻量清单里塞 Hub 客户端（那会让 CI 装成引擎依赖的子集，读起来像假绿）。
    huggingface_hub = pytest.importorskip(
        "huggingface_hub", reason="引擎重依赖未安装（requirements-engine.txt），跳过缓存命中用例")
    snap = tmp_path / "snap060db64"
    snap.mkdir(parents=True)
    (snap / "config.json").write_text("{}", encoding="utf-8")
    monkeypatch.setattr(huggingface_hub, "try_to_load_from_cache",
                        lambda repo, fn, **kw: str(snap / "config.json") if fn == "config.json" else None)
    monkeypatch.setenv("HF_HUB_CACHE", str(tmp_path / "none-here"))
    assert le.base_model_snapshot("X/Y") == snap.resolve()
    assert le.base_model_cached("X/Y") is True
    assert le.load_target("X/Y") == str(snap.resolve())
    monkeypatch.setattr(huggingface_hub, "try_to_load_from_cache", lambda repo, fn, **kw: None)
    assert le.base_model_snapshot("X/Y") is None
    assert le.load_target("X/Y") == "X/Y"      # 未缓存才回落到仓库 ID（此时才可能下载）


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


def test_task_error_is_actionable_and_course_scoped(tmp_path):
    cfg = le.normalize_config({})
    msg = le.task_error(cfg, 99)
    assert "datasets/build/99" in msg                       # 文案带真实课程号，照抄即可执行
    ds = le.add_dataset(7, [{"instruction": "q", "output": "a"}], source="上传")
    assert le.task_error(cfg, 7) is None                    # 本课程有数据集 → 放行
    assert le.task_error(cfg, 8) is not None                # 别的课程有不等于本课程有
    assert le.task_error(le.normalize_config({"dataset_id": ds["dataset_id"]}), 8) is None


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


def test_next_task_id_shape_and_daily_counter(monkeypatch):
    monkeypatch.setattr(le, "_today_str", lambda: "20261017")
    assert le.next_task_id() == "ft_20261017_001"
    assert le.next_task_id() == "ft_20261017_002"
    monkeypatch.setattr(le, "_today_str", lambda: "20261018")   # 换日重新从 001 起
    assert le.next_task_id() == "ft_20261018_001"


def test_create_task_freezes_config_and_dataset(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    task = le.create_task(1, "qwen-7b", le.normalize_config({"epochs": 2, "dataset_id": ds["dataset_id"]}))
    assert task["status"] == "pending" and task["total_epochs"] == 2
    assert task["dataset_id"] == ds["dataset_id"]
    assert task["base_model"] == "qwen-7b" and task["hf_model_id"] == "Qwen/Qwen2.5-0.5B"
    assert task["progress"] == 0.0 and task["current_epoch"] == 0


def test_progress_pct_is_monotonic_and_capped():
    assert le._progress_pct(0, 10) == 0.0
    assert le._progress_pct(5, 10) == 50.0
    assert le._progress_pct(10, 10) == 99.5  # 运行中封顶，完成才 100
    assert le._progress_pct(3, 0) == 0.0     # 除零保护


def test_record_progress_writes_state_values_not_kwargs(tmp_path):
    """钉住 `_record_progress` 的取数契约：progress 跟 global_step 走。
    若实现误从回调 kwargs 取 `step`（那里没有），progress 会恒为 0.0 且无报错。"""
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    cfg = le.normalize_config({"epochs": 2, "dataset_id": ds["dataset_id"]})
    t = le.create_task(1, "qwen-7b", cfg)
    tid = t["task_id"]
    assert t["total_epochs"] == 2
    le._record_progress(tid, 10, global_step=3, epoch=0.4, loss=1.2345678)
    row = le.get_task(tid)
    assert row["progress"] == 30.0 and row["loss_history"] == [1.234568]
    assert row["current_epoch"] == 1
    le._record_progress(tid, 10, global_step=10, epoch=1.9, loss=0.5)
    row = le.get_task(tid)
    assert row["progress"] == 99.5 and row["current_epoch"] == 2  # 运行中封顶 99.5，轮次不超过 epochs
    assert row["loss_history"] == [1.234568, 0.5]
    le._record_progress(tid, 10, global_step=10, epoch=2.0, loss=None)
    assert le.get_task(tid)["loss_history"] == [1.234568, 0.5]   # 无 loss 的日志行不写历史


def test_cancel_on_pending_marks_cancelled_and_on_finished_is_rejected(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    out = le.cancel_task(t["task_id"])
    assert out == {"success": True, "status": "cancelled"}
    assert le.get_task(t["task_id"])["status"] == "cancelled"
    le.update_task(t["task_id"], status="completed")
    again = le.cancel_task(t["task_id"])
    assert again["success"] is False and again["status"] == "completed" and "无法取消" in again["error"]


def test_delete_task_removes_record_and_run_dir(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    assert le.delete_task(t["task_id"])["task_id"] == t["task_id"]
    assert le.get_task(t["task_id"]) is None
    assert le.list_tasks(1) == []


def test_run_training_reports_missing_deps_honestly(tmp_path, monkeypatch):
    """依赖缺失/数据集为空都必须给出可识别失败，不得伪装 running 后静默结束。"""
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    monkeypatch.setattr(le, "engine_available", lambda: False)
    out = le.run_training(t["task_id"])
    assert out["status"] == "failed" and "依赖未安装" in out["error"]
    monkeypatch.setattr(le, "engine_available", lambda: True)
    empty = le.create_task(2, "qwen-7b", le.normalize_config({}))
    assert le.run_training(empty["task_id"])["status"] == "failed"


def test_format_sample_prefers_chat_template_and_falls_back():
    s = {"instruction": "什么是过拟合", "input": "", "output": "模型在训练集上表现好而泛化差"}
    txt = le._format_sample(s, None)
    assert "什么是过拟合" in txt and "泛化差" in txt
    called = {}

    def fake(messages, **kw):
        called["messages"] = messages
        called["kw"] = kw
        return "TPL"

    assert le._format_sample(s, fake) == "TPL"
    assert called["messages"][1]["content"].startswith("模型在训练集")
    assert called["kw"]["tokenize"] is False  # 不传 tokenize=False 拿到的是 token id 而非文本


def test_export_requires_completed_task(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    out = le.export_weights(t["task_id"])
    assert out["code"] == 503 and "尚未完成" in out["error"]


def test_export_zips_adapter_and_reports_real_bytes(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    adir = Path(t["adapter_dir"]); adir.mkdir(parents=True, exist_ok=True)
    (adir / "adapter_config.json").write_text('{"r": 8}', encoding="utf-8")
    (adir / "adapter_model.safetensors").write_bytes(b"\0" * 4096)
    le.update_task(t["task_id"], status="completed")
    out = le.export_weights(t["task_id"], public_base="http://localhost:8003")
    assert out["format"] == "peft_lora"
    assert out["download_url"] == f"http://localhost:8003/weights/{t['task_id']}.zip"
    assert out["file_size"] == le.format_size(out["size_bytes"])
    assert Path(out["local_path"]).exists() and out["size_bytes"] > 0


def test_infer_without_engine_deps_is_recognizable_failure(tmp_path, monkeypatch):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    le.update_task(t["task_id"], status="completed")
    monkeypatch.setattr(le, "engine_available", lambda: False)
    out = le.infer(t["task_id"], "什么是过拟合", {"temperature": 0.7, "max_tokens": 64})
    assert out["code"] == 503 and "requirements-engine.txt" in out["error"]


def test_infer_rejects_unfinished_task(tmp_path):
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    out = le.infer(t["task_id"], "q", {})
    assert out["code"] == 503 and "未完成" in out["error"]


def test_adapter_cache_is_keyed_by_task_not_only_by_presence():
    """钉住 `_MODEL_HOLDER` 的缓存键：漏掉 task_id 会让任务 B 静默用任务 A 的权重。"""
    assert le._adapter_cache_matches("ft_x") is False  # 无缓存
    le._MODEL_HOLDER.update({"model": object(), "task_id": "ft_x"})
    assert le._adapter_cache_matches("ft_x") is True
    assert le._adapter_cache_matches("ft_y") is False  # 换任务必须重载
    le.reset_store()
    assert le._adapter_cache_matches("ft_x") is False  # 重置后不得留下跨用例缓存


def test_reset_store_clears_cancel_flags_so_reused_ids_are_not_pre_cancelled(tmp_path):
    """按天序号会重号：残留取消标志必须随 reset_store 清掉。"""
    ds = le.add_dataset(1, [{"instruction": "q", "output": "a"}], source="上传")
    t = le.create_task(1, "qwen-7b", le.normalize_config({"dataset_id": ds["dataset_id"]}))
    le.cancel_task(t["task_id"])
    assert le._CANCEL_FLAGS[t["task_id"]] is True
    le.reset_store()
    assert le._CANCEL_FLAGS == {}


requires_real_engine = pytest.mark.skipif(
    not (le.engine_available() and le.base_model_cached("Qwen/Qwen2.5-0.5B")),
    reason="需 peft/transformers 与本地已缓存基座模型")


@requires_real_engine
def test_real_lora_training_produces_true_artifacts(tmp_path, monkeypatch):
    """真跑一次 LoRA：完成态、adapter 落盘、导出字节、推理文本必须都是实物。

    步数够多时**追加**训练损失下降断言：默认 6 步不给下降断言——52/108 条样本、
    completion-only、几步之内的损失本就非单调（本机实测 8 步首/末 3.4446/4.5615），
    硬钉"下降"等于替实验结果编形状。而跑到 `FT_TRAIN_STEPS>=40`（或按 epochs 自然跑满）
    时，本机实测头/尾三段均值 3.816 → 2.631 是真下降，此时不钉就是放水。
    注意这是**训练损失**，样本量小必然过拟合，不代表生成质量提升。
    """
    monkeypatch.setenv("FT_TRAIN_STEPS", "6")
    monkeypatch.setenv("FT_ALLOW_DOWNLOAD", "0")
    built = le.build_course_dataset(4007)   # 唯一有足够讲义正文的课（其余课语料 <20 条）
    assert built["success"], built
    cfg = le.normalize_config({"epochs": 1, "lora_rank": 4, "dataset_id": built["dataset_id"]})
    done = le.run_training(le.create_task(4007, "qwen-7b", cfg)["task_id"])
    assert done["status"] == "completed", done

    hist = done["loss_history"]
    assert len(hist) >= 2 and all(0.0 < x < 20.0 for x in hist)   # 真实数值，不是占位 0
    assert done["progress"] == 100.0 and done["current_epoch"] == 1
    if len(hist) >= 40:
        head, tail = sum(hist[:3]) / 3, sum(hist[-3:]) / 3
        assert tail < head, f"跑满 {len(hist)} 步训练损失未下降：头 {head:.3f} → 尾 {tail:.3f}"

    adir = Path(done["adapter_dir"])
    assert (adir / "adapter_config.json").exists()
    assert (adir / "adapter_model.safetensors").stat().st_size > 10_000

    ex = le.export_weights(done["task_id"])
    assert ex["code"] == 200 and ex["mode"] == "ENGINE" and ex["format"] == "peft_lora"
    assert Path(ex["local_path"]).stat().st_size == ex["size_bytes"] > 0
    assert Path(ex["local_path"]).read_bytes()[:2] == b"PK"        # 真是 zip

    out = le.infer(done["task_id"], "请讲解「计算机网络-第1章-网络概述与体系结构」的第 1 个要点。",
                   {"max_tokens": 32})
    assert out["mode"] == "ENGINE" and out["answer"] and out["tokens_used"] > 0
