package com.aicourse.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 教学班名单导入/重同步结果（V14 / B-12-P-08 WP5）。
 *
 * <p>口径：{@code added} 为新增或复活的名单；{@code removed} 为重同步时
 * 仅对 {@code ADMIN_CLASS_IMPORT} 来源软移除的名单（手工/自助记录不动）；
 * {@code skipped} 为已在本教学班有效或重复导入；{@code conflicted} 为学生已在该课程
 * 其他教学班有效选课时跳过、不静默覆盖的条数。</p>
 */
@Data
public class RosterSyncResultVO {

    /** 新增（含复活软退课记录）的名单数 */
    private int added;

    /** 重同步时软移除的行政班导入名单数（不动手工/自助记录） */
    private int removed;

    /** 已在本教学班有效或重复导入而跳过的名单数 */
    private int skipped;

    /** 与其他教学班有效选课冲突、未覆盖的名单数 */
    private int conflicted;

    /** 冲突学生ID列表，便于教师定位；无冲突时为空列表 */
    private List<Long> conflictedStudentIds = new ArrayList<>();

    /** 累加另一个同步结果，用于按多个行政班汇总 */
    public void merge(RosterSyncResultVO other) {
        if (other == null) {
            return;
        }
        added += other.added;
        removed += other.removed;
        skipped += other.skipped;
        conflicted += other.conflicted;
        conflictedStudentIds.addAll(other.conflictedStudentIds);
    }
}
