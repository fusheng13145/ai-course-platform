package com.aicourse.service.impl;

import com.aicourse.common.ActivityVisibility;
import com.aicourse.common.ApiCode;
import com.aicourse.entity.TaskTeachingClass;
import com.aicourse.entity.TeachingClass;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamTeachingClassMapper;
import com.aicourse.mapper.TaskTeachingClassMapper;
import com.aicourse.mapper.TeachingClassMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ActivityTargetServiceImpl} 单测（V15 / WP6 验收第 2 条）。
 */
@ExtendWith(MockitoExtension.class)
class ActivityTargetServiceImplTest {

    @Mock
    private TaskTeachingClassMapper taskTeachingClassMapper;
    @Mock
    private ExamTeachingClassMapper examTeachingClassMapper;
    @Mock
    private TeachingClassMapper teachingClassMapper;
    @InjectMocks
    private ActivityTargetServiceImpl service;

    private TeachingClass teachingClass(long id, long courseId) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setCourseId(courseId);
        return teachingClass;
    }

    @Test
    @DisplayName("未指定可见范围时默认 COURSE（存量与新建同口径）")
    void null_visibility_defaults_to_course() {
        assertEquals(ActivityVisibility.COURSE,
                service.normalizeAndValidate(null, 1L, null, null, false));
        assertEquals(ActivityVisibility.COURSE,
                service.normalizeAndValidate(null, 1L, " course ", null, false));
    }

    @Test
    @DisplayName("非法可见范围返回 400")
    void invalid_visibility_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.normalizeAndValidate(null, 1L, "PUBLIC", null, false));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("CLASSES 新建时必须至少有一个目标教学班")
    void classes_without_targets_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.normalizeAndValidate(null, 1L, ActivityVisibility.CLASSES, List.of(), false));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("目标教学班不属于活动课程时返回 400")
    void cross_course_target_rejected() {
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass(11L, 2L)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.normalizeAndValidate(null, 1L, ActivityVisibility.CLASSES, List.of(11L), false));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("合法 CLASSES 目标通过校验并返回 CLASSES")
    void valid_classes_targets_pass() {
        when(teachingClassMapper.selectBatchIds(any())).thenReturn(List.of(teachingClass(11L, 1L)));

        assertEquals(ActivityVisibility.CLASSES,
                service.normalizeAndValidate(null, 1L, ActivityVisibility.CLASSES, List.of(11L), false));
    }

    @Test
    @DisplayName("COURSE 范围重写目标时清空残留目标行")
    void replace_course_clears_targets() {
        service.replaceTaskTargets(1L, ActivityVisibility.COURSE, List.of(11L));

        verify(taskTeachingClassMapper).delete(any());
        verify(taskTeachingClassMapper, never()).insert(any(TaskTeachingClass.class));
    }

    @Test
    @DisplayName("CLASSES 范围重写目标时按去重结果落库")
    void replace_classes_inserts_targets() {
        service.replaceExamPaperTargets(2L, ActivityVisibility.CLASSES, List.of(11L, 11L, 12L));

        verify(examTeachingClassMapper).delete(any());
        ArgumentCaptor<com.aicourse.entity.ExamTeachingClass> captor =
                ArgumentCaptor.forClass(com.aicourse.entity.ExamTeachingClass.class);
        verify(examTeachingClassMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(List.of(11L, 12L),
                captor.getAllValues().stream()
                        .map(com.aicourse.entity.ExamTeachingClass::getTeachingClassId).toList());
    }
}
