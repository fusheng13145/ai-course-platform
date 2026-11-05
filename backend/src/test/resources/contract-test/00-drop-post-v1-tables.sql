-- ====================================================================
-- 契约测试库重建前置清理：丢弃 V1 DROP 清单未覆盖（V1 之后新增）的表
-- ====================================================================
-- 背景（2026-10-16 实测，真缺陷）：契约测试库由 spring.sql.init 按序重建——
-- V1 负责 DROP（48 张）+ CREATE 基线 + 种子，+ 结构迁移在此之上演进。
-- 但 V1 的 DROP 清单冻结在其编写时点，不含 V8/V9/V10 新建的表；于是测试库的
-- **第二次**重建会在 V8 的 `CREATE TABLE ai_call_logs`（无 IF NOT EXISTS）处
-- 报 `Table 'ai_call_logs' already exists`，4 个 @SpringBootTest 集成类
-- （ApiContractTest / AuthFlowIntegrationTest / ApplicationIT / Phase1Phase3IntegrationTest）
-- 全部在上下文加载阶段失败（本机复现；git stash 基线对照同样失败，属预存问题而非改动引入）。
--
-- 不能改 V1 本体：V1 是 Flyway 版本化迁移，改内容会使**开发库**的
-- flyway_schema_history 校验和不匹配（validateOnMigrate 直接拒启）。
-- 故新增本测试专属清理脚本，在 V1 之前执行（只做幂等 DROP，不创建任何对象）。
--
-- 纪律：+ 新增结构迁移若再建表，必须同步本清单——由
-- ContractTestSchemaBootstrapTest#postV1TablesAreDroppableBeforeRecreate 机械守住（漏加即红）。
-- ====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- V8__ai_call_logging.sql
DROP TABLE IF EXISTS ai_call_logs;
-- V9__semester_registry.sql
DROP TABLE IF EXISTS semesters;
-- V10__ai_generation_restrictions.sql
DROP TABLE IF EXISTS ai_generation_restrictions;
-- V12__org_foundation.sql（先子后父，FOREIGN_KEY_CHECKS 已在下方统一关闭）
DROP TABLE IF EXISTS admin_classes;
DROP TABLE IF EXISTS majors;
DROP TABLE IF EXISTS colleges;
DROP TABLE IF EXISTS schools;

SET FOREIGN_KEY_CHECKS = 1;
