package com.aicourse.service.impl;

import com.aicourse.entity.AiGenerationRestriction;
import com.aicourse.mapper.AiGenerationRestrictionMapper;
import com.aicourse.service.AiGenerationRestrictionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link AiGenerationRestrictionService} 实现。
 *
 * <p>两条口径：① 加入名单是**幂等 upsert**（同一位教师只保留一行，重复加入只更新原因与操作人，
 * 不新增记录也不抛唯一冲突）；② 移出按 userId 精确删除并返回是否真的删到，供上层区分"解除成功"
 * 与"本来就不在名单"。</p>
 */
@Service
public class AiGenerationRestrictionServiceImpl
        extends ServiceImpl<AiGenerationRestrictionMapper, AiGenerationRestriction>
        implements AiGenerationRestrictionService {

    @Override
    public List<AiGenerationRestriction> listAll() {
        return this.list(new LambdaQueryWrapper<AiGenerationRestriction>()
                .orderByDesc(AiGenerationRestriction::getCreatedAt));
    }

    @Override
    public boolean isRestricted(Long userId) {
        if (userId == null) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<AiGenerationRestriction>()
                .eq(AiGenerationRestriction::getUserId, userId)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiGenerationRestriction restrict(Long userId, String reason, Long adminId) {
        AiGenerationRestriction existing = this.getOne(new LambdaQueryWrapper<AiGenerationRestriction>()
                .eq(AiGenerationRestriction::getUserId, userId), false);
        if (existing != null) {
            // 幂等：已在名单里只更新原因与操作人，保留首次加入时间（"何时开始被限制"不可被覆盖）
            existing.setReason(reason);
            existing.setRestrictedBy(adminId);
            this.updateById(existing);
            return existing;
        }
        AiGenerationRestriction created = new AiGenerationRestriction();
        created.setUserId(userId);
        created.setReason(reason);
        created.setRestrictedBy(adminId);
        this.save(created);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unrestrict(Long userId) {
        if (userId == null) {
            return false;
        }
        return this.remove(new LambdaQueryWrapper<AiGenerationRestriction>()
                .eq(AiGenerationRestriction::getUserId, userId));
    }
}
