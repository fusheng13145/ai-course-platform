package com.aicourse.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量操作请求DTO
 * 用于接收前端批量处理多条记录时的操作参数，支持多种操作类型
 */
@Schema(description = "批量操作请求")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchOperationDTO {

    @NotEmpty(message = "目标ID列表不能为空")
    @Schema(description = "待操作的记录ID列表", example = "[1, 2, 3, 5]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @NotNull(message = "操作类型不能为空")
    @Pattern(regexp = "^(APPROVE|REJECT|DELETE|PUBLISH|ARCHIVE)$",
            message = "操作类型不合法")
    @Schema(description = "操作类型", example = "APPROVE",
            allowableValues = {"APPROVE", "REJECT", "DELETE", "PUBLISH", "ARCHIVE"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Size(max = 500, message = "操作原因不能超过500个字符")
    @Schema(description = "操作原因/备注（某些操作类型需要填写）", example = "批量审核通过 - 符合选课条件")
    private String reason;

    // ====================== Getter & Setter ======================

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
