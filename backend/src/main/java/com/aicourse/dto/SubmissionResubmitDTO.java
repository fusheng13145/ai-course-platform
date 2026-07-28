package com.aicourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 重新提交作业请求 DTO（白名单）
 * 替代原控制器中直接接收 Map&lt;String,Object&gt; 的宽松写法，避免任意字段注入。
 */
@Schema(description = "重新提交作业请求")
@lombok.Data
public class SubmissionResubmitDTO {

    @Schema(description = "文字作答内容")
    @Size(max = 50000, message = "作答内容过长")
    private String content;

    @Schema(description = "附件URL列表")
    private List<String> fileUrls;
}
