package com.aicourse.vo;

import com.aicourse.entity.Semester;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学期展示 VO（B-12／P-08 10A，WP10）。
 * <p>
 * 10A 拍板：库里只存规范名（如 {@code 2026-2027-1}），界面展示别名（如 {@code 2026 秋}），
 * 别名<b>不落任何表、不写进课程</b>——由本 VO 按规范名现场推导，改名即随动。</p>
 */
@Data
public class SemesterVO {

    private Long id;

    /** 规范学期名（学年内唯一，与库中逐字一致） */
    private String name;

    /** 展示别名：规范名 {@code Y1-Y2-K} → K=1 显示「Y1 秋」、K=2「Y2 春」、K=3「Y2 夏」；不匹配模式时原样返回规范名 */
    private String displayName;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private LocalDateTime archivedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 由实体推导展示 VO（别名不落库）。 */
    public static SemesterVO from(Semester semester) {
        SemesterVO vo = new SemesterVO();
        vo.setId(semester.getId());
        vo.setName(semester.getName());
        vo.setDisplayName(displayNameOf(semester.getName()));
        vo.setStartDate(semester.getStartDate());
        vo.setEndDate(semester.getEndDate());
        vo.setStatus(semester.getStatus());
        vo.setArchivedAt(semester.getArchivedAt());
        vo.setCreatedAt(semester.getCreatedAt());
        vo.setUpdatedAt(semester.getUpdatedAt());
        return vo;
    }

    /** 10A 别名推导（纯函数，便于单测）。 */
    public static String displayNameOf(String canonicalName) {
        if (canonicalName == null) {
            return null;
        }
        // 形如 2026-2027-1：第一学期=起始年秋；第二/三学期=结束年春/夏
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d{4})-(\\d{4})-(\\d)$").matcher(canonicalName.trim());
        if (matcher.matches()) {
            String startYear = matcher.group(1);
            String endYear = matcher.group(2);
            switch (matcher.group(3)) {
                case "1":
                    return startYear + " 秋";
                case "2":
                    return endYear + " 春";
                case "3":
                    return endYear + " 夏";
                default:
                    break;
            }
        }
        return canonicalName;
    }
}
