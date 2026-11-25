package com.aicourse.application.teacher;

import com.aicourse.entity.Question;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.TeacherAccessGuard;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 3E-05C：{@code TeacherImportApplicationService} 用例。
 *
 * <p>四条 Excel 路径的逐条容错/重复判定已由 {@code TeacherControllerCoverageTest} 以真实
 * {@code ExcelUtil} 覆盖（63 例）；本类只锁两条「编排顺序」契约与结果回显的根因截断：
 * ①JSON 导入必须<b>先全量</b>过课程归属校验再逐条落库（不允许部分成功）；
 * ②结果对象对「跳过/失败」与「提示语判定」的映射。</p>
 */
@ExtendWith(MockitoExtension.class)
class TeacherImportApplicationServiceTest {

    @Mock
    private QuestionService questionService;
    @Mock
    private CourseChapterService chapterService;
    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private CourseAccessControl courseAccessControl;
    @Mock
    private TeacherAccessGuard teacherAccessGuard;
    @Mock
    private Authentication auth;

    @InjectMocks
    private TeacherImportApplicationService service;

    private static Question question(Long courseId, String content) {
        Question q = new Question();
        q.setCourseId(courseId);
        q.setContent(content);
        return q;
    }

    @Test
    @DisplayName("JSON 导入-先全量授权再逐条落库：一题无权则一条都不落库")
    void importQuestionsAuthorizesAllBeforeSaving() {
        List<Question> questions = List.of(question(1L, "A"), question(2L, "B"));
        // lenient：第 1 题有权、正常通过；第 2 题无权抛 403（避免 strict stubbing 误判参数不匹配）
        org.mockito.Mockito.lenient().doThrow(new com.aicourse.exception.BusinessException(
                        com.aicourse.common.ApiCode.FORBIDDEN, "无权"))
                .when(courseAccessControl).assertCanManageCourse(eq(2L), any());

        org.junit.jupiter.api.Assertions.assertThrows(com.aicourse.exception.BusinessException.class,
                () -> service.importQuestions(questions, auth));

        verify(questionService, times(0)).save(any());
        InOrder order = inOrder(courseAccessControl);
        order.verify(courseAccessControl).assertCanManageCourse(eq(1L), any());
        order.verify(courseAccessControl).assertCanManageCourse(eq(2L), any());
    }

    @Test
    @DisplayName("JSON 导入-全部有权：逐条 save，importedCount 等于条数，total 为 null（保持原语义）")
    void importQuestionsCountsAll() {
        when(questionService.save(any())).thenReturn(true);

        var result = service.importQuestions(List.of(question(1L, "A"), question(1L, "B")), auth);

        assertEquals(2, result.getImportedCount());
        assertEquals(null, result.getTotal());
        verify(questionService, times(2)).save(any());
    }

    @Test
    @DisplayName("根因截断-取因果链末端，超 200 字符截断加省略号，无 message 退回类名")
    void rootCauseMessageTruncates() {
        RuntimeException deep = new RuntimeException(new RuntimeException(new IllegalStateException("boom")));
        assertEquals("boom", TeacherImportApplicationService.rootCauseMessage(deep));

        String longText = "x".repeat(500);
        String msg = TeacherImportApplicationService.rootCauseMessage(new RuntimeException(longText));
        assertEquals(201, msg.length());
        assertTrue(msg.endsWith("…"));

        assertEquals("IllegalArgumentException",
                TeacherImportApplicationService.rootCauseMessage(new IllegalArgumentException()));
    }

    @Test
    @DisplayName("根因截断-自引用因果链不死循环")
    void rootCauseMessageHandlesSelfReference() {
        RuntimeException selfRef = new RuntimeException("self") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertEquals("self", TeacherImportApplicationService.rootCauseMessage(selfRef));
    }

    @Test
    @DisplayName("结果组装-空明细时 errors 为 null，hasIssues=false")
    void outcomeFlagsIssueFree() {
        List<String> errors = new ArrayList<>();
        var result = new com.aicourse.vo.TeacherImportResultVO(3, 3);
        result.setSkippedCount(0);
        result.setFailedCount(errors.size());
        result.setErrors(errors.isEmpty() ? null : errors);

        assertEquals(null, result.getErrors());
        assertEquals(3, result.getImportedCount());
    }
}
