package com.aicourse.vo;

import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.entity.Vote;
import com.aicourse.entity.VoteRecord;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课堂互动出参：签到 / 签到记录 / 投票 / 投票记录。
 *
 * <p>四组字段均与实体一一对应、零删减，本批次<b>不改变任何可见性</b>：
 * `SignInRecord` 的 `ip` / `userAgent` / `latitude` / `longitude` 是防代签的现场证据，
 * `VoteRecord` 的 `ip` / `userAgent` 是防代投的证据，调用方均为教师侧或记录本人。
 *
 * <p><b>关于 `signCode` / `gesture`（已核实，非泄露）</b>：`GET /interactions/sign-ins?courseId=`
 * 在服务层 `SignInServiceImpl.listByCourse` 首行即调用 `assertCourseOwner(courseId, userId, auth)`，
 * 学生无法到达该返回路径，签到码只落到课程负责人与管理员的响应里。故无需为签到码拆学生端出参。
 */
public final class InteractionVOs {

    private InteractionVOs() {
    }

    /** 签到活动出参。 */
    @Data
    public static class SignInVO {
        private Long id;
        private Long courseId;
        private Long chapterId;
        private String title;
        private String type;
        private String status;
        private Integer durationMinutes;
        private String gesture;
        private String signCode;
        private Long creatorId;
        private LocalDateTime startedAt;
        private LocalDateTime closedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static SignInVO from(SignIn signIn) {
            if (signIn == null) {
                return null;
            }
            SignInVO vo = new SignInVO();
            vo.setId(signIn.getId());
            vo.setCourseId(signIn.getCourseId());
            vo.setChapterId(signIn.getChapterId());
            vo.setTitle(signIn.getTitle());
            vo.setType(signIn.getType());
            vo.setStatus(signIn.getStatus());
            vo.setDurationMinutes(signIn.getDurationMinutes());
            vo.setGesture(signIn.getGesture());
            vo.setSignCode(signIn.getSignCode());
            vo.setCreatorId(signIn.getCreatorId());
            vo.setStartedAt(signIn.getStartedAt());
            vo.setClosedAt(signIn.getClosedAt());
            vo.setCreatedAt(signIn.getCreatedAt());
            vo.setUpdatedAt(signIn.getUpdatedAt());
            return vo;
        }

        public static List<SignInVO> from(List<SignIn> signIns) {
            return signIns == null ? List.of() : signIns.stream().map(SignInVO::from).toList();
        }
    }

    /** 签到记录出参。 */
    @Data
    public static class SignInRecordVO {
        private Long id;
        private Long signInId;
        private Long studentId;
        private String status;
        private LocalDateTime signTime;
        private String ip;
        private String userAgent;
        private String photoUrl;
        private Double latitude;
        private Double longitude;
        private String submittedCode;
        private String submittedGesture;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static SignInRecordVO from(SignInRecord record) {
            if (record == null) {
                return null;
            }
            SignInRecordVO vo = new SignInRecordVO();
            vo.setId(record.getId());
            vo.setSignInId(record.getSignInId());
            vo.setStudentId(record.getStudentId());
            vo.setStatus(record.getStatus());
            vo.setSignTime(record.getSignTime());
            vo.setIp(record.getIp());
            vo.setUserAgent(record.getUserAgent());
            vo.setPhotoUrl(record.getPhotoUrl());
            vo.setLatitude(record.getLatitude());
            vo.setLongitude(record.getLongitude());
            vo.setSubmittedCode(record.getSubmittedCode());
            vo.setSubmittedGesture(record.getSubmittedGesture());
            vo.setCreatedAt(record.getCreatedAt());
            vo.setUpdatedAt(record.getUpdatedAt());
            return vo;
        }

        public static List<SignInRecordVO> from(List<SignInRecord> records) {
            return records == null ? List.of() : records.stream().map(SignInRecordVO::from).toList();
        }
    }

    /** 投票活动出参。 */
    @Data
    public static class VoteVO {
        private Long id;
        private Long courseId;
        private Long chapterId;
        private String title;
        private String description;
        private String voteMode;
        private String options;
        private String status;
        private Long creatorId;
        private LocalDateTime startedAt;
        private LocalDateTime closedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static VoteVO from(Vote vote) {
            if (vote == null) {
                return null;
            }
            VoteVO vo = new VoteVO();
            vo.setId(vote.getId());
            vo.setCourseId(vote.getCourseId());
            vo.setChapterId(vote.getChapterId());
            vo.setTitle(vote.getTitle());
            vo.setDescription(vote.getDescription());
            vo.setVoteMode(vote.getVoteMode());
            vo.setOptions(vote.getOptions());
            vo.setStatus(vote.getStatus());
            vo.setCreatorId(vote.getCreatorId());
            vo.setStartedAt(vote.getStartedAt());
            vo.setClosedAt(vote.getClosedAt());
            vo.setCreatedAt(vote.getCreatedAt());
            vo.setUpdatedAt(vote.getUpdatedAt());
            return vo;
        }

        public static List<VoteVO> from(List<Vote> votes) {
            return votes == null ? List.of() : votes.stream().map(VoteVO::from).toList();
        }
    }

    /** 投票记录出参。 */
    @Data
    public static class VoteRecordVO {
        private Long id;
        private Long voteId;
        private Long studentId;
        private String choice;
        private String textAnswer;
        private String ip;
        private String userAgent;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static VoteRecordVO from(VoteRecord record) {
            if (record == null) {
                return null;
            }
            VoteRecordVO vo = new VoteRecordVO();
            vo.setId(record.getId());
            vo.setVoteId(record.getVoteId());
            vo.setStudentId(record.getStudentId());
            vo.setChoice(record.getChoice());
            vo.setTextAnswer(record.getTextAnswer());
            vo.setIp(record.getIp());
            vo.setUserAgent(record.getUserAgent());
            vo.setCreatedAt(record.getCreatedAt());
            vo.setUpdatedAt(record.getUpdatedAt());
            return vo;
        }

        public static List<VoteRecordVO> from(List<VoteRecord> records) {
            return records == null ? List.of() : records.stream().map(VoteRecordVO::from).toList();
        }
    }
}
