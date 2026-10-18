-- ====================================================================
-- V8：AI 调用日志落库 + 会话补课程维度（跨课程 AI 统计的唯一数据源）
-- --------------------------------------------------------------------
-- 背景（手册 §D.12.4 / spec §9 取证）：`ai_chat_sessions` 原本没有 course_id 列，
-- 且 `AIController` 建会话时写作 `createSession(userId, null)` —— courseId 已在入口处
-- 收到却在那一行被丢弃，只透传给 RAG 引擎；全仓亦无任何 AI 调用落库，而
-- `GET /admin/statistics/ai-usage` 返回的 `totalCalls` 是由今日登录数推算出来的编造值
-- （`SysMonitorComponent` 里 `todayLogins * 3`，注释自承"估算"）。
-- ⇒ 按课程聚合的 AI 使用统计，此前**既无数据源、也无从回填**。
--
-- 本迁移只补"数据面"两件事：
--   ① `ai_chat_sessions` 增 `course_id`：会话可归属课程，同时修掉上面那处丢弃；
--   ② 新建 `ai_call_logs`：全部 AI 入口的逐次调用日志（场景 / 课程 / 供应商 / token / 耗时 / 成败）。
--
-- ⚠️ 口径边界（必须与 UI、手册一致，不得回避）：统计**只能从埋点上线当日起算**，
--    此前的调用没有留痕，任何"历史曲线"都是伪造。管理端页面与手册均已如实标注起算日。
-- ====================================================================

ALTER TABLE ai_chat_sessions
    ADD COLUMN course_id BIGINT NULL COMMENT '关联课程ID，NULL 表示未绑定课程（通识问答）' AFTER user_id,
    ADD INDEX idx_course_id (course_id);

CREATE TABLE ai_call_logs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NULL COMMENT '调用者用户ID；未认证为空',
    course_id BIGINT NULL COMMENT '关联课程ID；NULL 表示未绑定课程（通识问答/作文批改等）',
    scene VARCHAR(32) NOT NULL COMMENT '业务场景：QA_ASK/QA_CONTINUE/ESSAY_CORRECT/ESSAY_POLISH/QUESTION_RECOMMEND/LEARNING_RECOMMEND/WEAK_POINTS/LEARNING_ADVICE/LEARNING_PATH/PERSONALIZED_PATH/GRAPH_EXTRACT/QUESTION_GENERATE',
    provider VARCHAR(32) NULL COMMENT 'LLM 供应商：deepseek/openai/qwen；未配置任何 Key 时为空',
    prompt_tokens INT NOT NULL DEFAULT 0 COMMENT '输入 token 估算（1 token≈1.5 字符，与 AiQuotaService 同口径）',
    completion_tokens INT NOT NULL DEFAULT 0 COMMENT '输出 token 估算',
    duration_ms INT NULL COMMENT 'AI 调用耗时(ms)；未计时为空',
    success TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1-成功；0-失败或降级为兜底文案',
    error_msg VARCHAR(255) NULL COMMENT '失败原因（截断 255 字符）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_course_created (course_id, created_at) COMMENT '按课程聚合 AI 用量的主查询索引',
    KEY idx_user_created (user_id, created_at) COMMENT '按用户聚合',
    KEY idx_created_at (created_at) COMMENT '按时间窗过滤',
    KEY idx_scene (scene) COMMENT '按场景下钻'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用日志表（跨课程 AI 统计唯一数据源，自 2026-10-13 起埋点，历史不可回填）';
