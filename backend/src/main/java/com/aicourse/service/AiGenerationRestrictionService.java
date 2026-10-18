package com.aicourse.service;

import com.aicourse.entity.AiGenerationRestriction;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 教师 AI 出题限制名单服务（角色矩阵 ④ 修订：按特定教师限制）。
 *
 * <p>与平台级开关（{@code system_configs} 的 {@code ai.question-generation.teacher-enabled}）**并存**：
 * 平台开关是总闸（缺省=允许），本名单是逐人限制；判定唯一入口在
 * {@code AiGenerationPolicy}（先总闸、再名单，任一命中即 403）。</p>
 */
public interface AiGenerationRestrictionService extends IService<AiGenerationRestriction> {

    /** 全部名单（按加入时间倒序）。 */
    List<AiGenerationRestriction> listAll();

    /** 该用户是否在名单内（userId 为 null 时返回 false）。 */
    boolean isRestricted(Long userId);

    /**
     * 加入名单（已存在则更新原因与操作人，不产生重复行——表上另有唯一键兜底）。
     *
     * @param userId   被限制的教师用户ID
     * @param reason   限制原因（可空）
     * @param adminId  操作管理员ID（留痕，可空）
     * @return 名单记录
     */
    AiGenerationRestriction restrict(Long userId, String reason, Long adminId);

    /**
     * 移出名单。
     *
     * @param userId 教师用户ID
     * @return true=本次确实移除了一条；false=该用户原本不在名单内
     */
    boolean unrestrict(Long userId);
}
