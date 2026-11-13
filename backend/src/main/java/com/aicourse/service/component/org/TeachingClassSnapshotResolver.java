package com.aicourse.service.component.org;

import com.aicourse.entity.CourseEnrollment;
import com.aicourse.service.CourseEnrollmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 教学班快照解析器（B-12／P-08 WP7，2026-11-07）。
 * <p>
 * WP7 要求提交／考试会话／完成记录／成绩明细在<b>写入时</b>带上学生当时所在的教学班
 * （{@code teaching_class_id} 快照），并明确两条纪律：快照值<b>从成员关系解析，不从请求体信任</b>；
 * 写入之后<b>不随成员关系变化改写</b>（学生转班后历史记录仍归属原教学班）。
 * <p>
 * 成员关系判定与 {@code ActivityVisibilityGuard#approvedEnrollments} 逐字同口径：
 * {@code approvalStatus = 'APPROVED'} 且 {@code isActive = TRUE}。这里刻意做成独立组件而不是
 * 直接复用守卫的私有方法——守卫回答「能不能看」，本组件回答「属于哪个班」；两者共享的唯一
 * 事实是选课成员关系本身，谓词若分叉应由测试钉住（见 {@code TeachingClassSnapshotResolverTest}）。
 */
@Component
public class TeachingClassSnapshotResolver {

    /** 与 ActivityVisibilityGuard#approvedEnrollments 同口径的审批状态值。 */
    static final String STATUS_APPROVED = "APPROVED";

    private final CourseEnrollmentService enrollmentService;

    public TeachingClassSnapshotResolver(CourseEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    /**
     * 解析学生在某课程的 APPROVED 选课记录（活跃）。
     *
     * @return 无有效选课（未选课、待审批、已退课）时返回 {@code null}——快照列为可空，
     *         调用方照常落库，不因快照缺失拒绝业务写入
     */
    public CourseEnrollment approvedEnrollment(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            return null;
        }
        List<CourseEnrollment> rows = enrollmentService.list(new LambdaQueryWrapper<CourseEnrollment>()
                .eq(CourseEnrollment::getStudentId, studentId)
                .eq(CourseEnrollment::getCourseId, courseId)
                .eq(CourseEnrollment::getApprovalStatus, STATUS_APPROVED)
                .eq(CourseEnrollment::getIsActive, true));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 解析快照值：学生当前 APPROVED 选课上的教学班ID。
     *
     * @return 教学班ID；无有效选课或选课尚无班级归属（理论上默认班不变量下不会发生）时 {@code null}
     */
    public Long resolveTeachingClassId(Long studentId, Long courseId) {
        CourseEnrollment enrollment = approvedEnrollment(studentId, courseId);
        return enrollment == null ? null : enrollment.getTeachingClassId();
    }
}
