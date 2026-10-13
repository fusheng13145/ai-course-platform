package com.aicourse.service.component.ai;

import com.aicourse.service.AiGenerationRestrictionService;
import com.aicourse.service.component.sys.SysConfigComponent;
import org.springframework.stereotype.Component;

/**
 * AI 生成治理策略（角色矩阵「限制教师出题权限」的判定唯一入口，2026-10-13）。
 *
 * <p><b>两层判定（2026-10-13 修订后：平台总闸 + 逐人名单）</b>：
 * <ol>
 *   <li><b>平台总闸</b> —— {@link #isTeacherQuestionGenerationEnabled()}，值在既有
 *       {@code system_configs}（键 {@code ai.question-generation.teacher-enabled}），
 *       管理员关掉即对**所有非 ADMIN 调用方**生效（紧急"一键全禁"）；</li>
 *   <li><b>逐人名单</b> —— {@link #isUserRestricted(Long)}，值在 {@code ai_generation_restrictions}
 *       （V10 新增），只命中被点名的教师。</li>
 * </ol>
 * 控制器对非 ADMIN 调用方**先查总闸、再查名单**，任一命中即 403；ADMIN 两层都不受限
 * （治理者保留能力）。两层**并存而非替代**：把名单当总闸用会让"临时全禁"变成逐人操作。</p>
 *
 * <p><b>为什么缺省视为"允许"</b>：配置行/名单记录不存在时若判为关闭，会把"没配过"当成"被禁用"，
 * 使既有教师的出题能力在无人操作的情况下失效——缺省必须等于现状。</p>
 *
 * <p>⚠️ 未实现的相邻能力（如实记录）：**按课程**或**按时间窗**的临时限制未做；
 * 若项目主需要"某时段内全体禁出题"，应扩展总闸为带时间窗的配置，而不是批量往名单里塞人。</p>
 */
@Component
public class AiGenerationPolicy {

    /** 系统配置键（{@code system_configs.config_key}）：教师 AI 出题权限**总闸**开关。 */
    public static final String TEACHER_QUESTION_GENERATION_KEY = "ai.question-generation.teacher-enabled";

    /** 系统配置分组（{@code system_configs.config_group}），与 /admin/configs 的按组查询对齐。 */
    public static final String CONFIG_GROUP = "ai";

    private final SysConfigComponent sysConfigComponent;
    private final AiGenerationRestrictionService aiGenerationRestrictionService;

    public AiGenerationPolicy(SysConfigComponent sysConfigComponent,
                              AiGenerationRestrictionService aiGenerationRestrictionService) {
        this.sysConfigComponent = sysConfigComponent;
        this.aiGenerationRestrictionService = aiGenerationRestrictionService;
    }

    /**
     * 平台总闸：教师是否被允许使用 AI 出题。
     *
     * @return {@code true} 允许（配置缺失、空白或非 "false" 一律视为允许）
     */
    public boolean isTeacherQuestionGenerationEnabled() {
        String value = sysConfigComponent.getConfig(TEACHER_QUESTION_GENERATION_KEY);
        return value == null || !"false".equalsIgnoreCase(value.trim());
    }

    /**
     * 逐人名单：该用户是否被点名限制 AI 出题。
     *
     * @param userId 调用者用户ID（可空——匿名/未认证一律视为未被限制，实际鉴权由角色门负责）
     * @return {@code true} 在限制名单内
     */
    public boolean isUserRestricted(Long userId) {
        return aiGenerationRestrictionService.isRestricted(userId);
    }
}

