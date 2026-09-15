package com.aicourse.entity;

/**
 * 试卷状态枚举（修复 CR-3：消除 ExamPaperServiceImpl 中 PUBLISHED/DRAFT 等魔法串硬编码）。
 * <p>
 * 枚举 {@code name()} 与数据库持久化字符串保持一致（DRAFT / PUBLISHED / ...），
 * 实体字段仍为 {@code String}，调用方以 {@code ExamPaperStatus.X.name()} 写入、以
 * {@code ExamPaperStatus.X.name().equals(status)} 比较，DB 契约不变。新增状态请在此登记。
 * </p>
 */
public enum ExamPaperStatus {

    /** 草稿：未发布，仅教师可见可编辑。 */
    DRAFT,

    /** 已发布：对学生开放，发布后会推送选课通知。 */
    PUBLISHED,

    /** 已归档：考试结束后的只读态（保留枚举值，供状态机扩展）。 */
    ARCHIVED
}
