package com.aicourse.service.component.ai;

import com.aicourse.service.component.sys.SysConfigComponent;
import org.springframework.stereotype.Component;

/**
 * AI 生成治理策略（角色矩阵「限制教师出题权限」的实现落点，2026-10-13）。
 *
 * <p><b>口径（必须与手册、管理端页面一致）</b>：开关是**平台级治理开关**而非按教师逐个禁用——
 * 管理员关掉后，非 ADMIN 的调用方（教师）在 AI 出题入口一律 403；ADMIN 不受限（治理者保留能力）。
 * 开关值存在既有的 {@code system_configs} 表（走系统配置热切换，不新增存储面），
 * 由管理员经 {@code PUT /admin/configs/{key}} 写入，管理端「AI 使用与治理」页提供开关。</p>
 *
 * <p><b>为什么缺省视为"开启"</b>：配置行不存在时若判为关闭，会把"没配过"当成"被禁用"，
 * 使既有教师的出题能力在无人操作的情况下失效——缺省必须等于现状。</p>
 *
 * <p>⚠️ 未实现的相邻能力（如实记录）：**按教师/按课程**的差异化限制未做（需新增存储面与选择器 UI）；
 * 若项目主要"只限制某几个教师"，本条需扩展，不得把当前全局开关当成分教师的结果。</p>
 */
@Component
public class AiGenerationPolicy {

    /** 系统配置键（{@code system_configs.config_key}）：教师 AI 出题权限开关。 */
    public static final String TEACHER_QUESTION_GENERATION_KEY = "ai.question-generation.teacher-enabled";

    /** 系统配置分组（{@code system_configs.config_group}），与 /admin/configs 的按组查询对齐。 */
    public static final String CONFIG_GROUP = "ai";

    private final SysConfigComponent sysConfigComponent;

    public AiGenerationPolicy(SysConfigComponent sysConfigComponent) {
        this.sysConfigComponent = sysConfigComponent;
    }

    /**
     * 教师是否被允许使用 AI 出题。
     *
     * @return {@code true} 允许（配置缺失、空白或非 "false" 一律视为允许）
     */
    public boolean isTeacherQuestionGenerationEnabled() {
        String value = sysConfigComponent.getConfig(TEACHER_QUESTION_GENERATION_KEY);
        return value == null || !"false".equalsIgnoreCase(value.trim());
    }
}
