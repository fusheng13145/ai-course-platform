package com.aicourse.dto;

import com.aicourse.entity.GradeItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单项成绩入参（入参收实体加固）。
 *
 * <p>{@code graderId}（录入人）与 {@code teachingClassId}（归属快照）由服务端写入；
 * 实体直绑会让客户端自带录入人，成绩留痕的可信度直接失效。</p>
 *
 * <p>{@code score} 的 0~100 校验从实体原样继承 —— 服务层虽也会校验，但入参层的快速失败
 * 能给出字段级错误信息，属既有行为，不新增拒绝规则。</p>
 */
@Schema(name = "GradeItemSaveDTO", description = "单项成绩入参；录入人与归属由服务端写入")
@Data
public class GradeItemSaveDTO {

    @Schema(description = "成绩方案ID", example = "8")
    private Long policyId;

    @Schema(description = "学生ID", example = "45")
    private Long studentId;

    @Schema(description = "考核项键（对应方案 components 中的 key）", example = "homework")
    private String componentKey;

    @Schema(description = "折算后成绩（0~100）", example = "88.5")
    @DecimalMin(value = "0", message = "成绩得分不能小于 0")
    @DecimalMax(value = "100", message = "成绩得分不能大于 100")
    private BigDecimal score;

    @Schema(description = "原始分（未折算）", example = "17.7")
    private BigDecimal rawScore;

    @Schema(description = "来源对象ID（如某次作业提交ID）", example = "1024")
    private Long sourceId;

    @Schema(description = "备注")
    private String remark;

    public GradeItem toEntity() {
        GradeItem item = new GradeItem();
        item.setPolicyId(policyId);
        item.setStudentId(studentId);
        item.setComponentKey(componentKey);
        item.setScore(score);
        item.setRawScore(rawScore);
        item.setSourceId(sourceId);
        item.setRemark(remark);
        return item;
    }
}
