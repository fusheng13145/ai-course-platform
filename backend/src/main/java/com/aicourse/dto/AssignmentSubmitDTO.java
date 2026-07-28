package com.aicourse.dto;

import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 作业提交请求DTO
 * 用于学生提交课程作业，支持文本内容和文件附件
 */
@Schema(description = "作业提交请求")
@Data
public class AssignmentSubmitDTO {

    @Size(max = 50000, message = "文本内容长度不能超过50000个字符")
    @Schema(description = "学生提交的文本内容（支持Markdown）", example = "## 解题思路\n\n本题采用动态规划方法...")
    private String content;

    @Schema(description = "提交的文件URL地址列表", example = "[\"https://example.com/uploads/hw1.pdf\", \"https://example.com/uploads/code.zip\"]")
    private List<String> fileUrls;

}
