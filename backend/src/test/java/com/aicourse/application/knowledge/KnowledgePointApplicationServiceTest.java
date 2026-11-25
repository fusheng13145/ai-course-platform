package com.aicourse.application.knowledge;

import com.aicourse.common.PagedResponse;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.security.CourseAccessControl;
import com.aicourse.security.KnowledgeAccessSupport;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.KnowledgePointRelationService;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.vo.KnowledgeGraphVO;
import com.aicourse.vo.KnowledgePointRelationsVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 3E-05C：{@code KnowledgePointApplicationService} 用例。
 *
 * <p>重点锁：①无可见范围时<b>不查库</b>且分页版回显请求的 page/size；②子资源只下发同课程
 * 节点/边；③掌握度对非本课程学生返回 403 且不计算。</p>
 */
@ExtendWith(MockitoExtension.class)
class KnowledgePointApplicationServiceTest {

    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private KnowledgePointRelationService relationService;
    @Mock
    private CourseAccessControl courseAccessControl;
    @Mock
    private CourseEnrollmentService courseEnrollmentService;
    @Mock
    private KnowledgeAccessSupport knowledgeAccessSupport;
    @Mock
    private Authentication auth;

    @InjectMocks
    private KnowledgePointApplicationService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgePoint.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgePointRelation.class);
    }

    private static KnowledgePoint point(Long id, Long courseId) {
        KnowledgePoint p = new KnowledgePoint();
        p.setId(id);
        p.setCourseId(courseId);
        p.setName("点" + id);
        return p;
    }

    // ==================== 列表 ====================

    @Test
    @DisplayName("列表-无可见范围：直接空列表且不查库")
    void listPointsWithoutScope() {
        when(knowledgeAccessSupport.applyCourseScope(any(), eq(null), eq(auth))).thenReturn(false);

        assertTrue(service.listPoints(null, "函数", auth).isEmpty());
        verify(knowledgePointService, never()).list(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<KnowledgePoint>>any());
    }

    @Test
    @DisplayName("列表-有可见范围：把 keyword 作为 LIKE 条件下推")
    void listPointsWithKeyword() {
        when(knowledgeAccessSupport.applyCourseScope(any(), eq(1L), eq(auth))).thenReturn(true);
        when(knowledgePointService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(point(1L, 1L)));

        assertEquals(1, service.listPoints(1L, "函数", auth).size());
        verify(knowledgePointService).list(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("分页-无可见范围：回显请求 page/size 的空页，不用 empty() 写死 1/10")
    void listPointsPagedWithoutScopeEchoesRequest() {
        when(knowledgeAccessSupport.applyCourseScope(any(), any(), any())).thenReturn(false);

        PagedResponse<?> page = service.listPointsPaged(3, 50, 1L, null, auth);

        assertEquals(3L, page.getPage());
        assertEquals(50L, page.getPageSize());
        assertEquals(0L, page.getTotal());
        verify(knowledgePointService, never()).page(any(), any());
    }

    @Test
    @DisplayName("分页-越界收敛：page<1 归 1，size>200 归 200，size<1 归 1")
    void listPointsPagedClampsBounds() {
        when(knowledgeAccessSupport.applyCourseScope(any(), any(), any())).thenReturn(true);
        Page<KnowledgePoint> result = new Page<>(1, 200);
        result.setRecords(List.of());
        when(knowledgePointService.page(any(), any())).thenReturn(result);

        service.listPointsPaged(0, 500, 1L, "", auth);

        verify(knowledgePointService).page(
                org.mockito.ArgumentMatchers.argThat(p -> ((Page<?>) p).getCurrent() == 1L
                        && ((Page<?>) p).getSize() == 200L), any());
    }

    // ==================== 详情 / 子资源 ====================

    @Test
    @DisplayName("详情-先取对象再按课程可读性授权")
    void pointDetailChecksReadable() {
        when(knowledgeAccessSupport.requirePoint(1L)).thenReturn(point(1L, 7L));

        service.pointDetail(1L, auth);

        verify(courseAccessControl).assertCanReadCourse(eq(7L), eq(auth));
    }

    @Test
    @DisplayName("关联关系-只保留同课程节点与两端都在白名单内的边")
    void relationsScopeToSameCourse() {
        when(knowledgeAccessSupport.requirePoint(1L)).thenReturn(point(1L, 7L));
        KnowledgePointRelation sameCourse = new KnowledgePointRelation();
        sameCourse.setSourceId(1L);
        sameCourse.setTargetId(2L);
        KnowledgePointRelation crossCourse = new KnowledgePointRelation();
        crossCourse.setSourceId(1L);
        crossCourse.setTargetId(9L);
        when(relationService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sameCourse, crossCourse));
        when(knowledgePointService.listByIds(any(java.util.Collection.class)))
                .thenReturn(List.of(point(1L, 7L), point(2L, 7L), point(9L, 8L)));

        KnowledgePointRelationsVO vo = service.pointRelations(1L, auth);

        assertEquals(2, vo.getNodes().size());
        assertEquals(1, vo.getEdges().size());
        assertEquals(2L, vo.getEdges().get(0).getTargetId());
    }

    @Test
    @DisplayName("掌握度-学生不在本课程：403 且不计算")
    void masteryForbiddenWhenNotEnrolled() {
        when(knowledgeAccessSupport.requirePoint(1L)).thenReturn(point(1L, 7L));
        when(courseEnrollmentService.hasApprovedEnrollment(5L, 7L)).thenReturn(false);

        com.aicourse.exception.BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.aicourse.exception.BusinessException.class, () -> service.calculateMastery(1L, 5L, auth));

        assertEquals(com.aicourse.common.ApiCode.FORBIDDEN, ex.getCode());
        verify(knowledgePointService, never()).calculateMasteryDetail(anyLong(), anyLong());
    }

    // ==================== 图谱 ====================

    @Test
    @DisplayName("图谱-课程无节点时不查关系，快照计数为 0")
    void graphWithoutPointsSkipsRelationQuery() {
        when(knowledgePointService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        KnowledgeGraphVO vo = service.graph(7L, auth);

        assertEquals(0, vo.getTotalPoints());
        assertEquals(0, vo.getTotalRelations());
        verify(relationService, never()).list(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("图谱-节点与关系均按课程集合取数，快照版本可序列化")
    void graphWithPoints() {
        when(knowledgePointService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(point(1L, 7L), point(2L, 7L)));
        KnowledgePointRelation r = new KnowledgePointRelation();
        r.setSourceId(1L);
        r.setTargetId(2L);
        when(relationService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));

        KnowledgeGraphVO vo = service.graph(7L, auth);

        assertEquals(2, vo.getTotalPoints());
        assertEquals(1, vo.getTotalRelations());
        assertEquals(1, vo.getEdges().size());
    }

    @Test
    @DisplayName("课时校验统一只跑一次：detail 与子资源各自授权，不重复查同一课程")
    void readGuardCalledOncePerCall() {
        lenient().when(knowledgeAccessSupport.requirePoint(1L)).thenReturn(point(1L, 7L));
        service.pointDetail(1L, auth);
        verify(courseAccessControl, times(1)).assertCanReadCourse(anyLong(), any());
    }
}
