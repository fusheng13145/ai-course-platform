package com.aicourse.dto;

import com.aicourse.entity.Course;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 开课任务创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code Course} 实体</b>：实体上的 {@code id}／{@code createdAt}／
 * {@code updatedAt} 是服务端字段；{@code semester}（学期名快照）与 {@code year}（由学期名解析）、
 * {@code approvalStatus}／{@code approvedBy}／{@code approvalComment}／{@code approvalTime} 是审批链
 * 的服务端字段，实体直绑等于允许客户端自批自审；{@code completedAt} 属于结课归档断言。这些列一律
 * 由服务端写入（见 {@code CourseServiceImpl#createCourse} 的 V13 审批流水口径）。</p>
 *
 * <p>更新是局部语义：字段为 {@code null} 表示「不改」。课程库稳定信息（名称／编号／学分／学院等）
 * 虽然保留在入参里以兼容「无 definitionId 的旧建课入口」，但 {@code updateCourse} 会统一置空，
 * 只能通过课程库端点修改。</p>
 */
@Schema(name = "CourseSaveDTO", description = "开课任务创建／更新入参；审批与归档字段不接受客户端传入")
@Data
public class CourseSaveDTO {

    // ---- 课程库引用（V28 三层模型）----

    @Schema(description = "课程库定义ID；教师建课时必填（缺省由旧入口按课程字段生成）", example = "12")
    private Long definitionId;

    @Schema(description = "学期ID；缺省时由服务层取当前 ACTIVE 学期", example = "3")
    private Long semesterId;

    @Schema(description = "授课教师ID；仅管理员代建时生效，教师建课固定为本人", example = "7")
    private Long teacherId;

    // ---- 兼容旧入口（无 definitionId 时用于生成课程库定义；更新时被服务层置空）----

    @Schema(description = "课程编号（旧建课入口使用）", example = "CS101")
    private String code;

    @Schema(description = "课程名称（旧建课入口使用）")
    private String name;

    @Schema(description = "课程简介（旧建课入口使用）")
    private String description;

    @Schema(description = "学分（旧建课入口使用）", example = "3")
    private BigDecimal credits;

    @Schema(description = "所属学院ID（教师建课由负责人学院覆盖）", example = "1")
    private Long collegeId;

    @Schema(description = "课程分类（旧建课入口使用）", example = "专业核心")
    private String category;

    @Schema(description = "难度（旧建课入口使用）", example = "medium")
    private String difficulty;

    @Schema(description = "封面图地址（旧建课入口使用）")
    private String coverImage;

    @Schema(description = "教学大纲地址（旧建课入口使用）")
    private String syllabusUrl;

    @Schema(description = "标签（旧建课入口使用）")
    private String tags;

    // ---- 开课任务自身业务字段（更新可改）----

    @Schema(description = "课程价格", example = "0")
    private BigDecimal price;

    @Schema(description = "选课类型：FREE-自由选课，APPROVAL-需审批；缺省补 FREE",
            allowableValues = {"FREE", "APPROVAL", "ASSIGNED"})
    private String enrollmentType;

    @Schema(description = "最大选课人数；缺省补 100", example = "100")
    private Integer maxStudents;

    @Schema(description = "学生题库刷题开关；缺省补 false")
    private Boolean questionPracticeEnabled;

    @Schema(description = "课程状态；缺省补 ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
    private String status;

    @Schema(description = "是否启用；缺省补 true")
    private Boolean isActive;

    @Schema(description = "开课时间；缺省取学期开始日", example = "2026-09-01T00:00:00")
    private LocalDateTime startDate;

    @Schema(description = "结课时间；缺省取学期结束日 23:59:59", example = "2027-01-15T23:59:59")
    private LocalDateTime endDate;

    @Schema(description = "计划教学班数（1~50，缺省 1）；审批通过后按此生成教学班占位", example = "2")
    private Integer plannedSections;

    public Course toEntity() {
        Course course = new Course();
        applyTo(course);
        return course;
    }

    public void applyTo(Course course) {
        course.setDefinitionId(definitionId);
        course.setSemesterId(semesterId);
        course.setTeacherId(teacherId);
        course.setCode(code);
        course.setName(name);
        course.setDescription(description);
        course.setCredits(credits);
        course.setCollegeId(collegeId);
        course.setCategory(category);
        course.setDifficulty(difficulty);
        course.setCoverImage(coverImage);
        course.setSyllabusUrl(syllabusUrl);
        course.setTags(tags);
        course.setPrice(price);
        course.setEnrollmentType(enrollmentType);
        course.setMaxStudents(maxStudents);
        course.setQuestionPracticeEnabled(questionPracticeEnabled);
        course.setStatus(status);
        course.setIsActive(isActive);
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        course.setPlannedSections(plannedSections);
    }
}
