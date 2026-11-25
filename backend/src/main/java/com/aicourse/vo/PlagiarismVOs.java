package com.aicourse.vo;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查重出参：查重任务 / 相似片段。
 *
 * <p>字段与实体一一对应、零删减。`originalText` / `matchedText` 是提交正文片段，
 * 端点仅教师与管理员可达（`PlagiarismCheckController` 各读取端点带角色校验 + 提交归属校验），
 * 故不做裁剪。
 */
public final class PlagiarismVOs {

    private PlagiarismVOs() {
    }

    /** 查重任务出参。 */
    @Data
    public static class CheckVO {
        private Long id;
        private Long taskId;
        private Long submissionId;
        private Long studentId;
        private String checkType;
        private String status;
        private BigDecimal similarityScore;
        private Integer highSimilarityCount;
        private String resultSummary;
        private String checkDetail;
        private LocalDateTime checkedAt;
        private String errorMessage;
        private LocalDateTime createdAt;

        public static CheckVO from(PlagiarismCheck check) {
            if (check == null) {
                return null;
            }
            CheckVO vo = new CheckVO();
            vo.setId(check.getId());
            vo.setTaskId(check.getTaskId());
            vo.setSubmissionId(check.getSubmissionId());
            vo.setStudentId(check.getStudentId());
            vo.setCheckType(check.getCheckType());
            vo.setStatus(check.getStatus());
            vo.setSimilarityScore(check.getSimilarityScore());
            vo.setHighSimilarityCount(check.getHighSimilarityCount());
            vo.setResultSummary(check.getResultSummary());
            vo.setCheckDetail(check.getCheckDetail());
            vo.setCheckedAt(check.getCheckedAt());
            vo.setErrorMessage(check.getErrorMessage());
            vo.setCreatedAt(check.getCreatedAt());
            return vo;
        }

        public static List<CheckVO> from(List<PlagiarismCheck> checks) {
            return checks == null ? List.of() : checks.stream().map(CheckVO::from).toList();
        }
    }

    /** 相似片段出参。 */
    @Data
    public static class MatchVO {
        private Long id;
        private Long plagiarismCheckId;
        private Long matchedSubmissionId;
        private String matchedSource;
        private BigDecimal similarity;
        private String originalText;
        private String matchedText;
        private String matchType;
        private LocalDateTime createdAt;

        public static MatchVO from(PlagiarismMatch match) {
            if (match == null) {
                return null;
            }
            MatchVO vo = new MatchVO();
            vo.setId(match.getId());
            vo.setPlagiarismCheckId(match.getPlagiarismCheckId());
            vo.setMatchedSubmissionId(match.getMatchedSubmissionId());
            vo.setMatchedSource(match.getMatchedSource());
            vo.setSimilarity(match.getSimilarity());
            vo.setOriginalText(match.getOriginalText());
            vo.setMatchedText(match.getMatchedText());
            vo.setMatchType(match.getMatchType());
            vo.setCreatedAt(match.getCreatedAt());
            return vo;
        }

        public static List<MatchVO> from(List<PlagiarismMatch> matches) {
            return matches == null ? List.of() : matches.stream().map(MatchVO::from).toList();
        }
    }
}
