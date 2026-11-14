package com.aicourse.controller;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.entity.Task;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseApprovalService;
import com.aicourse.service.CourseService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.QuestionService;
import com.aicourse.service.SubmissionService;
import com.aicourse.service.TaskService;
import com.aicourse.service.TeacherCourseService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TeacherController 分支/长方法补测（覆盖率门禁专项）。
 *
 * <p>与 {@link TeacherControllerTest} 的分工：那份用例走的是「每个端点的正常放行 + 401 + STUDENT 403」，
 * 因此控制器里成片的导入方法（Excel 题目/章节/知识点导入）、归属校验的失败分支、
 * 入参可选分支（isPublished/status/courseId 是否为 null）与逐条容错分支基本没进过。
 * 本类按源码逐条枚举这些分支补测，重点是三条 Excel 导入长方法（含第二遍父级回填、
 * 重复跳过、单条失败降级、rootCauseMessage 的因果链遍历与截断）。
 *
 * <p>不可达分支（已在报告说明，未硬凑）：
 * {@code importQuestionsExcel} 与 {@code checkImportFile} 中 {@code file == null} ——
 * {@code @RequestParam("file") MultipartFile} 为必需部件，缺失时 Spring 先抛
 * {@code MissingServletRequestPartException}（由 GlobalExceptionHandler 转 400），控制器拿不到 null。
 */
@WebMvcTest(TeacherController.class)
class TeacherControllerCoverageTest extends ControllerTestBase {

    private static final long TEACHER_ID = 10L;
    private static final long ADMIN_ID = 90L;
    private static final long COURSE_ID = 5L;
    /** 他人课程：teacherId 与 TEACHER_ID 不同，用于越权分支 */
    private static final long FOREIGN_COURSE_ID = 77L;

    private static final String[] QUESTION_HEADERS =
            {"题型", "题干", "选项(JSON)", "正确答案", "难度", "分值", "知识点", "解析"};
    private static final String[] CHAPTER_HEADERS =
            {"章节标题", "父章节标题", "排序序号", "时长(分钟)", "是否发布", "章节内容"};
    private static final String[] KNOWLEDGE_HEADERS =
            {"知识点名称", "父知识点名称", "难度", "重要程度(1-10)", "预计学习时长(分钟)", "类型", "描述"};

    @MockitoBean private CourseService courseService;
    @MockitoBean private CourseApprovalService courseApprovalService;
    @MockitoBean private CourseChapterService chapterService;
    @MockitoBean private TaskService taskService;
    @MockitoBean private SubmissionService submissionService;
    @MockitoBean private QuestionService questionService;
    @MockitoBean private AnalyticsService analyticsService;
    @MockitoBean private CourseEnrollmentService enrollmentService;
    @MockitoBean private TeacherCourseService teachingService;
    @MockitoBean private KnowledgePointService knowledgePointService;
    /** 2026-11-13 对象级收口：/teacher/questions/import 逐题课程归属校验走 CourseAccessControl。 */
    @MockitoBean private com.aicourse.security.CourseAccessControl courseAccessControl;

    @BeforeEach
    void registerEntityMetadata() {
        // LambdaQueryWrapper 的列名与绑定参数在 getSqlSegment() 时才落地，需先注册实体元数据，
        // 否则下面 wrapperParams(...) 读不到 where 条件值（与 GradeControllerReportBoundaryTest 同法）。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Task.class);
    }

    /** 触发 wrapper 生成 SQL 片段并取出绑定参数值（等价于真实 SQL 的过滤条件）。 */
    private static Collection<Object> wrapperParams(Wrapper<?> wrapper) {
        assertTrue(wrapper != null, "查询条件 wrapper 不得为 null");
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }

    // ==================== 夹具 ====================

    private static Course course(Long id, Long teacherId) {
        Course c = new Course();
        c.setId(id);
        c.setTeacherId(teacherId);
        c.setName("课程 " + id);
        return c;
    }

    private static Task task(Long id, Long courseId) {
        Task t = new Task();
        t.setId(id);
        t.setCourseId(courseId);
        t.setTitle("作业 " + id);
        return t;
    }

    private static Question question(Long id, Long courseId) {
        Question q = new Question();
        q.setId(id);
        q.setCourseId(courseId);
        q.setContent("题干 " + id);
        return q;
    }

    private static CourseChapter chapter(Long id, Long courseId, String title) {
        CourseChapter ch = new CourseChapter();
        ch.setId(id);
        ch.setCourseId(courseId);
        ch.setTitle(title);
        return ch;
    }

    private static KnowledgePoint point(Long id, String name) {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(id);
        p.setName(name);
        return p;
    }

    /** 模拟真实分页行为：把控制器下传的 page/size 回填到结果页，使响应信封的分页字段可被实证。 */
    private static Page<Task> echoPage(Page<Task> sent, List<Task> records) {
        Page<Task> p = new Page<>();
        p.setRecords(records);
        p.setTotal(records.size());
        p.setCurrent(sent.getCurrent());
        p.setSize(sent.getSize());
        return p;
    }

    /** 生成 .xlsx 上传件：第 0 行为表头，其后按给定二维数组逐行写入（null 元素表示该单元格留空）。 */
    private static MockMultipartFile xlsx(String partName, String fileName, String[] headers, List<String[]> rows)
            throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("导入");
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] cells = rows.get(r);
                for (int c = 0; c < cells.length; c++) {
                    if (cells[c] != null) {
                        row.createCell(c).setCellValue(cells[c]);
                    }
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile(partName, fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bos.toByteArray());
        }
    }

    /** 只有表头、没有数据行的合法 xlsx（用于「文件中没有可导入的数据」分支）。 */
    private static MockMultipartFile headerOnlyFile(String[] headers) throws Exception {
        return xlsx("file", "only-header.xlsx", headers, List.of());
    }

    /** cause 指向自身的病态异常：用于验证 rootCauseMessage 的自引用保护不会死循环。 */
    private static final class SelfCauseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SelfCauseException(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }

    // ==================== 课程：归属校验的失败分支与管理员放行 ====================

    @Test
    @DisplayName("GET /teacher/courses/{id} - 管理员跳过归属校验后课程已被删除：400「课程不存在」")
    void getCourseDetail_adminBypassThenMissing_returns400() throws Exception {
        // 管理员分支：assertCourseOwner 直接 return，不再查库；随后控制器再 getById 得到 null，
        // 命中 getCourseDetail 自身的 if (course == null)。真实语义是「校验与读取之间课程被并发删除」。
        when(courseService.getById(COURSE_ID)).thenReturn(null);

        mockMvc.perform(get("/teacher/courses/" + COURSE_ID).with(withUser(ADMIN_ID, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    @Test
    @DisplayName("GET /teacher/courses/{id} - 教师访问不存在课程：404 且不进入详情读取")
    void getCourseDetail_courseMissing_returns404() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(null);

        mockMvc.perform(get("/teacher/courses/" + COURSE_ID).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("课程不存在"));
        // 归属校验失败即终止：详情读取那次 getById 不应发生（共 1 次调用）
        verify(courseService, times(1)).getById(COURSE_ID);
    }

    @Test
    @DisplayName("GET /teacher/courses/{id} - 教师访问他人课程：403 无权操作该课程")
    void getCourseDetail_foreignCourse_returns403() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));

        mockMvc.perform(get("/teacher/courses/" + FOREIGN_COURSE_ID).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该课程"));
    }

    @Test
    @DisplayName("DELETE /teacher/courses/{id} - 管理员可删除他人课程（isAdmin 放行分支）")
    void deleteCourse_adminBypass_passes() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));

        mockMvc.perform(delete("/teacher/courses/" + FOREIGN_COURSE_ID).with(withUser(ADMIN_ID, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("删除成功"));
        verify(courseService).deleteCourseById(FOREIGN_COURSE_ID);
    }

    @Test
    @DisplayName("GET /teacher/courses/{id}/classes - 端点此前完全未测：归属校验通过后委托 getCourseClasses")
    void courseClasses_ownershipPasses_delegates() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(teachingService.getCourseClasses(COURSE_ID)).thenReturn(List.of(java.util.Map.of("className", "计2101")));

        mockMvc.perform(get("/teacher/courses/" + COURSE_ID + "/classes").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].className").value("计2101"));
        verify(teachingService).getCourseClasses(COURSE_ID);
    }

    @Test
    @DisplayName("GET /teacher/courses/{id}/classes - 他人课程：403")
    void courseClasses_foreignCourse_returns403() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));

        mockMvc.perform(get("/teacher/courses/" + FOREIGN_COURSE_ID + "/classes").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(teachingService, never()).getCourseClasses(any());
    }

    // ==================== 作业：创建时的可选字段与归属校验 ====================

    @Test
    @DisplayName("POST /teacher/assignments - 已带 courseId/isPublished/status：不覆盖教师显式值")
    void createAssignment_explicitFields_preserved() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(taskService.save(any(Task.class))).thenReturn(true);

        // 命中 task.getCourseId() != null（走归属校验）、getIsPublished() == null 为假、getStatus() == null 为假
        mockMvc.perform(post("/teacher/assignments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"第三章作业\",\"courseId\":" + COURSE_ID
                                + ",\"isPublished\":true,\"status\":\"PUBLISHED\"}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isPublished").value(true))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.courseId").value((int) COURSE_ID));

        verify(courseService).getById(COURSE_ID);
        verify(taskService).save(any(Task.class));
    }

    @Test
    @DisplayName("POST /teacher/assignments - 无课程归属校验失败：403 且不落库")
    void createAssignment_foreignCourse_returns403AndNotSaved() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));

        mockMvc.perform(post("/teacher/assignments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权作业\",\"courseId\":" + FOREIGN_COURSE_ID + "}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(taskService, never()).save(any(Task.class));
    }

    @Test
    @DisplayName("GET /teacher/assignments/{id} - 作业不存在：404「作业不存在」")
    void getAssignmentDetail_taskMissing_returns404() throws Exception {
        when(taskService.getById(999L)).thenReturn(null);

        mockMvc.perform(get("/teacher/assignments/999").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("作业不存在"));
    }

    @Test
    @DisplayName("GET /teacher/assignments/{id} - 归属校验通过后作业被并发删除：400「作业不存在」")
    void getAssignmentDetail_taskVanishesBetweenChecks_returns400() throws Exception {
        // 第 1 次 getById（assertTaskOwner）拿到作业，第 2 次（详情读取）拿到 null，
        // 命中 getAssignmentDetail 自身的 if (task == null)。
        when(taskService.getById(9L)).thenReturn(task(9L, COURSE_ID)).thenReturn(null);
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));

        mockMvc.perform(get("/teacher/assignments/9").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("作业不存在"));
    }

    @Test
    @DisplayName("DELETE /teacher/assignments/{id} - 作业归属课程不属于当前教师：403")
    void deleteAssignment_courseNotOwned_returns403() throws Exception {
        when(taskService.getById(9L)).thenReturn(task(9L, FOREIGN_COURSE_ID));
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));

        mockMvc.perform(delete("/teacher/assignments/9").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(taskService, never()).removeById(any(Long.class));
    }

    // ==================== 作业列表分页：可选课程筛选 ====================

    @Test
    @DisplayName("GET /teacher/assignments - 带 courseId：分页参数与等值条件一并下传")
    void getAssignments_withCourseId_appliesCourseFilter() throws Exception {
        // P2-2：教师视角恒按 readableCourseIds 过滤（mock 给出包含目标课程的可读集）
        when(courseAccessControl.readableCourseIds(any())).thenReturn(List.of(COURSE_ID));
        AtomicReference<Page<Task>> sentPage = new AtomicReference<>();
        AtomicReference<Wrapper<Task>> sentQw = new AtomicReference<>();
        when(taskService.page(any(), any())).thenAnswer(inv -> {
            Page<Task> sent = inv.getArgument(0);
            sentPage.set(sent);
            sentQw.set(inv.getArgument(1));
            return echoPage(sent, List.of(task(1L, COURSE_ID)));
        });

        mockMvc.perform(get("/teacher/assignments")
                        .param("courseId", String.valueOf(COURSE_ID)).param("page", "2").param("size", "5")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(5));

        assertEquals(2L, sentPage.get().getCurrent(), "页码须原样下传给分页查询");
        assertEquals(5L, sentPage.get().getSize(), "每页条数须原样下传给分页查询");
        assertTrue(wrapperParams(sentQw.get()).contains(COURSE_ID),
                "带 courseId 时须拼出课程等值条件，实际=" + wrapperParams(sentQw.get()));
    }

    @Test
    @DisplayName("GET /teacher/assignments - 不带 courseId：按 readableCourseIds in 过滤（P2-2/D9 收口，旧契约'教师看全量'废止）")
    void getAssignments_withoutCourseId_buildsEmptyWrapper() throws Exception {
        AtomicReference<Wrapper<Task>> sentQw = new AtomicReference<>();
        AtomicReference<Page<Task>> sentPage = new AtomicReference<>();
        when(taskService.page(any(), any())).thenAnswer(inv -> {
            Page<Task> sent = inv.getArgument(0);
            sentPage.set(sent);
            sentQw.set(inv.getArgument(1));
            return echoPage(sent, List.of());
        });
        // P2-2：教师恒按可读课程过滤；这里给两门可读课程，wrapper 须拼出 in 条件
        when(courseAccessControl.readableCourseIds(any())).thenReturn(List.of(COURSE_ID, 9L));

        mockMvc.perform(get("/teacher/assignments").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10));

        assertEquals(1L, sentPage.get().getCurrent(), "缺省页码应为 1");
        assertEquals(10L, sentPage.get().getSize(), "缺省每页条数应为 10");
        // MP 的 in(column, 集合) 展开为逐元素参数：可读集 {COURSE_ID, 9} 须同时出现在参数中
        Collection<Object> params = wrapperParams(sentQw.get());
        assertTrue(params.contains(COURSE_ID) && params.contains(9L),
                "不带 courseId 时须按可读课程 in 过滤（D9 收口），实际=" + params);
    }

    // ==================== 题库：可选归属与题目孤儿态 ====================

    @Test
    @DisplayName("POST /teacher/questions - 指定课程时执行归属校验并落 createdBy")
    void createQuestion_withCourse_checksOwnership() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));

        mockMvc.perform(post("/teacher/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"题干\",\"courseId\":" + COURSE_ID + "}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.createdBy").value((int) TEACHER_ID));
        verify(courseService).getById(COURSE_ID);
        verify(questionService).createQuestion(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions - 指定他人课程：403 且不建题")
    void createQuestion_foreignCourse_returns403() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));

        mockMvc.perform(post("/teacher/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"题干\",\"courseId\":" + FOREIGN_COURSE_ID + "}")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(questionService, never()).createQuestion(any(Question.class));
    }

    @Test
    @DisplayName("GET /teacher/questions/{id} - 题目不存在：400「题目不存在」")
    void getQuestionDetail_questionMissing_returns400() throws Exception {
        when(questionService.getById(999L)).thenReturn(null);

        mockMvc.perform(get("/teacher/questions/999").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("题目不存在"));
    }

    @Test
    @DisplayName("GET /teacher/questions/{id} - 孤儿题目（courseId 为空）：跳过课程归属校验")
    void getQuestionDetail_orphanQuestion_skipsCourseCheck() throws Exception {
        when(questionService.getById(3L)).thenReturn(question(3L, null));

        mockMvc.perform(get("/teacher/questions/3").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(3));
        // courseId 为 null 时不应触碰课程表（否则每次查孤儿题都白跑一次 getById）
        verify(courseService, never()).getById(any());
    }

    @Test
    @DisplayName("PUT /teacher/questions/{id} - 他人课程下的题目：403 且不落库")
    void updateQuestion_foreignCourse_returns403() throws Exception {
        when(questionService.getById(4L)).thenReturn(question(4L, FOREIGN_COURSE_ID));
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));

        mockMvc.perform(put("/teacher/questions/4").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"改写题干\"}").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(questionService, never()).updateById(any(Question.class));
    }

    @Test
    @DisplayName("GET /teacher/questions - 不带 courseId：跳过归属校验，直接分页")
    void getQuestions_withoutCourseId_skipsOwnershipCheck() throws Exception {
        Page<Question> emptyPage = new Page<>();
        emptyPage.setRecords(List.of());
        when(questionService.page(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/teacher/questions").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
        verify(courseService, never()).getById(any());
        verify(questionService).page(any(), any());
    }

    // ==================== JSON 批量导入：循环体 ====================

    @Test
    @DisplayName("POST /teacher/questions/import - 非空列表：逐题过课程归属校验后逐条 save 并回显 importedCount")
    void importQuestions_nonEmptyList_savesEach() throws Exception {
        when(questionService.save(any(Question.class))).thenReturn(true);

        mockMvc.perform(post("/teacher/questions/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"content\":\"题干一\",\"courseId\":1},{\"content\":\"题干二\",\"courseId\":1},{\"content\":\"题干三\",\"courseId\":1}]")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.importedCount").value(3))
                // JSON 导入路径不回填 total（VO 的 total 为 null 时键缺失，前端零改动）
                .andExpect(jsonPath("$.data.total").doesNotExist());

        // 2026-11-13 收口：逐题课程归属校验（同 QuestionController.batchImport 口径）
        verify(courseAccessControl, times(3)).assertCanManageCourse(eq(1L), any());
        verify(questionService, times(3)).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions/import - 元素缺题干：@Valid 容器元素校验拦为 400，一条都不落库")
    void importQuestions_blankContent_rejected400() throws Exception {
        mockMvc.perform(post("/teacher/questions/import").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"content\":\"\"}]")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(questionService, never()).save(any(Question.class));
    }

    // ==================== Excel 题目导入：校验 + 逐条容错 + 因果链回显 ====================

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 空文件：400「请选择要上传的 Excel 文件」")
    void importQuestionsExcel_emptyFile_rejected400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(empty).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择要上传的 Excel 文件"));
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 只有表头：400「文件中没有可导入的题目数据」")
    void importQuestionsExcel_headerOnly_rejected400() throws Exception {
        MockMultipartFile file = headerOnlyFile(QUESTION_HEADERS);

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("文件中没有可导入的题目数据"));
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 指定他人课程：403 且不解析文件")
    void importQuestionsExcel_foreignCourse_returns403() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS,
                List.<String[]>of(new String[]{"SINGLE_CHOICE", "题干一", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file)
                        .param("courseId", String.valueOf(FOREIGN_COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 重复题干：跳过并计数，幂等不写库")
    void importQuestionsExcel_duplicate_skippedAndReported() throws Exception {
        // 不传 courseId → isDuplicateQuestion 走 isNull(courseId) 分支
        when(questionService.count(any())).thenReturn(1L);
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS,
                List.<String[]>of(new String[]{"SINGLE_CHOICE", "已存在题干", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成（存在跳过或失败项，详见明细）"))
                .andExpect(jsonPath("$.data.importedCount").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.errors").doesNotExist());
        verify(questionService, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 全新题干带 courseId：逐题补齐 courseId/createdBy/isActive")
    void importQuestionsExcel_newQuestionWithCourse_persistsEnrichedFields() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(questionService.count(any())).thenReturn(0L);
        when(questionService.save(any(Question.class))).thenReturn(true);
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS,
                List.<String[]>of(new String[]{"SINGLE_CHOICE", "新题干", "[\"A\",\"B\"]", "A", "中等", "5", "栈", "解析文本"}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成"))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.errors").doesNotExist());

        ArgumentCaptor<Question> cap = ArgumentCaptor.forClass(Question.class);
        verify(questionService).save(cap.capture());
        Question saved = cap.getValue();
        assertNull(saved.getId(), "导入前必须清空前端/文件带来的 ID，否则会变成覆盖他人记录");
        assertEquals(COURSE_ID, saved.getCourseId());
        assertEquals(TEACHER_ID, saved.getCreatedBy());
        assertEquals(Boolean.TRUE, saved.getIsActive());
        assertEquals("MEDIUM", saved.getDifficulty(), "难度必须归一化到 EASY/MEDIUM/HARD");
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - save 返回 false：不计入 imported 但也不算失败")
    void importQuestionsExcel_saveReturnsFalse_notCounted() throws Exception {
        when(questionService.count(any())).thenReturn(0L);
        when(questionService.save(any(Question.class))).thenReturn(false);
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS,
                List.<String[]>of(new String[]{"SINGLE_CHOICE", "题干一", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成"))
                .andExpect(jsonPath("$.data.importedCount").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 单条抛异常：逐条容错并回显根本原因（含因果链）")
    void importQuestionsExcel_saveThrows_failsSoftWithRootCause() throws Exception {
        when(questionService.count(any())).thenReturn(0L);
        when(questionService.save(any(Question.class)))
                .thenThrow(new RuntimeException(new IllegalStateException("Column 'content' cannot be null")));
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS,
                List.<String[]>of(new String[]{"SINGLE_CHOICE", "坏题干", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成（存在跳过或失败项，详见明细）"))
                .andExpect(jsonPath("$.data.importedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]")
                        .value("第 1 条：Column 'content' cannot be null"));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 根本原因无 message/空白 message：回显降级为异常类名")
    void importQuestionsExcel_rootCauseWithoutMessage_fallsBackToClassName() throws Exception {
        when(questionService.count(any())).thenReturn(0L);
        // 第 1 条：message 为 null；第 2 条：message 为全空白 —— 两者都应退化为类名而不是回显 "null"
        when(questionService.save(any(Question.class)))
                .thenThrow(new RuntimeException())
                .thenThrow(new IllegalArgumentException("   "));
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS, List.of(
                new String[]{"SINGLE_CHOICE", "题干甲", null, "A", "中等", "5", null, null},
                new String[]{"SINGLE_CHOICE", "题干乙", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.errors[0]").value("第 1 条：RuntimeException"))
                .andExpect(jsonPath("$.data.errors[1]").value("第 2 条：IllegalArgumentException"));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 原因链自引用：不死循环，取到该层 message")
    void importQuestionsExcel_selfReferencingCause_terminates() throws Exception {
        when(questionService.count(any())).thenReturn(0L);
        when(questionService.save(any(Question.class))).thenThrow(new SelfCauseException("自引用原因链"));
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS,
                List.<String[]>of(new String[]{"SINGLE_CHOICE", "题干", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value("第 1 条：自引用原因链"));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 超长原因：截断到 200 字符 + 省略号，不整段回显 SQL")
    void importQuestionsExcel_overlongRootCause_truncated() throws Exception {
        String longMsg = "x".repeat(250);
        when(questionService.count(any())).thenReturn(0L);
        when(questionService.save(any(Question.class))).thenThrow(new RuntimeException(longMsg));
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS,
                List.<String[]>of(new String[]{"SINGLE_CHOICE", "题干", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors[0]").value("第 1 条：" + "x".repeat(200) + "…"));
    }

    @Test
    @DisplayName("POST /teacher/questions/import-excel - 混合场景：导入 + 跳过 + 失败同时出现，明细逐条对齐序号")
    void importQuestionsExcel_mixedOutcomes_reportsAllCounters() throws Exception {
        when(questionService.count(any())).thenReturn(1L, 0L, 0L);
        when(questionService.save(any(Question.class)))
                .thenAnswer(inv -> true)
                .thenThrow(new RuntimeException("唯一键冲突"));
        MockMultipartFile file = xlsx("file", "questions.xlsx", QUESTION_HEADERS, List.of(
                new String[]{"SINGLE_CHOICE", "重复题干", null, "A", "中等", "5", null, null},
                new String[]{"SINGLE_CHOICE", "成功题干", null, "A", "中等", "5", null, null},
                new String[]{"SINGLE_CHOICE", "失败题干", null, "A", "中等", "5", null, null}));

        mockMvc.perform(multipart("/teacher/questions/import-excel").file(file).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.skippedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value("第 3 条：唯一键冲突"));
    }

    // ==================== 导出：不带 courseId 的分支 ====================

    @Test
    @DisplayName("GET /teacher/questions/export - 不传 courseId：跳过归属校验，导出全量题库")
    void exportQuestions_withoutCourseId_exportsAll() throws Exception {
        when(questionService.list(any(Wrapper.class))).thenReturn(List.of(question(1L, null)));

        mockMvc.perform(get("/teacher/questions/export").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(mvcResult -> {
                    byte[] body = mvcResult.getResponse().getContentAsByteArray();
                    assertTrue(body.length > 4, "导出内容不应为空");
                    assertEquals('P', body[0]);
                    assertEquals('K', body[1]);
                });
        verify(courseService, never()).getById(any());
    }

    // ==================== 学情分析：courseId 可选分支 ====================

    @Test
    @DisplayName("GET /teacher/analytics/knowledge-points - 不传 courseId：以 null 委托全平台分布")
    void getKnowledgePointAnalytics_withoutCourseId_passesNull() throws Exception {
        when(analyticsService.getGradeDistribution(null)).thenReturn(java.util.Map.of("scope", "platform"));

        mockMvc.perform(get("/teacher/analytics/knowledge-points").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scope").value("platform"));
        verify(courseService, never()).getById(any());
        verify(analyticsService).getGradeDistribution(null);
    }

    @Test
    @DisplayName("GET /teacher/analytics/report/export - 缺少必需 courseId：400 且不查库")
    void exportReport_missingCourseId_returns400() throws Exception {
        mockMvc.perform(get("/teacher/analytics/report/export").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("courseId")));
        verify(analyticsService, never()).exportReport(any());
    }

    @Test
    @DisplayName("GET /teacher/analytics/class/{id} - 服务层抛业务异常：按异常码包装为 403")
    void getClassAnalytics_serviceThrowsBusinessException_wrapped() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(analyticsService.getCourseAnalysis(COURSE_ID))
                .thenThrow(new com.aicourse.exception.BusinessException(403, "该课程尚未发布学情"));

        mockMvc.perform(get("/teacher/analytics/class/" + COURSE_ID).with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该课程尚未发布学情"));
    }

    @Test
    @DisplayName("GET /teacher/analytics/students/{id} - 无审批通过选课：403 且不拉取学情")
    void getStudentAnalytics_noApprovedEnrollment_returns403() throws Exception {
        when(enrollmentService.hasApprovedEnrollmentWithTeacher(eq(5L), eq(TEACHER_ID))).thenReturn(false);

        mockMvc.perform(get("/teacher/analytics/students/5").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权查看该学生学情"));
        verify(analyticsService, never()).getUserDashboard(any());
    }

    // ==================== AI 建课：可选参数缺省分支 ====================

    @Test
    @DisplayName("POST /teacher/courses/ai/generate-outline - 仅 topic：category/level 透传 null、chapterCount 用默认 6")
    void generateOutline_optionalParamsOmitted_usesDefaults() throws Exception {
        when(teachingService.generateCourseOutlineByAI(eq("数据结构"), any(), any(), eq(6), eq(TEACHER_ID)))
                .thenReturn(java.util.Map.of("topic", "数据结构", "chapterCount", 6));

        mockMvc.perform(post("/teacher/courses/ai/generate-outline").param("topic", "数据结构")
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chapterCount").value(6));

        verify(teachingService).generateCourseOutlineByAI("数据结构", null, null, 6, TEACHER_ID);
    }

    @Test
    @DisplayName("POST /teacher/courses/ai/generate-outline - 缺 topic：400 且不调用 AI")
    void generateOutline_missingTopic_returns400() throws Exception {
        mockMvc.perform(post("/teacher/courses/ai/generate-outline").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(teachingService, never())
                .generateCourseOutlineByAI(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("POST /teacher/courses/{id}/clone - 不带 newName：透传 null 由服务层沿用原名")
    void cloneCourse_withoutNewName_passesNull() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(teachingService.cloneCourse(COURSE_ID, null, TEACHER_ID))
                .thenReturn(java.util.Map.of("sourceCourseId", COURSE_ID));

        mockMvc.perform(post("/teacher/courses/" + COURSE_ID + "/clone").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sourceCourseId").value((int) COURSE_ID));
        verify(teachingService).cloneCourse(COURSE_ID, null, TEACHER_ID);
    }

    // ==================== Excel 章节导入 ====================

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 空文件：400「请选择要上传的 Excel 文件」")
    void importChaptersExcel_emptyFile_rejected400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "chapters.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(empty)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择要上传的 Excel 文件"));
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 非 xlsx：400「仅支持 .xlsx 格式」")
    void importChaptersExcel_badFormat_rejected400() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "chapters.csv", "text/csv",
                "章节标题,父章节标题\n第一章,\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(csv)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持 .xlsx 格式的 Excel 文件"));
        verify(courseService, never()).getById(any());
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 课程不属于当前教师：403 且不读文件")
    void importChaptersExcel_foreignCourse_returns403() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));
        MockMultipartFile file = xlsx("file", "chapters.xlsx", CHAPTER_HEADERS,
                List.<String[]>of(new String[]{"第一章", null, "1", "30", "是", null}));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(FOREIGN_COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(chapterService, never()).save(any(CourseChapter.class));
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 只有表头：400「文件中没有可导入的章节数据」")
    void importChaptersExcel_headerOnly_rejected400() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        MockMultipartFile file = headerOnlyFile(CHAPTER_HEADERS);

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文件中没有可导入的章节数据"));
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 两遍写入：父章节在文件任意位置都能回填 parentId")
    void importChaptersExcel_parentBackfill_writesTwoPasses() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(chapterService.list(any(Wrapper.class))).thenReturn(List.of());
        AtomicLong idGen = new AtomicLong(100L);
        when(chapterService.save(any(CourseChapter.class))).thenAnswer(inv -> {
            CourseChapter ch = inv.getArgument(0);
            ch.setId(idGen.incrementAndGet());
            return true;
        });
        // 第二遍查父章节：返回 101（即第一遍刚写入的「第一章」），不等于子章节 102 → 回填
        when(chapterService.getOne(any(Wrapper.class))).thenReturn(chapter(101L, COURSE_ID, "第一章"));

        MockMultipartFile file = xlsx("file", "chapters.xlsx", CHAPTER_HEADERS, List.of(
                // 显式排序号 + 显式未发布写法
                new String[]{"第一章", null, "3", "30", "否", "概述内容"},
                // 排序号留空 → 回退为行下标；父章节标题引用「第一章」
                new String[]{"第二节", "第一章", null, "45", "是", null}));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("导入完成"))
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.errors").doesNotExist());

        ArgumentCaptor<CourseChapter> saveCap = ArgumentCaptor.forClass(CourseChapter.class);
        verify(chapterService, times(2)).save(saveCap.capture());
        CourseChapter first = saveCap.getAllValues().get(0);
        assertEquals(COURSE_ID, first.getCourseId());
        assertEquals(Integer.valueOf(3), first.getOrderIndex(), "文件显式排序号优先");
        assertEquals(Boolean.FALSE, first.getIsPublished(), "「否」必须判为未发布");
        assertEquals(Integer.valueOf(30), first.getDuration());
        CourseChapter second = saveCap.getAllValues().get(1);
        assertEquals(Integer.valueOf(1), second.getOrderIndex(), "排序号留空时回退为行下标");
        assertEquals(Boolean.TRUE, second.getIsPublished());
        assertNull(second.getContent());

        ArgumentCaptor<CourseChapter> updateCap = ArgumentCaptor.forClass(CourseChapter.class);
        verify(chapterService).updateById(updateCap.capture());
        assertEquals(102L, updateCap.getValue().getId());
        assertEquals(101L, updateCap.getValue().getParentId(), "父章节须按标题解析后回填");
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 同课程同标题重复：跳过（库中已有 + 文件内重复各一次）")
    void importChaptersExcel_duplicateTitles_skipped() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(chapterService.list(any(Wrapper.class))).thenReturn(List.of(chapter(9L, COURSE_ID, "已存在章节")));
        AtomicLong idGen = new AtomicLong(200L);
        when(chapterService.save(any(CourseChapter.class))).thenAnswer(inv -> {
            CourseChapter ch = inv.getArgument(0);
            ch.setId(idGen.incrementAndGet());
            return true;
        });

        MockMultipartFile file = xlsx("file", "chapters.xlsx", CHAPTER_HEADERS, List.of(
                new String[]{"已存在章节", null, null, null, null, null},
                new String[]{"新章节", null, null, null, null, null},
                new String[]{"新章节", null, null, null, null, null}));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成（存在跳过或失败项，详见明细）"))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.skippedCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.errors").doesNotExist());
        verify(chapterService, times(1)).save(any(CourseChapter.class));
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 单条抛异常：记录序号与标题并继续其余行")
    void importChaptersExcel_saveThrows_recordsRowContext() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(chapterService.list(any(Wrapper.class))).thenReturn(List.of());
        when(chapterService.save(any(CourseChapter.class)))
                .thenThrow(new RuntimeException(new IllegalStateException("duration 超范围")))
                .thenAnswer(inv -> {
                    ((CourseChapter) inv.getArgument(0)).setId(302L);
                    return true;
                });

        MockMultipartFile file = xlsx("file", "chapters.xlsx", CHAPTER_HEADERS, List.of(
                new String[]{"坏章节", null, null, "99999", null, null},
                new String[]{"好章节", null, null, null, null, null}));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成（存在跳过或失败项，详见明细）"))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value("第 1 条（坏章节）：duration 超范围"));
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - save 返回 false：不计入导入也不建父子待回填")
    void importChaptersExcel_saveReturnsFalse_notImported() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(chapterService.list(any(Wrapper.class))).thenReturn(List.of());
        when(chapterService.save(any(CourseChapter.class))).thenReturn(false);

        MockMultipartFile file = xlsx("file", "chapters.xlsx", CHAPTER_HEADERS,
                List.<String[]>of(new String[]{"第一章", "父章节", null, null, null, null}));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0));
        // 未落库就不该有第二遍回填（getById/updateById 均不触发）
        verify(chapterService, never()).updateById(any(CourseChapter.class));
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 父章节不存在：保留为顶级并在明细中说明")
    void importChaptersExcel_parentMissing_keepsTopLevelAndReports() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(chapterService.list(any(Wrapper.class))).thenReturn(List.of());
        AtomicLong idGen = new AtomicLong(400L);
        when(chapterService.save(any(CourseChapter.class))).thenAnswer(inv -> {
            CourseChapter ch = inv.getArgument(0);
            ch.setId(idGen.incrementAndGet());
            return true;
        });
        when(chapterService.getOne(any(Wrapper.class))).thenReturn(null);

        MockMultipartFile file = xlsx("file", "chapters.xlsx", CHAPTER_HEADERS,
                List.<String[]>of(new String[]{"子章节", "根本不存在的父章节", null, null, null, null}));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成（存在跳过或失败项，详见明细）"))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value(containsString("未找到父章节「根本不存在的父章节」")));
        verify(chapterService, never()).updateById(any(CourseChapter.class));
    }

    @Test
    @DisplayName("POST /teacher/chapters/import-excel - 父标题写成本身：判为自引用，保持顶级")
    void importChaptersExcel_parentResolvesToSelf_keepsTopLevel() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(chapterService.list(any(Wrapper.class))).thenReturn(List.of());
        AtomicLong idGen = new AtomicLong(500L);
        when(chapterService.save(any(CourseChapter.class))).thenAnswer(inv -> {
            CourseChapter ch = inv.getArgument(0);
            ch.setId(idGen.incrementAndGet());
            return true;
        });
        // 按标题回查恰好命中自己（父标题 == 本行标题）→ 不能把自己设为父章节
        when(chapterService.getOne(any(Wrapper.class))).thenAnswer(inv -> chapter(501L, COURSE_ID, "自己"));

        MockMultipartFile file = xlsx("file", "chapters.xlsx", CHAPTER_HEADERS,
                List.<String[]>of(new String[]{"自己", "自己", null, null, null, null}));

        mockMvc.perform(multipart("/teacher/chapters/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value(containsString("已作为顶级章节导入")));
        verify(chapterService, never()).updateById(any(CourseChapter.class));
    }

    // ==================== Excel 知识点导入 ====================

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - 空文件 / 非 xlsx：均 400 且不查课程")
    void importKnowledgePointsExcel_fileGuard_rejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "kp.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(empty)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择要上传的 Excel 文件"));

        MockMultipartFile txt = new MockMultipartFile("file", "kp.txt", "text/plain", "名称\n".getBytes());
        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(txt)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持 .xlsx 格式的 Excel 文件"));

        verify(courseService, never()).getById(any());
        verify(knowledgePointService, never()).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - 只有表头：400「文件中没有可导入的知识点数据」")
    void importKnowledgePointsExcel_headerOnly_rejected400() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        MockMultipartFile file = headerOnlyFile(KNOWLEDGE_HEADERS);

        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("文件中没有可导入的知识点数据"));
    }

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - 课程不属于当前教师：403")
    void importKnowledgePointsExcel_foreignCourse_returns403() throws Exception {
        when(courseService.getById(FOREIGN_COURSE_ID)).thenReturn(course(FOREIGN_COURSE_ID, 999L));
        MockMultipartFile file = xlsx("file", "kp.xlsx", KNOWLEDGE_HEADERS,
                List.<String[]>of(new String[]{"栈", null, "中等", "8", "20", "concept", null}));

        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(file)
                        .param("courseId", String.valueOf(FOREIGN_COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(knowledgePointService, never()).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - 重要度缺省补 5、父知识点按名称回填")
    void importKnowledgePointsExcel_importanceDefaultAndParentBackfill() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        AtomicLong idGen = new AtomicLong(600L);
        when(knowledgePointService.save(any(KnowledgePoint.class))).thenAnswer(inv -> {
            KnowledgePoint p = inv.getArgument(0);
            p.setId(idGen.incrementAndGet());
            return true;
        });
        when(knowledgePointService.getOne(any(Wrapper.class))).thenReturn(point(601L, "栈"));

        MockMultipartFile file = xlsx("file", "kp.xlsx", KNOWLEDGE_HEADERS, List.of(
                // 重要度留空 → 缺省 5；类型中文写法归一化为小写 concept
                new String[]{"栈", null, "中等", null, "20", "概念", "LIFO 结构"},
                // 显式重要度 9，父知识点以名称引用
                new String[]{"表达式求值", "栈", "困难", "9", null, "skill", null}));

        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成"))
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.errors").doesNotExist());

        ArgumentCaptor<KnowledgePoint> saveCap = ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(knowledgePointService, times(2)).save(saveCap.capture());
        KnowledgePoint root = saveCap.getAllValues().get(0);
        assertEquals(COURSE_ID, root.getCourseId());
        assertEquals(Integer.valueOf(5), root.getImportance(), "重要度留空必须补默认 5（列 NOT NULL）");
        assertEquals("concept", root.getPointType(), "类型要归一化为库中权威小写取值");
        assertEquals("MEDIUM", root.getDifficultyLevel(), "「中等」须归一化为前端映射表里的 MEDIUM");
        assertEquals(Integer.valueOf(20), root.getEstimatedTime());
        assertEquals(Integer.valueOf(0), root.getOrderIndex());
        KnowledgePoint child = saveCap.getAllValues().get(1);
        assertEquals(Integer.valueOf(9), child.getImportance());
        assertEquals("HARD", child.getDifficultyLevel(), "「困难」须归一化为 HARD，不能让中文原样入库");
        assertEquals("skill", child.getPointType());
        assertEquals(Integer.valueOf(1), child.getOrderIndex());
        assertNull(child.getEstimatedTime());

        ArgumentCaptor<KnowledgePoint> updateCap = ArgumentCaptor.forClass(KnowledgePoint.class);
        verify(knowledgePointService).updateById(updateCap.capture());
        assertEquals(602L, updateCap.getValue().getId());
        assertEquals(601L, updateCap.getValue().getParentId());
    }

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - 同课程同名称重复：跳过并计数")
    void importKnowledgePointsExcel_duplicateNames_skipped() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of(point(9L, "已存在知识点")));
        AtomicLong idGen = new AtomicLong(700L);
        when(knowledgePointService.save(any(KnowledgePoint.class))).thenAnswer(inv -> {
            KnowledgePoint p = inv.getArgument(0);
            p.setId(idGen.incrementAndGet());
            return true;
        });

        MockMultipartFile file = xlsx("file", "kp.xlsx", KNOWLEDGE_HEADERS, List.of(
                new String[]{"已存在知识点", null, null, null, null, null, null},
                new String[]{"新知识点", null, null, null, null, null, null},
                new String[]{"新知识点", null, null, null, null, null, null}));

        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成（存在跳过或失败项，详见明细）"))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(0));
        verify(knowledgePointService, times(1)).save(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - save 返回 false：不计入成功数也不做父级回填")
    void importKnowledgePointsExcel_saveReturnsFalse_notCountedAsImported() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        when(knowledgePointService.save(any(KnowledgePoint.class))).thenReturn(false);

        MockMultipartFile file = xlsx("file", "kp.xlsx", KNOWLEDGE_HEADERS,
                List.<String[]>of(new String[]{"未落库知识点", "父知识点", null, null, null, null, null}));

        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                // 未落库不算失败也不算成功：既无跳过也无错误时仍是「导入完成」
                .andExpect(jsonPath("$.message").value("导入完成"))
                .andExpect(jsonPath("$.data.importedCount").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.errors").doesNotExist());

        verify(knowledgePointService, times(1)).save(any(KnowledgePoint.class));
        // 失败的条目不进 pendingParent，第二遍回填整段不应触发
        verify(knowledgePointService, never()).getOne(any(Wrapper.class));
        verify(knowledgePointService, never()).updateById(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - 单条失败 + 父知识点缺失：明细同时回显两类问题")
    void importKnowledgePointsExcel_failureAndMissingParent_reported() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        when(knowledgePointService.save(any(KnowledgePoint.class)))
                .thenThrow(new RuntimeException("名称含非法字符"))
                .thenAnswer(inv -> {
                    KnowledgePoint p = inv.getArgument(0);
                    p.setId(802L);
                    return true;
                });
        when(knowledgePointService.getOne(any(Wrapper.class))).thenReturn(null);

        MockMultipartFile file = xlsx("file", "kp.xlsx", KNOWLEDGE_HEADERS, List.of(
                new String[]{"坏知识点", null, null, null, null, null, null},
                new String[]{"孤儿知识点", "不存在的父名称", null, null, null, null, null}));

        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("导入完成（存在跳过或失败项，详见明细）"))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(2))
                .andExpect(jsonPath("$.data.errors[0]").value("第 1 条（坏知识点）：名称含非法字符"))
                .andExpect(jsonPath("$.data.errors[1]").value(containsString("已作为顶级导入")));
        verify(knowledgePointService, never()).updateById(any(KnowledgePoint.class));
    }

    @Test
    @DisplayName("POST /teacher/knowledge-points/import-excel - 父知识点解析为自身：保持顶级")
    void importKnowledgePointsExcel_parentResolvesToSelf_keepsTopLevel() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(List.of());
        when(knowledgePointService.save(any(KnowledgePoint.class))).thenAnswer(inv -> {
            KnowledgePoint p = inv.getArgument(0);
            p.setId(901L);
            return true;
        });
        when(knowledgePointService.getOne(any(Wrapper.class))).thenReturn(point(901L, "自己"));

        MockMultipartFile file = xlsx("file", "kp.xlsx", KNOWLEDGE_HEADERS,
                List.<String[]>of(new String[]{"自己", "自己", null, null, null, null, null}));

        mockMvc.perform(multipart("/teacher/knowledge-points/import-excel").file(file)
                        .param("courseId", String.valueOf(COURSE_ID))
                        .with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value(containsString("未找到父知识点「自己」")));
        verify(knowledgePointService, never()).updateById(any(KnowledgePoint.class));
    }

    // ==================== 章节更新：管理员放行 + 章节课程为 null ====================

    @Test
    @DisplayName("PUT /teacher/courses/{id}/chapters/{cid} - 管理员可跨课程改写（chapter.courseId 为空也判为不匹配）")
    void updateChapter_adminAndChapterWithoutCourse_handled() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, 999L));
        CourseChapter orphan = new CourseChapter();
        orphan.setId(31L);
        orphan.setCourseId(null);
        when(chapterService.getById(31L)).thenReturn(orphan);

        // 管理员跳过课程归属；但章节必须确实挂在该课程下 —— courseId 为 null 时 equals 为假 → 403
        mockMvc.perform(put("/teacher/courses/" + COURSE_ID + "/chapters/31").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改写\"}").with(withUser(ADMIN_ID, "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该章节"));
        verify(chapterService, never()).updateById(any(CourseChapter.class));
        // 管理员在 assertCourseOwner 提前返回，不该再查一次课程表
        verify(courseService, never()).getById(any());
    }

    @Test
    @DisplayName("DELETE /teacher/courses/{id}/chapters/{cid} - 章节不存在：404 且不调用 removeById")
    void deleteChapter_chapterMissing_returns404() throws Exception {
        when(courseService.getById(COURSE_ID)).thenReturn(course(COURSE_ID, TEACHER_ID));
        when(chapterService.getById(anyLong())).thenReturn(null);

        mockMvc.perform(delete("/teacher/courses/" + COURSE_ID + "/chapters/55").with(withUser(TEACHER_ID, "TEACHER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("章节不存在"));
        verify(chapterService, never()).removeById(any(Long.class));
    }
}
