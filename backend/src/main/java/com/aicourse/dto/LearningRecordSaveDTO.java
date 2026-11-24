package com.aicourse.dto;

import com.aicourse.entity.LearningRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 学习记录上报入参（入参收实体加固）。
 *
 * <p><b>为什么不直接绑 {@code LearningRecord} 实体</b>：实体直绑时客户端可以自带 {@code userId}
 * （把学时记到别人账上＝跨人写越权）、{@code id}／{@code createdAt}。DTO 只暴露行为字段，
 * 归属用户由控制器取登录身份写入。</p>
 */
@Schema(name = "LearningRecordSaveDTO", description = "学习记录上报入参；userId/主键/创建时间不接受客户端传入")
@Data
public class LearningRecordSaveDTO {

    @Schema(description = "资源ID", example = "101")
    private Long resourceId;

    @Schema(description = "章节ID", example = "202")
    private Long chapterId;

    @Schema(description = "课程ID", example = "4007")
    private Long courseId;

    @Schema(description = "行为类型", example = "VIEW")
    private String actionType;

    @Schema(description = "本次时长（秒）", example = "600")
    private Integer duration;

    @Schema(description = "进度百分比", example = "45.5")
    private BigDecimal progress;

    @Schema(description = "扩展元数据（JSON 文本）")
    private String metadata;

    public LearningRecord toEntity() {
        LearningRecord record = new LearningRecord();
        applyTo(record);
        return record;
    }

    public void applyTo(LearningRecord record) {
        record.setResourceId(resourceId);
        record.setChapterId(chapterId);
        record.setCourseId(courseId);
        record.setActionType(actionType);
        record.setDuration(duration);
        record.setProgress(progress);
        record.setMetadata(metadata);
    }
}
