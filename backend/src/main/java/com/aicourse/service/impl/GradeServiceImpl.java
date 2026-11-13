package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.GradeItem;
import com.aicourse.entity.GradePolicy;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.GradeItemMapper;
import com.aicourse.mapper.GradePolicyMapper;
import com.aicourse.service.GradeService;
import com.aicourse.service.UserService;
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

    /** 权重合计判定容差：允许浮点/两位小数配置带来的 0.01 误差，不放过真正的缺额或超额。 */
    private static final double WEIGHT_TOLERANCE = 0.01d;

    private final GradePolicyMapper policyMapper;
    private final GradeItemMapper itemMapper;
    private final UserService userService;
    private final com.aicourse.service.component.org.TeachingClassSnapshotResolver teachingClassSnapshotResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GradeServiceImpl(GradePolicyMapper policyMapper, GradeItemMapper itemMapper,
                            UserService userService,
                            com.aicourse.service.component.org.TeachingClassSnapshotResolver teachingClassSnapshotResolver) {
        this.policyMapper = policyMapper;
        this.itemMapper = itemMapper;
        this.userService = userService;
        this.teachingClassSnapshotResolver = teachingClassSnapshotResolver;
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
        // WP7 快照：仅首次落库解析教学班；更新分支不回改（转班不漂移）。
        // 刻意走 policyMapper 而非 this.getById：单测无 Spring 上下文时 baseMapper 未注入。
        GradePolicy policy = policyMapper.selectById(policyId);
        item.setTeachingClassId(policy == null ? null
                : teachingClassSnapshotResolver.resolveTeachingClassId(studentId, policy.getCourseId()));
        itemMapper.insert(item);
        return item;
    }


    /** 计算成绩 */
    @Override
    public FinalGradeVO computeFinal(Long policyId, Long studentId) {
        FinalGradeVO vo = buildFinal(policyId, studentId);
        vo.setStudentName(displayName(studentId, studentNames(List.of(studentId))));
        return vo;
    }

    /** 总评计算本体：只查方案与明细，姓名由调用方填充（报表走批量查询，避免逐学生 N+1） */
    private FinalGradeVO buildFinal(Long policyId, Long studentId) {
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

        List<ComponentRow> rows = new ArrayList<>();
        List<String> ungradedLabels = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double totalWeight = 0d;
        double gradedWeight = 0d;
        BigDecimal weighted = BigDecimal.ZERO;

        for (Map<String, Object> c : comps) {
            String key = text(c.get("key"));
            String name = text(c.get("name"));
            Double parsed = parseWeight(c.get("weight"));
            if (parsed == null) {
                // 历史脏数据（权重缺失/写成非数字字符串）不应让整个报表 500，按 0 计并提示修正
                log.warn("成绩方案 policyId={} 构成项 key={} 的 weight 缺失或非数字，按 0 权重计算", policyId, key);
                warnings.add("维度「" + label(key, name) + "」的权重缺失或不是数字，已按 0 权重计算，请编辑成绩方案修正");
            }
            double weight = parsed == null ? 0d : parsed;
            totalWeight += weight;

            GradeItem it = itemMap.get(key);
            BigDecimal raw = it == null ? null : it.getScore();
            rows.add(new ComponentRow(key, name, weight, raw));
            if (raw == null) {
                ungradedLabels.add(label(key, name));
            } else if (weight > 0) {
                gradedWeight += weight;
                weighted = weighted.add(raw.multiply(BigDecimal.valueOf(weight)));
            }
        }

        if (!comps.isEmpty() && Math.abs(totalWeight - 100) > WEIGHT_TOLERANCE) {
            warnings.add("成绩方案权重合计为 " + plain(totalWeight) + "%，应为 100%，请编辑成绩方案修正");
        }

        BigDecimal finalScore;
        if (gradedWeight > 0) {
            finalScore = weighted.divide(BigDecimal.valueOf(gradedWeight), 2, RoundingMode.HALF_UP);
            if (!ungradedLabels.isEmpty()) {
                warnings.add("未评分维度不计入总评分母：" + String.join("、", ungradedLabels)
                        + " 未评分，总评已按已评分维度权重合计 " + plain(gradedWeight) + "% 归一化");
            }
        } else {
            finalScore = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (comps.isEmpty()) {
                warnings.add("成绩方案未配置构成项，无法计算总评");
            } else if (totalWeight <= 0) {
                // 权重全 0 是方案本身的缺陷：无论有没有评分总评都是 0，优先提示配置问题
                warnings.add("所有维度的权重均为 0，总评记 0");
            } else {
                warnings.add("该学生尚无任何计入总评的已评分维度（"
                        + String.join("、", ungradedLabels) + " 未评分），总评记 0");
            }
        }

        List<GradeComponentDetailVO> detail = new ArrayList<>();
        for (ComponentRow r : rows) {
            boolean counted = r.score() != null && r.weight() > 0;
            BigDecimal contribution = counted
                    ? r.score().multiply(BigDecimal.valueOf(r.weight()))
                            .divide(BigDecimal.valueOf(gradedWeight), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            detail.add(new GradeComponentDetailVO(r.key(), r.name(), r.weight(), r.score(),
                    contribution, counted));
        }
        return new FinalGradeVO(policyId, studentId, detail, finalScore, warnings);
    }

    /** 维度展示名：name 缺失时退回 key，避免提示里出现 null。 */
    private static String label(String key, String name) {
        return name == null ? String.valueOf(key) : name;
    }

    /** 百分数展示：去掉无意义的尾随零（60.00 → 60，66.67 → 66.67）。 */
    private static String plain(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    /** 单个构成项在总评计算中的中间态（得分可为 null，表示未评分）。 */
    private record ComponentRow(String key, String name, double weight, BigDecimal score) {
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
        if (studentIds.isEmpty()) {
            return res;
        }
        Map<Long, String> names = studentNames(studentIds);
        for (Long sid : studentIds) {
            FinalGradeVO vo = buildFinal(policy.getId(), sid);
            vo.setStudentName(displayName(sid, names));
            res.add(vo);
        }
        res.sort((a, b) -> b.getFinalScore().compareTo(a.getFinalScore()));
        return res;
    }

    /** 批量取学生姓名，一次查询覆盖全班，避免报表逐行查库 */
    private Map<Long, String> studentNames(Collection<Long> studentIds) {
        List<Long> ids = studentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (User u : userService.listByIds(ids)) {
            names.put(u.getId(), u.getFullName());
        }
        return names;
    }

    /** 姓名缺失（账号被删/姓名为空）时退回学生ID，避免报表出现空白行 */
    private static String displayName(Long studentId, Map<Long, String> names) {
        String name = names.get(studentId);
        return name == null || name.isBlank() ? "学生 #" + studentId : name;
    }

    /**
     * 校验权重配置：允许整体留空（先建方案、后配维度），但一旦填写就必须结构合法且**合计 100%**——
     * 否则存入的配置会在 computeFinal 里被 parseComponents 静默吞掉，全班总评无声变成 0 分；
     * 权重不配平也会让总评按缺的份额被稀释，属数据完整性问题而非偏好问题。
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
        double sum = 0d;
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
            sum += weight;
        }
        // 空数组等同于「先建壳方案、后配维度」，不强制配平
        if (!comps.isEmpty() && Math.abs(sum - 100) > WEIGHT_TOLERANCE) {
            throw new BusinessException("成绩构成权重合计须为 100%，当前为 " + plain(sum) + "%");
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
