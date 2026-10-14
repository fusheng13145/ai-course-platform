package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseChapter;
import com.aicourse.entity.Resource;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.CourseChapterService;
import com.aicourse.service.CourseService;
import com.aicourse.service.RAGService;
import com.aicourse.service.ResourceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 课程知识库编排服务单元测试。
 * <p>
 * 覆盖四条主路径：
 * <ol>
 *   <li>{@code rag.enabled=false} → 只枚举不写入（mode=LOCAL，绝不调用引擎，不误导为「已入库」）；</li>
 *   <li>引擎可用 → 幂等写入（按文档名去重）+ 重建索引（mode=ENGINE）；</li>
 *   <li>引擎不可用（列表/写入/建索引任一返回 503）→ mode=UNAVAILABLE + code=503（失败可见，不静默）；</li>
 *   <li>课程不存在 → 抛 {@code BusinessException(404)}；课程无可入库资料 → total=0 且不触碰引擎。</li>
 * </ol>
 * 说明：断言用 {@code LambdaQueryWrapper} 桩匹配（MyBatis-Plus 的列名解析是惰性的，
 * 单测中构造 wrapper 不会触发 TableInfo 解析），因此无需启动 Spring 上下文。
 */
class CourseKnowledgeBaseServiceImplTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private final RAGService ragService = mock(RAGService.class);
    private final CourseService courseService = mock(CourseService.class);
    private final CourseChapterService courseChapterService = mock(CourseChapterService.class);
    private final ResourceService resourceService = mock(ResourceService.class);

    private CourseKnowledgeBaseServiceImpl service(boolean ragEnabled) {
        return new CourseKnowledgeBaseServiceImpl(
                ragEnabled, ragService, courseService, courseChapterService, resourceService);
    }

    // ---------- 1. 引擎未启用 ----------

    @Test
    @DisplayName("rag.enabled=false：枚举课程资料但不写入引擎，mode=LOCAL")
    void disabled_enumerates_without_writing() throws Exception {
        stubCourseWithChapterAndResource();
        when(courseService.getById(1L)).thenReturn(course());

        JsonNode node = read(service(false).buildFromCourse(1L));

        assertEquals("LOCAL", node.get("mode").asText());
        assertEquals(3, node.get("total").asInt());
        assertEquals(0, node.get("added").asInt());
        assertEquals(0, node.get("skipped").asInt());
        assertEquals(3, node.get("documents").size());
        assertEquals("[课程简介] 数据结构", node.get("documents").get(0).get("name").asText());
        assertEquals("[章节] 第一章 线性表", node.get("documents").get(1).get("name").asText());
        assertEquals("章节", node.get("documents").get(1).get("source").asText());
        assertEquals("[资源] 讲义.pdf", node.get("documents").get(2).get("name").asText());
        assertEquals("资源", node.get("documents").get(2).get("source").asText());
        assertTrue(node.get("note").asText().contains("rag.enabled=false"));
        assertNull(node.get("code"));

        verify(ragService, never()).addDocument(anyLong(), anyString(), anyString());
        verify(ragService, never()).listDocuments(any());
        verify(ragService, never()).buildIndex(anyLong());
    }

    @Test
    @DisplayName("课程无可入库资料：total=0 且完全不触碰引擎")
    void empty_candidates_skip_engine() throws Exception {
        Course empty = new Course();
        empty.setId(7L);
        empty.setName("空课程");
        when(courseService.getById(7L)).thenReturn(empty);
        when(courseChapterService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(resourceService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        JsonNode node = read(service(false).buildFromCourse(7L));

        assertEquals(0, node.get("total").asInt());
        assertEquals("LOCAL", node.get("mode").asText());
        assertTrue(node.get("note").asText().contains("暂无可入库资料"));
        verifyNoInteractions(ragService);
    }

    // ---------- 2. 引擎可用 ----------

    @Test
    @DisplayName("引擎可用：写入全部资料并重建索引，mode=ENGINE")
    void enabled_writes_documents_and_builds_index() throws Exception {
        stubCourseWithChapterAndResource();
        when(courseService.getById(1L)).thenReturn(course());
        when(ragService.listDocuments(1L)).thenReturn("{\"documents\":[]}");
        when(ragService.addDocument(eq(1L), anyString(), anyString()))
                .thenReturn("{\"id\":1,\"documentName\":\"x\",\"mode\":\"ENGINE\"}");
        when(ragService.buildIndex(1L)).thenReturn("{\"success\":true,\"indexedChunks\":9}");

        JsonNode node = read(service(true).buildFromCourse(1L));

        assertEquals("ENGINE", node.get("mode").asText());
        assertEquals(3, node.get("total").asInt());
        assertEquals(3, node.get("added").asInt());
        assertEquals(0, node.get("skipped").asInt());
        assertEquals(9, node.get("indexedChunks").asInt());
        assertNull(node.get("code"));

        verify(ragService, times(3)).addDocument(eq(1L), anyString(), anyString());
        verify(ragService, times(1)).buildIndex(1L);
    }

    @Test
    @DisplayName("引擎可用：知识库已存在的文档按文档名幂等跳过")
    void enabled_skips_existing_documents() throws Exception {
        stubCourseWithChapterAndResource();
        when(courseService.getById(1L)).thenReturn(course());
        when(ragService.listDocuments(1L))
                .thenReturn("{\"documents\":[{\"documentName\":\"[章节] 第一章 线性表\"}]}");
        when(ragService.addDocument(eq(1L), anyString(), anyString()))
                .thenReturn("{\"id\":1,\"documentName\":\"x\",\"mode\":\"ENGINE\"}");
        when(ragService.buildIndex(1L)).thenReturn("{\"success\":true,\"indexedChunks\":4}");

        JsonNode node = read(service(true).buildFromCourse(1L));

        assertEquals("ENGINE", node.get("mode").asText());
        assertEquals(2, node.get("added").asInt());
        assertEquals(1, node.get("skipped").asInt());

        verify(ragService, times(2)).addDocument(eq(1L), anyString(), anyString());
    }

    // ---------- 3. 引擎不可用 ----------

    @Test
    @DisplayName("读已有文档列表即 503：mode=UNAVAILABLE 且不发起任何写入")
    void unavailable_when_list_documents_fails() throws Exception {
        stubCourseWithChapterAndResource();
        when(courseService.getById(1L)).thenReturn(course());
        when(ragService.listDocuments(1L)).thenReturn("{\"error\":\"引擎未接入\",\"code\":503}");

        JsonNode node = read(service(true).buildFromCourse(1L));

        assertEquals("UNAVAILABLE", node.get("mode").asText());
        assertEquals(503, node.get("code").asInt());
        assertEquals(0, node.get("added").asInt());
        assertNotNull(node.get("error"));
        assertEquals(3, node.get("total").asInt());

        verify(ragService, never()).addDocument(anyLong(), anyString(), anyString());
        verify(ragService, never()).buildIndex(anyLong());
    }

    @Test
    @DisplayName("写入阶段 503：mode=UNAVAILABLE、added=0，且不重建索引")
    void unavailable_when_add_document_fails() throws Exception {
        stubCourseWithChapterAndResource();
        when(courseService.getById(1L)).thenReturn(course());
        when(ragService.listDocuments(1L)).thenReturn("{\"documents\":[]}");
        when(ragService.addDocument(eq(1L), anyString(), anyString()))
                .thenReturn("{\"error\":\"引擎未接入\",\"code\":503}");

        JsonNode node = read(service(true).buildFromCourse(1L));

        assertEquals("UNAVAILABLE", node.get("mode").asText());
        assertEquals(503, node.get("code").asInt());
        assertEquals(0, node.get("added").asInt());
        assertTrue(node.get("note").asText().contains("未能写入"));

        verify(ragService, never()).buildIndex(anyLong());
    }

    @Test
    @DisplayName("索引阶段 503：文档已写入但 mode=UNAVAILABLE，note 如实说明索引未重建")
    void unavailable_when_build_index_fails() throws Exception {
        stubCourseWithChapterAndResource();
        when(courseService.getById(1L)).thenReturn(course());
        when(ragService.listDocuments(1L)).thenReturn("{\"documents\":[]}");
        when(ragService.addDocument(eq(1L), anyString(), anyString()))
                .thenReturn("{\"id\":1,\"mode\":\"ENGINE\"}");
        when(ragService.buildIndex(1L)).thenReturn("{\"error\":\"引擎未接入\",\"code\":503}");

        JsonNode node = read(service(true).buildFromCourse(1L));

        assertEquals("UNAVAILABLE", node.get("mode").asText());
        assertEquals(3, node.get("added").asInt());
        assertTrue(node.get("note").asText().contains("索引"));
    }

    // ---------- 4. 入参边界 ----------

    @Test
    @DisplayName("课程不存在：抛 BusinessException(404)")
    void course_not_found_throws_404() {
        when(courseService.getById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(false).buildFromCourse(99L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("courseId 为 null：同样抛 BusinessException(404)，不触碰引擎")
    void null_course_id_throws_404() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(false).buildFromCourse(null));
        assertEquals(404, ex.getCode());
        verifyNoInteractions(ragService);
    }

    // ---------- 夹具 ----------

    private void stubCourseWithChapterAndResource() {
        when(courseChapterService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(chapter(11L, "第一章 线性表", "顺序表与链表的实现", 1)));
        when(resourceService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(resource(21L, "讲义.pdf", "第一章讲义", Resource.MODERATION_NORMAL)));
    }

    private Course course() {
        Course c = new Course();
        c.setId(1L);
        c.setName("数据结构");
        c.setDescription("本课程讲授线性表、树与图");
        return c;
    }

    private CourseChapter chapter(Long id, String title, String content, Integer orderIndex) {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(1L);
        chapter.setTitle(title);
        chapter.setContent(content);
        chapter.setOrderIndex(orderIndex);
        return chapter;
    }

    private Resource resource(Long id, String fileName, String description, String moderationStatus) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setCourseId(1L);
        resource.setFileName(fileName);
        resource.setDescription(description);
        resource.setModerationStatus(moderationStatus);
        return resource;
    }

    private JsonNode read(String raw) throws Exception {
        return OM.readTree(raw);
    }
}
