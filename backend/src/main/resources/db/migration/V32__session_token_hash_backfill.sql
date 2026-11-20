-- V32（2026-11-20，R2）：存量会话 token 全量 SHA-256 回填。
-- 背景：新签发会话已只存 SHA-256 指纹（AuthSessionComponent），但历史行仍存明文 JWT。
-- 任何能读到 user_sessions 的人可直接复用这些 token；本迁移把非 64 位小写十六进制的
-- session_token 统一替换为 SHA2(token, 256)，与 Java 侧写入口径一致。
-- 前置读数：本地开发库 user_sessions 共 2063 行，其中 LIKE 'eyJ%' 的明文 JWT 2051 行；
-- 契约测试库由 V1 重建后为空，本迁移为空操作。
-- 回滚：不可逆（明文 token 已被覆盖，且不应恢复）。
UPDATE user_sessions
SET session_token = SHA2(session_token, 256)
WHERE session_token IS NOT NULL
  AND session_token NOT REGEXP '^[0-9a-f]{64}$';
