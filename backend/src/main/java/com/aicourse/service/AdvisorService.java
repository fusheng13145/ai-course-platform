package com.aicourse.service;

import com.aicourse.entity.AdminClass;
import com.aicourse.vo.AdvisorClassProfileVO;

import java.util.List;

/**
 * 班主任画像服务（B-12／P-08 WP9，6A 口径：只出聚合值）。
 */
public interface AdvisorService {

    /** 当前教师担任班主任的行政班列表（active）。 */
    List<AdminClass> listMyClasses(Long advisorId);

    /**
     * 班级聚合画像。ACL：班主任本人（{@code AdminClassAccessControl.assertAdminClassAdvisor}）
     * 或管理员按学院作用域（院系仅本院、校级全校，能力矩阵「班级画像」行）。
     */
    AdvisorClassProfileVO classProfile(Long adminClassId, Long operatorId, boolean adminCaller);

    /**
     * 学生预警明细（交接文档 §5.6「学生预警摘要」，6A 允许的画像外个人面）。
     * <p>口径与分析侧一致：进度 &lt; 40 或 总评 &lt; 60。ACL 同 {@link #classProfile}。</p>
     *
     * @return 每行：studentId/studentName/courseId/courseName/progress/finalScore/reasons
     */
    java.util.List<java.util.Map<String, Object>> classAlerts(Long adminClassId, Long operatorId, boolean adminCaller);
}
