package com.aicourse.service.component.submission;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Submission;
import com.aicourse.entity.Task;
import com.aicourse.integration.rag.RagContract.RagRetrievalResult;
import com.aicourse.integration.rag.RagContract.RagSource;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.AIService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.NotificationService;
import com.aicourse.service.RAGService;
import com.aicourse.service.TaskService;
import com.aicourse.service.component.ai.CourseEvidenceComponent;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 批改的课程证据接地测试：验证提示词注入【课程参考资料】/【课程知识点（知识图谱）】/【课程知识点范围】
 * 与批改口径、submissionData.aiEvidence 的写入，以及证据服务全部异常时的降级行为。
 */
@ExtendWith(MockitoExtension.class)
class SubmissionGradeGroundingTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private TaskService taskService;
    @Mock private NotificationService notificationService;
    @Mock private AIService aiService;
    @Mock private SubmissionWriteComponent writeComponent;
    @Mock private RAGService ragService;
    @Mock private KnowledgePointService knowledgePointService;

    private SubmissionGradeComponent component;

    private Submission submission;
    private Task task;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper 需要实体 TableInfo 才能解析列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);

        component = new SubmissionGradeComponent(submissionMapper, taskService, notificationService, aiService,
                new SubmissionJsonComponent(new ObjectMapper()),
                writeComponent,
                new CourseEvidenceComponent(ragService, knowledgePointService));

        task = new Task();
        task.setId(1L);
        task.setCourseId(1L);
        task.setTitle("函数练习");
        task.setRequirements("请说明函数与模块的用法");
        task.setMaxScore(new BigDecimal("100"));

        submission = new Submission();
        submission.setId(1L);
        submission.setTaskId(1L);
        submission.setStudentId(11L);
        submission.setContent("我的答案：函数可以复用代码");

        when(submissionMapper.selectById(1L)).thenReturn(submission);
        when(taskService.getById(1L)).thenReturn(task);
        when(aiService.generateContent(eq("homework_grading"), anyString()))
                .thenReturn("{\"score\":85,\"feedback\":\"ok\",\"highlights\":\"h\",\"suggestions\":\"s\"}");
    }

    private static KnowledgePoint point(Long id, String name, String description, Integer importance) {
        KnowledgePoint point = new KnowledgePoint();
        point.setId(id);
        point.setName(name);
        point.setDescription(description);
        point.setImportance(importance);
        return point;
    }

    @Test
    @DisplayName("有课程证据：提示词注入资料与知识图谱知识点并写入 aiEvidence")
    void aiGrade_withEvidence_groundsPromptAndRecordsEvidence() {
        when(ragService.retrieveTyped(anyString(), eq(1L), eq(5)))
                .thenReturn(new RagRetrievalResult(
                        List.of(new RagSource(1L, "讲义A", 0.9, "片段X")), "ENGINE", null, null));
        when(knowledgePointService.listByCourse(1L))
                .thenReturn(List.of(point(1L, "函数与模块", "函数是可复用代码块", 5)));
        when(knowledgePointService.getPrerequisites(1L)).thenReturn(List.of());

        Submission result = component.aiGradeSubmission(1L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).generateContent(eq("homework_grading"), promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("【课程参考资料】"), "缺少参考资料段，实际=" + prompt);
        assertTrue(prompt.contains("讲义A"), "缺少召回文档名，实际=" + prompt);
        assertTrue(prompt.contains("【课程知识点（知识图谱）】"), "缺少知识点段，实际=" + prompt);
        assertTrue(prompt.contains("函数与模块"), "缺少知识点名，实际=" + prompt);
        assertTrue(prompt.contains("批改口径"), "缺少批改口径，实际=" + prompt);

        String data = result.getSubmissionData();
        assertNotNull(data);
        assertTrue(data.contains("\"aiEvidence\""), "应写入 aiEvidence，实际=" + data);
        assertTrue(data.contains("讲义A"), "aiEvidence 应含召回文档名，实际=" + data);
        assertTrue(data.contains("函数与模块"), "aiEvidence 应含知识点名，实际=" + data);
        assertEquals("ok", result.getAiFeedback());

        // 只通过 update(null, wrapper) 更新 AI 列，不整行覆盖教师字段
        verify(submissionMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("无课程证据：提示词不含证据段且不写 aiEvidence，评分仍正常落库")
    void aiGrade_withoutEvidence_omitsSectionsAndEvidence() {
        when(ragService.retrieveTyped(anyString(), eq(1L), eq(5)))
                .thenReturn(new RagRetrievalResult(List.of(), "ENGINE", null, null));
        when(knowledgePointService.listByCourse(1L)).thenReturn(List.of());

        Submission result = component.aiGradeSubmission(1L);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).generateContent(eq("homework_grading"), promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertFalse(prompt.contains("【课程参考资料】"), "无证据不得出现参考资料段，实际=" + prompt);
        assertFalse(prompt.contains("【课程知识点范围】"), "无证据不得出现知识点范围段，实际=" + prompt);
        assertFalse(prompt.contains("【课程知识点（知识图谱）】"), "无证据不得出现知识点段，实际=" + prompt);

        String data = result.getSubmissionData();
        assertNotNull(data);
        assertFalse(data.contains("aiEvidence"), "无证据不得伪造 aiEvidence，实际=" + data);
        assertTrue(data.contains("aiSuggestedScore"), "AI 建议分数应正常写入，实际=" + data);
        assertTrue(data.contains("85"), "AI 建议分数应为 85，实际=" + data);
        assertEquals("ok", result.getAiFeedback());
    }

    @Test
    @DisplayName("证据服务异常：降级为无证据批改，不抛异常也不写 aiEvidence")
    void aiGrade_evidenceFailure_degradesGracefully() {
        when(ragService.retrieveTyped(anyString(), anyLong(), eq(5)))
                .thenThrow(new RuntimeException("rag down"));
        when(knowledgePointService.listByCourse(anyLong()))
                .thenThrow(new RuntimeException("kg down"));

        Submission result = assertDoesNotThrow(() -> component.aiGradeSubmission(1L));

        String data = result.getSubmissionData();
        assertNotNull(data);
        assertFalse(data.contains("aiEvidence"), "证据检索失败不得写入 aiEvidence，实际=" + data);
        assertTrue(data.contains("aiSuggestedScore"), "AI 建议分数应正常写入，实际=" + data);
        assertEquals("ok", result.getAiFeedback());
    }
}
