package com.aicourse.service.component.ai;

import com.aicourse.service.component.sys.SysConfigComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiGenerationPolicy 单元测试（角色矩阵「限制教师出题权限」的判定口径）。
 *
 * <p>重点是「缺省等于现状」这条：配置行不存在（或空白/非 false 值）必须判为**允许**，
 * 否则会把"没配过"当成"被禁用"，让既有教师的出题能力在无人操作的情况下静默失效。</p>
 */
@ExtendWith(MockitoExtension.class)
class AiGenerationPolicyTest {

    @Mock
    private SysConfigComponent sysConfigComponent;

    @InjectMocks
    private AiGenerationPolicy policy;

    @Test
    @DisplayName("配置缺失（null）视为允许：缺省必须等于现状，不得把'没配过'当'被禁用'")
    void missingConfig_isAllowed() {
        when(sysConfigComponent.getConfig(AiGenerationPolicy.TEACHER_QUESTION_GENERATION_KEY))
                .thenReturn(null);
        assertTrue(policy.isTeacherQuestionGenerationEnabled());
    }

    @Test
    @DisplayName("空白值视为允许（不把脏数据当禁用）")
    void blankValue_isAllowed() {
        when(sysConfigComponent.getConfig(AiGenerationPolicy.TEACHER_QUESTION_GENERATION_KEY))
                .thenReturn("   ");
        assertTrue(policy.isTeacherQuestionGenerationEnabled());
    }

    @Test
    @DisplayName("显式 true / TRUE 视为允许")
    void explicitTrue_isAllowed() {
        when(sysConfigComponent.getConfig(AiGenerationPolicy.TEACHER_QUESTION_GENERATION_KEY))
                .thenReturn("TRUE");
        assertTrue(policy.isTeacherQuestionGenerationEnabled());
    }

    @Test
    @DisplayName("显式 false（含大小写与空白）判为禁用")
    void explicitFalse_isDenied() {
        when(sysConfigComponent.getConfig(AiGenerationPolicy.TEACHER_QUESTION_GENERATION_KEY))
                .thenReturn(" false ");
        assertFalse(policy.isTeacherQuestionGenerationEnabled());
        verify(sysConfigComponent).getConfig(AiGenerationPolicy.TEACHER_QUESTION_GENERATION_KEY);
    }
}
