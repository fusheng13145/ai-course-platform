package com.aicourse.service;

import com.aicourse.entity.TeachingClassAdminClass;
import com.aicourse.vo.RosterSyncResultVO;

import java.util.List;

/**
 * 教学班名单服务（V14 / B-12-P-08 WP5）。
 *
 * <p>负责行政班导入与重同步：导入生成 {@code ADMIN_CLASS_IMPORT} 来源的选课记录；
 * 重同步只增删该类来源的名单，不覆盖也不删除手工/自助选课。</p>
 */
public interface RosterService {

    /**
     * 查询教学班的行政班来源列表（课程负责人、本班主讲、管理员可读）。
     */
    List<TeachingClassAdminClass> listSources(Long teachingClassId, Long operatorId);

    /**
     * 按行政班导入名单：重复导入幂等，已存在有效选课不覆盖。
     */
    RosterSyncResultVO importFromAdminClass(Long teachingClassId, Long adminClassId, Long operatorId);

    /**
     * 重同步名单：返回 added/removed/skipped；{@code adminClassId} 为空时同步全部已导入行政班。
     */
    RosterSyncResultVO resync(Long teachingClassId, Long adminClassId, Long operatorId);
}
