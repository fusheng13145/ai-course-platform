package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.Question;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointMapper;
import com.aicourse.mapper.WrongQuestionMapper;
import com.aicourse.service.QuestionService;
import com.aicourse.service.WrongQuestionService;
import com.aicourse.util.WrongQuestionSnapshotUtil;
import com.aicourse.vo.WrongQuestionGroupVO;
import com.aicourse.vo.WrongQuestionStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题本服务实现类
 * 实现错题记录、掌握管理、练习、统计、推荐等核心业务逻辑
 */
@Service
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMapper, WrongQuestion> implements WrongQuestionService {

    private static final Logger log = LoggerFactory.getLogger(WrongQuestionServiceImpl.class);

    /** 解析 question.knowledge_points 的 JSON 数组文本（如 ["Python基础","数据结构"]） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 未能解析出知识点时的归组名（与原实现保持一致） */
    private static final String UNKNOWN_KP = "未知知识点";

    private final QuestionService questionService;

    /**
     * 注入 Mapper 而非 KnowledgePointService：后者已反向依赖本服务
     * （见 KnowledgePointServiceImpl 的 {@code @Lazy WrongQuestionService}），
     * 从 Service 层再注入会加深双向依赖。
     */
    private final KnowledgePointMapper knowledgePointMapper;

    public WrongQuestionServiceImpl(QuestionService questionService,
                                    KnowledgePointMapper knowledgePointMapper) {
        this.questionService = questionService;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /**
     * 记录错题（考试/练习时自动调用）
     * 查询是否已有该学生+题目的错题记录，有则更新，无则新建
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WrongQuestion recordWrongAnswer(Long studentId, Long questionId, Long courseId, String wrongAnswer, String sourceType) {
        // 查询是否已有该学生+题目的错题记录
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(WrongQuestion::getQuestionId, questionId);

        WrongQuestion existing = this.getOne(queryWrapper);

        if (existing != null) {
            // 已有记录，更新错误答案、练习次数和最后练习时间
            existing.setWrongAnswer(wrongAnswer);
            existing.setPracticeCount((existing.getPracticeCount() != null ? existing.getPracticeCount() : 0) + 1);
            existing.setLastPracticedAt(LocalDateTime.now());
            // 再次答错，取消掌握标记
            existing.setMastered(false);
            // 刷新快照（2026-09-23 修复）：保证错题本展示与当前题库一致
            WrongQuestionSnapshotUtil.fillFromQuestion(existing, questionService.getById(questionId));
            this.updateById(existing);
            return existing;
        } else {
            // 新建错题记录
            WrongQuestion wrongQuestion = new WrongQuestion();
            wrongQuestion.setStudentId(studentId);
            wrongQuestion.setQuestionId(questionId);
            wrongQuestion.setCourseId(courseId);
            wrongQuestion.setWrongAnswer(wrongAnswer);
            wrongQuestion.setSourceType(sourceType);
            wrongQuestion.setMastered(false);
            wrongQuestion.setPracticeCount(1);
            wrongQuestion.setLastPracticedAt(LocalDateTime.now());
            // 快照填充（2026-09-23 修复）：questionContent / correctAnswer 等快照字段
            // 此前无人写入，导致错题本列表题干为空（详见 WrongQuestionSnapshotUtil）
            WrongQuestionSnapshotUtil.fillFromQuestion(wrongQuestion, questionService.getById(questionId));
            this.save(wrongQuestion);
            return wrongQuestion;
        }
    }

    /**
     * 标记已掌握
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMastered(Long wrongQuestionId) {
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }
        wrongQuestion.setMastered(true);
        this.updateById(wrongQuestion);
    }

    /**
     * 标记已掌握（带归属校验，修复 P1-2 IDOR：错题必须属于该学生）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markMasteredForStudent(Long wrongQuestionId, Long studentId) {
        WrongQuestion wrongQuestion = assertOwnedBy(wrongQuestionId, studentId);
        wrongQuestion.setMastered(true);
        this.updateById(wrongQuestion);
    }

    /**
     * 删除错题（带归属校验，修复 P1-2 IDOR：错题必须属于该学生）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForStudent(Long wrongQuestionId, Long studentId) {
        log.info("删除错题题目");

        assertOwnedBy(wrongQuestionId, studentId);
        this.removeById(wrongQuestionId);
    }

    /**
     * 归属断言：错题记录必须属于该学生，否则 403 拒绝。
     */
    private WrongQuestion assertOwnedBy(Long wrongQuestionId, Long studentId) {
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }
        if (studentId == null || wrongQuestion.getStudentId() == null
                || !wrongQuestion.getStudentId().equals(studentId)) {
            throw new BusinessException(403, "无权操作该错题记录");
        }
        return wrongQuestion;
    }

    /**
     * 取消掌握标记（再次答错时调用）
     * 同时增加练习次数表示再次犯错
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmarkMastered(Long wrongQuestionId) {
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }
        wrongQuestion.setMastered(false);
        // 增加练习次数，表示再次犯错
        wrongQuestion.setPracticeCount((wrongQuestion.getPracticeCount() != null ? wrongQuestion.getPracticeCount() : 0) + 1);
        wrongQuestion.setLastPracticedAt(LocalDateTime.now());
        this.updateById(wrongQuestion);
    }

    /**
     * 更新练习次数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementPracticeCount(Long wrongQuestionId) {
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }
        wrongQuestion.setPracticeCount((wrongQuestion.getPracticeCount() != null ? wrongQuestion.getPracticeCount() : 0) + 1);
        wrongQuestion.setLastPracticedAt(LocalDateTime.now());
        this.updateById(wrongQuestion);
    }

    /**
     * 获取用户错题本（分页）
     */
    @Override
    public Page<WrongQuestion> getWrongQuestionBook(Long studentId, Long courseId, int page, int size) {
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .orderByDesc(WrongQuestion::getLastPracticedAt);

        Page<WrongQuestion> result = this.page(Page.of(page, size), queryWrapper);
        fillDerivedFields(result.getRecords());
        return result;
    }

    /**
     * 补全「联表派生的展示字段」：题干 / 选项 / 正确答案 / 解析 / 知识点名。
     *
     * <p>修复（2026-09-23，P2-6）：{@link WrongQuestion} 中这些字段标注为
     * {@code @TableField(exist = false)}，实体注释已写明「联表派生的展示字段（不入库，仅用于
     * 错题本列表展示题干/答案/解析）」，但<b>查询路径从未实现填充</b> —— 导致错题本列表
     * 题干与正确答案恒为空（前端 {@code useWrongQuestions} 依赖 {@code questionContent} 渲染）。
     * 现于查询后按 questionId 补全。</p>
     */
    private void fillDerivedFields(List<WrongQuestion> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (WrongQuestion wq : list) {
            if (wq.getQuestionId() != null) {
                WrongQuestionSnapshotUtil.fillFromQuestion(wq, questionService.getById(wq.getQuestionId()));
            }
        }
    }

    /**
     * 获取未掌握的错题
     */
    @Override
    public List<WrongQuestion> getUnmastered(Long studentId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .eq(WrongQuestion::getMastered, false)
                .orderByAsc(WrongQuestion::getLastPracticedAt);  // 优先显示最早未掌握的

        List<WrongQuestion> list = this.list(queryWrapper);
        fillDerivedFields(list);
        return list;
    }

    /**
     * 按知识点分类的错题
     * 通过关联题目获取知识点信息进行分组
     */
    @Override
    public List<WrongQuestionGroupVO> getByKnowledgePoints(Long studentId, Long courseId) {
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .orderByDesc(WrongQuestion::getLastPracticedAt);

        List<WrongQuestion> wrongQuestions = this.list(queryWrapper);

        // 按知识点名称分组：一道题可关联多个知识点，故会同时出现在多个分组中
        Map<String, List<WrongQuestion>> grouped = new LinkedHashMap<>();
        for (WrongQuestion wq : wrongQuestions) {
            Question question = questionService.getById(wq.getQuestionId());
            // 补齐派生展示字段（题干/选项/答案/知识点名，见 P2-6），否则分组内的题目项字段全为空
            WrongQuestionSnapshotUtil.fillFromQuestion(wq, question);
            for (String kpName : extractKnowledgePointNames(question)) {
                grouped.computeIfAbsent(kpName, k -> new ArrayList<>()).add(wq);
            }
        }

        if (grouped.isEmpty()) {
            return List.of();
        }

        // 回填知识点ID：一次批量查询，避免逐组查询造成 N+1
        Map<String, Long> idByName = new HashMap<>();
        List<KnowledgePoint> points = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(courseId != null, KnowledgePoint::getCourseId, courseId)
                        .in(KnowledgePoint::getName, grouped.keySet()));
        for (KnowledgePoint kp : points) {
            idByName.putIfAbsent(kp.getName(), kp.getId());
        }

        return grouped.entrySet().stream()
                .map(e -> new WrongQuestionGroupVO(idByName.get(e.getKey()), e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(WrongQuestionGroupVO::getCount).reversed())
                .toList();
    }

    /**
     * 提取题目关联的知识点名称。
     *
     * <p>{@code question.knowledge_points} 实际存的是 <b>JSON 数组文本</b>
     * （如 {@code ["Python基础","数据结构"]}），但也兼容历史/手工录入的「逗号分隔纯文本」——
     * 两种形态都能解析。解析不出任何名称时归入「未知知识点」，保证错题不被丢弃。</p>
     */
    private List<String> extractKnowledgePointNames(Question question) {
        if (question == null || question.getKnowledgePoints() == null
                || question.getKnowledgePoints().isBlank()) {
            return List.of(UNKNOWN_KP);
        }
        String raw = question.getKnowledgePoints().trim();
        if (raw.startsWith("[")) {
            try {
                List<String> parsed = JSON_MAPPER.readValue(raw, new TypeReference<List<String>>() { });
                List<String> cleaned = parsed.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                if (!cleaned.isEmpty()) {
                    return cleaned;
                }
            } catch (Exception e) {
                log.debug("知识点 JSON 解析失败，退回分隔符拆分：{}", raw);
            }
        }
        List<String> bySplit = Arrays.stream(raw.split("[,，;；]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return bySplit.isEmpty() ? List.of(UNKNOWN_KP) : bySplit;
    }

    /**
     * 错题统计
     * 返回总数、已掌握数、未掌握数、按来源分类等统计数据
     */
    @Override
    public WrongQuestionStatsVO getWrongQuestionStats(Long studentId, Long courseId) {
        // 总错题数
        LambdaQueryWrapper<WrongQuestion> totalWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId);
        long total = this.count(totalWrapper);

        // 已掌握数
        LambdaQueryWrapper<WrongQuestion> masteredWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .eq(WrongQuestion::getMastered, true);
        long mastered = this.count(masteredWrapper);

        // 掌握率
        double masteryRate = total > 0 ? (double) mastered / total * 100 : 0;

        // 按来源类型分组统计
        List<WrongQuestion> allWrongQuestions = this.list(totalWrapper);
        Map<String, Long> sourceTypeStats = allWrongQuestions.stream()
                .collect(Collectors.groupingBy(
                        wq -> wq.getSourceType() != null ? wq.getSourceType() : "OTHER",
                        Collectors.counting()
                ));

        // 按错因（reason）分组统计
        Map<String, Long> reasonStats = allWrongQuestions.stream()
                .filter(wq -> wq.getReason() != null && !wq.getReason().isEmpty())
                .collect(Collectors.groupingBy(
                        WrongQuestion::getReason,
                        Collectors.counting()
                ));

        // 练习次数分布统计
        long neverPracticed = allWrongQuestions.stream()
                .filter(wq -> wq.getPracticeCount() == null || wq.getPracticeCount() <= 1)
                .count();
        long practicedMultiple = allWrongQuestions.stream()
                .filter(wq -> wq.getPracticeCount() != null && wq.getPracticeCount() > 1)
                .count();
        Map<String, Long> practiceDist = new HashMap<>();
        practiceDist.put("neverOrOnce", neverPracticed);
        practiceDist.put("multipleTimes", practicedMultiple);

        return new WrongQuestionStatsVO(
                total, mastered, total - mastered,
                Math.round(masteryRate * 100.0) / 100.0,
                sourceTypeStats, reasonStats, practiceDist);
    }

    /**
     * 生成错题练习卷（优先选择practiceCount低/练习不足的错题）
     */
    @Override
    public List<Question> generatePracticePaper(Long studentId, Long courseId, int count) {
        log.info("生成错题题目");

        // 查询未掌握的错题，按练习次数升序排列（优先选练习不足的）
        List<WrongQuestion> unmasteredList = getUnmastered(studentId, courseId);

        // 按practiceCount升序排序（练习次数少的优先练习）
        unmasteredList.sort(Comparator.comparingInt(wq ->
                wq.getPracticeCount() != null ? wq.getPracticeCount() : 0));

        // 限制数量
        if (unmasteredList.size() > count) {
            unmasteredList = unmasteredList.subList(0, count);
        }

        // 提取questionId列表
        List<Long> questionIds = unmasteredList.stream()
                .map(WrongQuestion::getQuestionId)
                .collect(Collectors.toList());

        if (questionIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询题目
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIds)
                .eq(Question::getIsActive, true);

        List<Question> questions = questionService.list(questionWrapper);

        // 打乱顺序返回
        Collections.shuffle(questions);
        return questions;
    }

    /**
     * 推荐相关题目（基于同一知识点的其他题目）
     * 查询错题对应的Question → 获取其knowledgePoints → 查询相同知识点的其他题目 → 排除已掌握的
     */
    @Override
    public List<Question> recommendRelatedQuestions(Long wrongQuestionId) {
        // 获取错题记录
        WrongQuestion wrongQuestion = this.getById(wrongQuestionId);
        if (wrongQuestion == null) {
            throw new BusinessException("错题记录不存在");
        }

        // 获取错题对应的题目
        Question question = questionService.getById(wrongQuestion.getQuestionId());
        if (question == null || question.getKnowledgePoints() == null || question.getKnowledgePoints().isEmpty()) {
            return new ArrayList<>();
        }

        // 获取该学生已掌握的错题对应的所有题目ID
        LambdaQueryWrapper<WrongQuestion> masteredWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, wrongQuestion.getStudentId())
                .eq(WrongQuestion::getMastered, true);
        List<WrongQuestion> masteredList = this.list(masteredWrapper);
        Set<Long> masteredQuestionIds = masteredList.stream()
                .map(WrongQuestion::getQuestionId)
                .collect(Collectors.toSet());

        // 排除当前错题本身
        masteredQuestionIds.add(wrongQuestion.getQuestionId());

        // 查询相同知识点的其他题目（排除已掌握的和当前题目）
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<Question>()
                .like(Question::getKnowledgePoints, question.getKnowledgePoints())
                .notIn(Question::getId, masteredQuestionIds)
                .eq(Question::getIsActive, true)
                .last("LIMIT 10");

        return questionService.list(questionWrapper);
    }

    /**
     * 添加错题（支持可选填写错因分析）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WrongQuestion addWrongQuestion(Long studentId, Long questionId, Long courseId,
                                           String wrongAnswer, String sourceType, String reason) {
                                               log.info("新增错题题目");

        // 复用recordWrongAnswer逻辑，额外设置reason字段
        WrongQuestion result = recordWrongAnswer(studentId, questionId, courseId, wrongAnswer, sourceType);
        // 如果提供了错因分析，更新到记录中（仅在新建时或reason为空时写入）
        if (reason != null && !reason.isEmpty()) {
            LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                    .eq(WrongQuestion::getStudentId, studentId)
                    .eq(WrongQuestion::getQuestionId, questionId);
            WrongQuestion existing = this.getOne(queryWrapper);
            if (existing != null) {
                existing.setReason(reason);
                this.updateById(existing);
                return existing;
            }
        }
        return result;
    }
}
