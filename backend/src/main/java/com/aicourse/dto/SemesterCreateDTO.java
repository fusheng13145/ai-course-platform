package com.aicourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建学期入参（角色矩阵「① 创建学期」）。
 */
@Data
public class SemesterCreateDTO {

    @NotBlank(message = "学期名称不能为空")
    @Size(max = 64, message = "学期名称最长 64 字符")
    private String name;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
