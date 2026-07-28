package com.aicourse.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交详情VO（含批改界面数据）
 * 继承提交列表基础信息，增加学生提交内容、评语、批改历史和查重报告等详情
 */
@Schema(description = "提交详情（含批改界面数据）")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubmissionDetailVO extends SubmissionListVO {

    // ====================== 学生提交内容 ======================

    @Schema(description = "学生提交的文本内容", example = "## 解题思路\n\n本题采用动态规划方法...")
    private String content;

    @Schema(description = "学生提交的文件URL", example = "https://example.com/uploads/submission/hw1.pdf")
    private String fileUrl;

    // ====================== 评语与反馈 ======================

    @Schema(description = "教师人工评语", example = "整体完成质量不错，建议加强异常处理部分。")
    private String feedback;

    @Schema(description = "AI智能评语全文", example = "【优点】\n1. 代码结构清晰...\n【不足】\n1. 缺少输入校验...")
    private String aiFeedbackFull;

    // ====================== 批改历史 ======================

    @Schema(description = "批改历史记录列表")
    private List<GradingRecord> gradingHistory;

    // ====================== 查重报告 ======================

    @Schema(description = "相似性报告")
    private SimilarityReport similarityReport;

    /**
     * 批改记录内部类
     */
    @Schema(description = "批改记录")
    public static class GradingRecord {

        @Schema(description = "批改ID", example = "5001")
        private Long id;

        @Schema(description = "批改人ID", example = "100")
        private Long graderId;

        @Schema(description = "批改人姓名", example = "李教授")
        private String graderName;

        @Schema(description = "本次得分", example = "85.5")
        private BigDecimal score;

        @Schema(description = "本次评语", example = "第一次批改：基本正确，有改进空间")
        private String comment;

        @Schema(description = "批改时间", example = "2024-12-31T09:30:00")
        private LocalDateTime gradedAt;

    }

    /**
     * 相似性报告内部类
     */
    @Schema(description = "相似性报告")
    public static class SimilarityReport {

        @Schema(description = "总体相似度(0-100)", example = "25.8")
        private Double score;

        @Schema(description = "匹配来源列表")
        private List<MatchSource> matches;

        /**
         * 匹配来源内部类
         */
        @Schema(description = "匹配来源")
        public static class MatchSource {

            @Schema(description = "来源描述", example = "张三的《第一次编程作业》")
            private String source;

            @Schema(description = "该来源相似度(0-100)", example = "35.2")
            private Double similarity;

        }

    }

}
