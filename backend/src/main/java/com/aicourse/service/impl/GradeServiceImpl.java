package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.GradeItemMapper;
import com.aicourse.mapper.GradePolicyMapper;
import com.aicourse.service.GradeService;
import com.aicourse.vo.FinalGradeVO;
import com.aicourse.vo.GradeComponentDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 过程性成绩（多维加权）服务实现
 */
@Service
public class GradeServiceImpl extends ServiceImpl<GradePolicyMapper, GradePolicy> implements GradeService {

    private static final Logger log = LoggerFactory.getLogger(GradeServiceImpl.class);

    private final GradePolicyMapper policyMapper;
    private final GradeItemMapper itemMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GradeServiceImpl(GradePolicyMapper policyMapper, GradeItemMapper itemMapper) {
        this.policyMapper = policyMapper;
        this.itemMapper = itemMapper;
    }


    /** 保存成绩 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradePolicy savePolicy(GradePolicy policy, Long userId) {
        log.info("保存成绩");

        if (policy.getCourseId() == null) {
            throw new BusinessException("课程ID不能为空");
        }
        validateComponents(policy.getComponents());
        GradePolicy exist = getPolicy(policy.getCourseId());
        policy.setCreatorId(userId);
        policy.setIsDefault(true);
        if (exist != null) {
            policy.setId(exist.getId());
            policy.setCreatedAt(exist.getCreatedAt());
            this.updateById(policy);
            return policy;
        }
        this.save(policy);
        return policy;
    }


    /** 查询成绩 */
    @Override
    public GradePolicy getPolicy(Long courseId) {
        List<GradePolicy> list = this.list(new LambdaQueryWrapper<GradePolicy>()
                .eq(GradePolicy::getCourseId, courseId)
                .orderByDesc(GradePolicy::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }


    /** 查询成绩 */
    @Override
    public GradePolicy getPolicyById(Long policyId) {
        return policyMapper.selectById(policyId);
    }


    /** 成绩相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeItem upsertItem(Long policyId, Long studentId, String componentKey,
                               BigDecimal score, BigDecimal rawScore, Long sourceId,
                               Long graderId, String remark) {
        GradeItem exist = itemMapper.selectOne(new LambdaQueryWrapper<GradeItem>()
                .eq(GradeItem::getPolicyId, policyId)
                .eq(GradeItem::getStudentId, studentId)
                .eq(GradeItem::getComponentKey, componentKey));
        if (exist != null) {
            exist.setScore(score);
            exist.setRawScore(rawScore);
            exist.setSourceId(sourceId);
            exist.setGraderId(graderId);
            exist.setRemark(remark);
            itemMapper.updateById(exist);
            return exist;
        }
        GradeItem item = new GradeItem();
        item.setPolicyId(policyId);
        item.setStudentId(studentId);
        item.setComponentKey(componentKey);
        item.setScore(score);
        item.setRawScore(rawScore);
        item.setSourceId(sourceId);
        item.setGraderId(graderId);
        item.setRemark(remark);
        itemMapper.insert(item);
        return item;
    }


    /** 计算成绩 */
    @Override
    public FinalGradeVO computeFinal(Long policyId, Long studentId) {
        GradePolicy policy = this.getById(policyId);
        if (policy == null) {
            throw new BusinessException("成绩方案不存在");
        }
        List<Map<String, Object>> comps = parseComponents(policy.getComponents());
        List<GradeItem> items = itemMapper.selectList(new LambdaQueryWrapper<GradeItem>()
                .eq(GradeItem::getPolicyId, policyId)
                .eq(GradeItem::getStudentId, studentId));
        Map<String, GradeItem> itemMap = items.stream()
                .collect(Collectors.toMap(GradeItem::getComponentKey, i -> i, (a, b) -> a));

        List<GradeComponentDetailVO> detail = new ArrayList<>();
        BigDecimal finalScore = BigDecimal.ZERO;
        for (Map<String, Object> c : comps) {
            String key = text(c.get("key"));
            String name = text(c.get("name"));
            Double parsed = parseWeight(c.get("weight"));
            if (parsed == null) {
                // 历史脏数据（权重缺失/写成非数字字符串）不应让整个报表 500，按 0 计并在日志中定位
                log.warn("成绩方案 policyId={} 构成项 key={} 的 weight 缺失或非数字，按 0 权重计算", policyId, key);
            }
            double weight = parsed == null ? 0d : parsed;
            GradeItem it = itemMap.get(key);
            BigDecimal s = (it != null && it.getScore() != null) ? it.getScore() : BigDecimal.ZERO;
            BigDecimal contribution = s.multiply(BigDecimal.valueOf(weight))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            finalScore = finalScore.add(contribution);
            detail.add(new GradeComponentDetailVO(key, name, weight, s, contribution));
        }
        return new FinalGradeVO(policyId, studentId, detail,
                finalScore.setScale(2, RoundingMode.HALF_UP));
    }


    /** 成绩相关操作 */
    @Override
    public List<FinalGradeVO> courseReport(Long courseId) {
        GradePolicy policy = getPolicy(courseId);
        if (policy == null) {
            throw new BusinessException("课程尚未配置成绩方案");
        }
        List<GradeItem> items = itemMapper.selectList(new LambdaQueryWrapper<GradeItem>()
                .eq(GradeItem::getPolicyId, policy.getId())
                .select(GradeItem::getStudentId));
        Set<Long> studentIds = items.stream()
                .map(GradeItem::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<FinalGradeVO> res = new ArrayList<>();
        for (Long sid : studentIds) {
            res.add(computeFinal(policy.getId(), sid));
        }
        res.sort((a, b) -> b.getFinalScore().compareTo(a.getFinalScore()));
        return res;
    }

    /**
     * 校验权重配置：允许整体留空（先建方案、后配维度），但一旦填写就必须结构合法——
     * 否则存入的配置会在 computeFinal 里被 parseComponents 静默吞掉，全班总评无声变成 0 分。
     */
    private void validateComponents(String json) {
        if (json == null || json.isBlank()) {
            return;
        }
        List<Map<String, Object>> comps;
        try {
            comps = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new BusinessException("成绩构成项必须是对象数组，如 [{\"key\":\"exam\",\"name\":\"期末\",\"weight\":60}]");
        }
        Set<String> keys = new HashSet<>();
        for (Map<String, Object> c : comps) {
            String key = c == null ? null : text(c.get("key"));
            if (key == null) {
                throw new BusinessException("成绩构成项缺少标识 key");
            }
            if (!keys.add(key)) {
                throw new BusinessException("成绩构成项标识重复: " + key);
            }
            Double weight = parseWeight(c.get("weight"));
            if (weight == null || weight < 0 || weight > 100) {
                throw new BusinessException("成绩构成项 " + key + " 的权重必须是 0-100 的数字");
            }
        }
    }

    /** 权重解析：缺失、非数字类型或不可解析的字符串统一返回 null，由调用方决定降级或拒绝。 */
    private static Double parseWeight(Object raw) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Double.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String text(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private List<Map<String, Object>> parseComponents(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("成绩相关操作失败: {}", e.getMessage(), e);

            return new ArrayList<>();
        }
    }
}
