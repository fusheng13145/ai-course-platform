package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 开始考试请求体。
 *
 * <p>3E-03B：原 {@code POST /exam-sessions/start} 用 {@code Map<String, Long>} 接参，
 * 键名靠字符串约定，缺 {@code examPaperId} 时会一路传到授权组件才炸。
 * 收成 DTO 后缺参在 Web 层就返回 400。</p>
 */
@Schema(description = "开始考试请求体")
public class ExamStartDTO {

    @Schema(description = "试卷ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "examPaperId 不能为空")
    private Long examPaperId;

    public Long getExamPaperId() {
        return examPaperId;
    }

    public void setExamPaperId(Long examPaperId) {
        this.examPaperId = examPaperId;
    }
}
