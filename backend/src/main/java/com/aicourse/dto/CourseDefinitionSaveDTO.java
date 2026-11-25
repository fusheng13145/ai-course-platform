package com.aicourse.dto;

import com.aicourse.entity.CourseDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程库创建／更新入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code CourseDefinition} 实体</b>：实体上有 {@code id}／{@code createdBy}／
 * {@code createdAt}／{@code updatedAt} 这类服务端字段，实体直绑等于把主键与创建人交给客户端指定
 * （可注入主键写入脏行、可伪造课程归属人）。DTO 只暴露课程库业务字段，服务端字段由
 * {@code CourseDefinitionServiceImpl} 自己填。</p>
 *
 * <p>更新是局部语义：字段为 {@code null} 表示「不改」，与 service 的逐字段白名单拷贝一致。</p>
 */
@Schema(name = "CourseDefinitionSaveDTO", description = "课程库创建／更新入参；服务端字段（id/createdBy/时间戳）不接受客户端传入")
@Data
public class CourseDefinitionSaveDTO {

    @Schema(description = "课程编号；创建时必填，更新时留空表示不改", example = "CS101")
    private String code;

    @Schema(description = "课程名称", example = "计算机网络")
    private String name;

    @Schema(description = "课程简介")
    private String description;

    @Schema(description = "学分；创建时缺省由服务层补 0", example = "3")
    private BigDecimal credits;

    @Schema(description = "学时", example = "48")
    private Integer hours;

    @Schema(description = "课程性质；创建时缺省由服务层补「选修」", example = "必修")
    private String nature;

    @Schema(description = "所属学院ID；教师只能在自己学院建课", example = "1")
    private Long collegeId;

    @Schema(description = "课程类别", example = "专业核心")
    private String category;

    @Schema(description = "难度", example = "medium")
    private String difficulty;

    @Schema(description = "先修课程")
    private String prerequisite;

    @Schema(description = "封面图地址")
    private String coverImage;

    @Schema(description = "教学大纲地址")
    private String syllabusUrl;

    @Schema(description = "标签")
    private String tags;

    @Schema(description = "状态；只接受 ACTIVE / INACTIVE（大小写不敏感），创建时缺省由服务层补 ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE"})
    @Pattern(regexp = "(?i)ACTIVE|INACTIVE", message = "课程定义状态只能是 ACTIVE 或 INACTIVE")
    private String status;

    public CourseDefinition toEntity() {
        CourseDefinition definition = new CourseDefinition();
        applyTo(definition);
        return definition;
    }

    public void applyTo(CourseDefinition definition) {
        definition.setCode(code);
        definition.setName(name);
        definition.setDescription(description);
        definition.setCredits(credits);
        definition.setHours(hours);
        definition.setNature(nature);
        definition.setCollegeId(collegeId);
        definition.setCategory(category);
        definition.setDifficulty(difficulty);
        definition.setPrerequisite(prerequisite);
        definition.setCoverImage(coverImage);
        definition.setSyllabusUrl(syllabusUrl);
        definition.setTags(tags);
        definition.setStatus(status);
    }
}
