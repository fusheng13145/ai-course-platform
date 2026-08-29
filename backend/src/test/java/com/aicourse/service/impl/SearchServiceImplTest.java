package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Note;
import com.aicourse.entity.Question;
import com.aicourse.service.CourseService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.NoteService;
import com.aicourse.service.QuestionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全站搜索服务单元测试（D6）。
 * 覆盖：多类型聚合 / 名称优先加权 / 空关键字与空词元防护 / note 仅本人。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceImplTest {

    @Mock private KnowledgePointService knowledgePointService;
    @Mock private QuestionService questionService;
    @Mock private CourseService courseService;
    @Mock private NoteService noteService;
    @InjectMocks private SearchServiceImpl service;

    private KnowledgePoint kp(Long id, String name, String desc) {
        KnowledgePoint k = new KnowledgePoint();
        k.setId(id);
        k.setName(name);
        k.setDescription(desc);
        return k;
    }

    private Course course(Long id, String name, String code, String desc) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        c.setCode(code);
        c.setDescription(desc);
        return c;
    }

    private Question question(Long id, String content) {
        Question q = new Question();
        q.setId(id);
        q.setContent(content);
        return q;
    }

    private Note note(Long id, String title, String content) {
        Note n = new Note();
        n.setId(id);
        n.setTitle(title);
        n.setContent(content);
        return n;
    }

    @Test
    @DisplayName("聚合搜索-知识/题目/课程/笔记四类型返回扁平结果")
    void search_aggregatesAllTypes() {
        when(knowledgePointService.list(any(Wrapper.class)))
                .thenReturn(List.of(kp(1L, "TCP 三次握手", "TCP 连接的建立过程详解")));
        when(questionService.list(any(Wrapper.class)))
                .thenReturn(List.of(question(2L, "什么是 TCP 三次握手？")));
        when(courseService.list(any(Wrapper.class)))
                .thenReturn(List.of(course(3L, "TCP/IP 网络原理", "CN101", "TCP 与网络基础课程")));
        when(noteService.list(any(Wrapper.class)))
                .thenReturn(List.of(note(4L, "TCP 笔记", "三次握手与四次挥手")));

        List<Map<String, Object>> result = service.search(
                "tcp 三次握手", List.of("knowledge", "question", "course", "note"), 9L, 10);

        assertEquals(4, result.size());
        // 名称命中优先：知识点(TCP 三次握手)应排最前
        Map<String, Object> first = result.get(0);
        assertEquals("knowledge", first.get("type"));
        assertEquals(1L, first.get("id"));
        assertTrue((double) first.get("score") > 0);
    }

    @Test
    @DisplayName("仅检索指定类型-不查询其他类型")
    void search_filtersByType() {
        when(knowledgePointService.list(any(Wrapper.class)))
                .thenReturn(List.of(kp(1L, "OSI 模型", "七层参考模型")));

        List<Map<String, Object>> result = service.search("OSI", List.of("knowledge"), 9L, 10);

        assertEquals(1, result.size());
        assertEquals("knowledge", result.get(0).get("type"));
        // 仅 knowledge 类型时不应触发其他 Service 查询
        verify(questionService, never()).list(any(Wrapper.class));
    }

    @Test
    @DisplayName("空关键字返回空列表")
    void search_blankKeyword_empty() {
        assertTrue(service.search("   ", List.of("knowledge"), 9L, 10).isEmpty());
        assertTrue(service.search(null, List.of("knowledge"), 9L, 10).isEmpty());
    }

    @Test
    @DisplayName("无匹配-各类型返回空-不抛异常")
    void search_noMatch_emptyResults() {
        when(knowledgePointService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(questionService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(courseService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(noteService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = service.search(
                "不存在的词xyz", List.of("knowledge", "question", "course", "note"), 9L, 10);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("limit 截断-只返回前 N 条")
    void search_respectsLimit() {
        when(knowledgePointService.list(any(Wrapper.class)))
                .thenReturn(List.of(
                        kp(1L, "UDP 协议", "UDP 报文格式"),
                        kp(2L, "UDP 校验", "UDP 校验和计算"),
                        kp(3L, "UDP 端口", "UDP 端口号")));

        List<Map<String, Object>> result = service.search("UDP", List.of("knowledge"), 9L, 2);
        assertEquals(2, result.size());
    }
}
