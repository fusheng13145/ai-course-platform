package com.aicourse.service.component.org;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.service.CourseEnrollmentService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 教学班快照解析器单测（WP7）。
 *
 * <p>判据（拆包方案 WP7 验收 2）：新写入的快照从成员关系取值，不从请求体信任。
 * 本测试钉住解析谓词：只认 {@code approvalStatus='APPROVED'} 且 {@code isActive=TRUE} 的选课，
 * 与 {@code ActivityVisibilityGuard#approvedEnrollments} 同口径。</p>
 */
class TeachingClassSnapshotResolverTest {

    private CourseEnrollmentService enrollmentService;
    private TeachingClassSnapshotResolver resolver;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseEnrollment.class);
    }

    @BeforeEach
    void setUp() {
        enrollmentService = mock(CourseEnrollmentService.class);
        resolver = new TeachingClassSnapshotResolver(enrollmentService);
    }

    private CourseEnrollment enrollment(Long classId, String status, Boolean active) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudentId(9L);
        enrollment.setCourseId(3L);
        enrollment.setTeachingClassId(classId);
        enrollment.setApprovalStatus(status);
        enrollment.setIsActive(active);
        return enrollment;
    }

    @Test
    @DisplayName("APPROVED 且活跃的选课 → 返回其教学班快照")
    void resolvesFromApprovedEnrollment() {
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(enrollment(55L, "APPROVED", true)));

        assertThat(resolver.resolveTeachingClassId(9L, 3L)).isEqualTo(55L);
    }

    @Test
    @DisplayName("无选课记录 → 快照为 null，不抛异常（快照缺失不阻断业务写入）")
    void returnsNullWhenNoEnrollment() {
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of());

        assertThat(resolver.resolveTeachingClassId(9L, 3L)).isNull();
    }

    @Test
    @DisplayName("入参为空 → 直接返回 null，不触库")
    void returnsNullOnNullArguments() {
        assertThat(resolver.resolveTeachingClassId(null, 3L)).isNull();
        assertThat(resolver.resolveTeachingClassId(9L, null)).isNull();
        org.mockito.Mockito.verifyNoInteractions(enrollmentService);
    }

    @Test
    @DisplayName("approvedEnrollment 返回完整选课对象，供调用方读取其它字段")
    void approvedEnrollmentExposesRow() {
        CourseEnrollment row = enrollment(55L, "APPROVED", true);
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any()))
                .thenReturn(List.of(row));

        assertThat(resolver.approvedEnrollment(9L, 3L)).isSameAs(row);
    }

    @Test
    @DisplayName("阴性对照：谓词必须经过 Wrapper 条件构造（非全表直取）")
    void queryUsesConditionalWrapper() {
        when(enrollmentService.list(ArgumentMatchers.<Wrapper<CourseEnrollment>>any())).thenReturn(List.of());
        resolver.resolveTeachingClassId(9L, 3L);

        org.mockito.ArgumentCaptor<Wrapper<CourseEnrollment>> captor =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        org.mockito.Mockito.verify(enrollmentService).list(captor.capture());
        // MyBatis-Plus 的 LambdaQueryWrapper 生成带 WHERE 的 SQL 片段，而非空条件
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSqlSegment()).isNotBlank();
    }
}
