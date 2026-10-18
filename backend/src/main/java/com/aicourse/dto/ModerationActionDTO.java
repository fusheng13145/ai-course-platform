package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 内容审核处置入参（讨论回复 / 资源共用同一动作集）。
 */
@Data
public class ModerationActionDTO {

    /** 处置动作：HIDE-隐藏（可恢复）；RESTORE-恢复；DELETE-删除（物理删，不可恢复） */
    @NotBlank(message = "处置动作不能为空")
    @Pattern(regexp = "HIDE|RESTORE|DELETE", message = "处置动作只能是 HIDE / RESTORE / DELETE")
    private String action;

    /** 处置原因（建议填写；DELETE 无法在内容上留痕，原因只进操作日志） */
    @Size(max = 255, message = "处置原因最长 255 字符")
    private String reason;

    /** 是否同时封禁作者（仅 HIDE / DELETE 有意义；RESTORE 忽略该字段） */
    private Boolean banAuthor;
}
