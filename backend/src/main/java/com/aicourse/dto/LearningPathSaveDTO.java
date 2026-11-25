package com.aicourse.dto;

import com.aicourse.entity.LearningPath;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习路径创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code LearningPath} 实体</b>：实体直绑时客户端可以自带
 * {@code studentId}（把路径写给别人＝跨人越权）、{@code status}／{@code currentStage}／
 * {@code completionRate}／{@code actualCompletionDate}（绕过 pause/resume/complete/advance 直接改进度结论）、
 * 以及 {@code id}／{@code createdAt}。DTO 只暴露路径的业务描述字段；归属与进度状态由控制器
 * （登录用户）与专用推进接口决定。</p>
 *
 * <p>更新是局部语义：字段为 {@code null} 表示「不改」。</p>
 */
@Schema(name = "LearningPathSaveDTO", description = "学习路径创建／更新入参；studentId/进度状态/主键不接受客户端传入")
@Data
public class LearningPathSaveDTO {

    @Schema(description = "课程ID", example = "4007")
    private Long courseId;

    @Schema(description = "路径名称", example = "计算机网络 7 天冲刺")
    private String pathName;

    @Schema(description = "路径说明")
    private String description;

    @Schema(description = "阶段划分（JSON 文本）")
    private String stages;

    @Schema(description = "计划详情（JSON 文本）")
    private String planDetail;

    @Schema(description = "每日任务（JSON 文本）")
    private String dailyTasks;

    @Schema(description = "学习目标")
    private String learningGoal;

    @Schema(description = "总阶段数", example = "3")
    private Integer totalStages;

    @Schema(description = "预计完成时间", example = "2026-12-01T00:00:00")
    private LocalDateTime estimatedCompletionDate;

    public LearningPath toEntity() {
        LearningPath path = new LearningPath();
        applyTo(path);
        return path;
    }

    public void applyTo(LearningPath path) {
        path.setCourseId(courseId);
        path.setPathName(pathName);
        path.setDescription(description);
        path.setStages(stages);
        path.setPlanDetail(planDetail);
        path.setDailyTasks(dailyTasks);
        path.setLearningGoal(learningGoal);
        path.setTotalStages(totalStages);
        path.setEstimatedCompletionDate(estimatedCompletionDate);
    }
}
