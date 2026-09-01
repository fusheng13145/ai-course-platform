package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.PlagiarismCheck;
import com.aicourse.entity.PlagiarismMatch;
import com.aicourse.entity.Submission;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.PlagiarismCheckMapper;
import com.aicourse.mapper.SubmissionMapper;
import com.aicourse.service.PlagiarismCheckService;
import com.aicourse.service.PlagiarismMatchService;
import com.aicourse.service.SubmissionService;
import com.aicourse.vo.PlagiarismCheckResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.aicourse.common.ApiCode;

/**
 * 查重检测服务实现类
 * 使用基于词重叠（Jaccard相似度）的文本比较算法
 */
@Service
public class PlagiarismCheckServiceImpl extends ServiceImpl<PlagiarismCheckMapper, PlagiarismCheck> implements PlagiarismCheckService {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismCheckServiceImpl.class);

    private final SubmissionService submissionService;
    private final SubmissionMapper submissionMapper;
    private final PlagiarismMatchService plagiarismMatchService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 高相似度判定阈值（默认0.3即30%） */
    private static final BigDecimal HIGH_SIMILARITY_THRESHOLD = new BigDecimal("0.3");

    public PlagiarismCheckServiceImpl(PlagiarismCheckMapper plagiarismCheckMapper,
                                      @Lazy SubmissionService submissionService,
                                      SubmissionMapper submissionMapper,
                                      PlagiarismMatchService plagiarismMatchService,
                                      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.baseMapper = plagiarismCheckMapper;
        this.submissionService = submissionService;
        this.submissionMapper = submissionMapper;
        this.plagiarismMatchService = plagiarismMatchService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建查重任务（初始状态为PENDING）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlagiarismCheck createCheck(Long submissionId, String checkType) {
        log.info("创建查重检查");

        // 验证提交记录是否存在
        Submission submission = submissionService.getById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }
        if (submission.getContent() == null || submission.getContent().isEmpty()) {
            throw new BusinessException("提交内容为空，无法进行查重");
        }
        // 创建查重任务
        PlagiarismCheck check = new PlagiarismCheck();
        check.setSubmissionId(submissionId);
        check.setTaskId(submission.getTaskId());
        check.setStudentId(submission.getStudentId());
        check.setCheckType(checkType != null ? checkType : "TEXT");
        check.setStatus("PENDING");
        check.setSimilarityScore(BigDecimal.ZERO);
        check.setHighSimilarityCount(0);
        this.save(check);
        return check;
    }

    /**
     * 执行查重检测
     * 算法流程：
     * 1. 获取当前提交内容
     * 2. 获取同任务下其他提交
     * 3. 逐对进行词重叠/Jaccard相似度计算
     * 4. 对超过阈值的匹配创建明细记录
     * 5. 汇总结果并更新状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeCheck(Long checkId) {
        // 获取查重任务
        PlagiarismCheck check = this.getById(checkId);
        if (check == null) {
            throw new BusinessException("查重任务不存在");
        }
        try {
            // 更新状态为检测中
            check.setStatus("CHECKING");
            this.updateById(check);
            // 获取当前提交的内容
            Submission currentSubmission = submissionService.getById(check.getSubmissionId());
            if (currentSubmission == null || currentSubmission.getContent() == null) {
                throw new BusinessException("无法获取提交内容");
            }
            String currentText = currentSubmission.getContent().trim();
            // 获取同任务下其他提交（排除自身）
            LambdaQueryWrapper<Submission> otherWrapper = new LambdaQueryWrapper<Submission>()
                    .eq(Submission::getTaskId, check.getTaskId())
                    .ne(Submission::getId, check.getSubmissionId())
                    .isNotNull(Submission::getContent);
            List<Submission> otherSubmissions = submissionMapper.selectList(otherWrapper);
            // 存储所有匹配结果
            List<Map<String, Object>> allMatches = new ArrayList<>();
            BigDecimal maxSimilarity = BigDecimal.ZERO;
            int highSimCount = 0;
            // 逐对比较
            for (Submission other : otherSubmissions) {
                String otherText = other.getContent().trim();
                if (otherText.isEmpty()) {
                    continue;
                }
                // 计算Jaccard相似度（基于词汇重叠）
                Map<String, Object> compareResult = calculateSimilarity(currentText, otherText);
                BigDecimal similarity = (BigDecimal) compareResult.get("similarity");
                // 更新最高相似度
                if (similarity.compareTo(maxSimilarity) > 0) {
                    maxSimilarity = similarity;
                }
                // 判定是否为高相似度匹配
                if (similarity.compareTo(HIGH_SIMILARITY_THRESHOLD) >= 0) {
                    highSimCount++;
                    // 构建匹配明细
                    Map<String, Object> matchData = new HashMap<>();
                    matchData.put("matchedSubmissionId", other.getId());
                    matchData.put("matchedSource", "学生ID:" + other.getStudentId() + "的提交");
                    matchData.put("similarity", similarity);
                    matchData.put("originalText", compareResult.get("originalFragment"));
                    matchData.put("matchedText", compareResult.get("matchedFragment"));
                    // 根据相似度判断匹配类型
                    String matchType = determineMatchType(similarity);
                    matchData.put("matchType", matchType);
                    allMatches.add(matchData);
                }
            }
            // 批量保存高相似度匹配明细
            if (!allMatches.isEmpty()) {
                plagiarismMatchService.batchSaveMatches(checkId, allMatches);
            }
            // 生成检测结果摘要
            String summary = generateResultSummary(maxSimilarity, highSimCount, otherSubmissions.size());

            // 将详细匹配结果序列化为JSON存入checkDetail字段（含算法版本信息）
            String checkDetailJson = buildCheckDetailJson(allMatches, maxSimilarity, highSimCount, otherSubmissions.size());
            check.setCheckDetail(checkDetailJson);

            // 更新查重任务结果
            check.setSimilarityScore(maxSimilarity.setScale(4, RoundingMode.HALF_UP));
            check.setHighSimilarityCount(highSimCount);
            check.setResultSummary(summary);
            check.setStatus("COMPLETED");
            check.setCheckedAt(LocalDateTime.now());
            this.updateById(check);
        } catch (BusinessException e) {
            log.error("执行查重检查失败: {}", e.getMessage(), e);

            // 业务异常直接抛出
            check.setStatus("FAILED");
            check.setErrorMessage(e.getMessage());
            this.updateById(check);
            throw e;
        } catch (Exception e) {
            log.error("执行查重检查失败: {}", e.getMessage(), e);

            // 其他异常记录后抛出
            check.setStatus("FAILED");
            check.setErrorMessage("查重执行异常: " + e.getMessage());
            this.updateById(check);
            throw new BusinessException(ApiCode.INTERNAL_SERVER_ERROR, "查重执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取查重结果详情
     */
    @Override
    public PlagiarismCheckResultVO getCheckResult(Long checkId) {
        PlagiarismCheck check = this.getById(checkId);
        if (check == null) {
            throw new BusinessException("查重任务不存在");
        }
        // 获取该查重任务的所有匹配明细
        LambdaQueryWrapper<PlagiarismMatch> matchWrapper = new LambdaQueryWrapper<PlagiarismMatch>()
                .eq(PlagiarismMatch::getPlagiarismCheckId, checkId)
                .orderByDesc(PlagiarismMatch::getSimilarity);
        List<PlagiarismMatch> matches = plagiarismMatchService.list(matchWrapper);

        // 解析checkDetail JSON返回结构化数据
        // 原实现为条件 put（键缺失即不出现），VO 侧以 null + 字段级 NON_NULL 精确复刻该语义
        String detail = check.getCheckDetail();
        boolean hasDetail = detail != null && !detail.isEmpty();
        String checkDetailRaw = hasDetail ? detail : null;
        String algorithmVersion = (hasDetail && detail.contains("algorithmVersion"))
                ? "Jaccard-CJKBigram-v2.0" : null;

        return new PlagiarismCheckResultVO(check, check.getSimilarityScore(), check.getHighSimilarityCount(),
                check.getResultSummary(), check.getStatus(), matches, checkDetailRaw, algorithmVersion);
    }

    /**
     * 获取高相似度匹配列表（超过指定阈值）
     */
    @Override
    public List<PlagiarismMatch> getHighSimilarityMatches(Long checkId, BigDecimal threshold) {
        LambdaQueryWrapper<PlagiarismMatch> queryWrapper = new LambdaQueryWrapper<PlagiarismMatch>()
                .eq(PlagiarismMatch::getPlagiarismCheckId, checkId)
                .ge(PlagiarismMatch::getSimilarity, threshold)
                .orderByDesc(PlagiarismMatch::getSimilarity);
        return plagiarismMatchService.list(queryWrapper);
    }

    /**
     * 获取某提交的所有查重历史记录
     */
    @Override
    public List<PlagiarismCheck> getSubmissionChecks(Long submissionId) {
        LambdaQueryWrapper<PlagiarismCheck> queryWrapper = new LambdaQueryWrapper<PlagiarismCheck>()
                .eq(PlagiarismCheck::getSubmissionId, submissionId)
                .orderByDesc(PlagiarismCheck::getCreatedAt);
        return this.list(queryWrapper);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算两段文本之间的Jaccard相似度（基于词汇重叠）
     * Jaccard = |A ∩ B| / |A ∪ B|
     *
     * @param text1 文本一
     * @param text2 文本二
     * @return 包含similarity、originalFragment、matchedFragment的结果Map
     */
    private Map<String, Object> calculateSimilarity(String text1, String text2) {
        // 分词（按空格和标点切分）
        Set<String> words1 = tokenize(text1);
        Set<String> words2 = tokenize(text2);
        // 计算交集和并集
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        // 计算Jaccard相似度
        double similarity = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        // 提取公共片段用于展示
        String originalFragment = extractCommonFragment(text1, words1, intersection);
        String matchedFragment = extractCommonFragment(text2, words2, intersection);
        Map<String, Object> result = new HashMap<>();
        result.put("similarity", BigDecimal.valueOf(similarity));
        result.put("originalFragment", originalFragment);
        result.put("matchedFragment", matchedFragment);
        return result;
    }

    /**
     * 中英文混合分词（v2.0，与 RAG 检索的 bigram 策略一致）：
     * <ul>
     *   <li>中文（CJK）连续段：滑窗生成 bigram（二字词元），显著提升中文改写/复制的召回率；
     *       旧版按标点切分会把整个中文子句当作单一词元，导致中文查重几乎失效。</li>
     *   <li>英文/数字：按非字母数字切分，小写化，保留长度&gt;1 的词。</li>
     * </ul>
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        // 1) 提取 CJK 连续段并生成 bigram
        java.util.regex.Matcher cjk = java.util.regex.Pattern
                .compile("[\\u4E00-\\u9FFF]+").matcher(text);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() == 1) {
                tokens.add(run);
            } else {
                for (int i = 0; i < run.length() - 1; i++) {
                    tokens.add(run.substring(i, i + 2));
                }
            }
        }
        // 2) 非 CJK 部分：按非字母数字切分
        String nonCjk = text.replaceAll("[\\u4E00-\\u9FFF]+", " ");
        for (String t : nonCjk.split("[^a-zA-Z0-9]+")) {
            if (t.length() > 1) {
                tokens.add(t.toLowerCase());
            }
        }
        return tokens;
    }

    /**
     * 从原文中提取包含公共词元的片段。
     * v2.0：定位公共词元在原文中的首个命中位置，截取其前后共约100字符的上下文，
     * 比旧版罗列词元更利于人工比对；最多拼接两处命中，合计不超过200字符。
     */
    private String extractCommonFragment(String fullText, Set<String> allWords, Set<String> commonWords) {
        StringBuilder fragment = new StringBuilder();
        int segments = 0;
        int lastEnd = -1;
        for (String word : commonWords) {
            int idx = fullText.indexOf(word);
            if (idx < 0 || (lastEnd >= 0 && idx < lastEnd)) {
                continue; // 未命中或与上一片段重叠
            }
            int start = Math.max(0, idx - 30);
            int end = Math.min(fullText.length(), idx + word.length() + 70);
            if (fragment.length() > 0) {
                fragment.append(" ... ");
            }
            fragment.append(fullText, start, end);
            lastEnd = end;
            if (++segments >= 2 || fragment.length() > 200) {
                break;
            }
        }
        return fragment.length() > 200 ? fragment.substring(0, 200) : fragment.toString();
    }

    /**
     * 根据相似度值判定匹配类型
     */
    private String determineMatchType(BigDecimal similarity) {
        if (similarity.compareTo(new BigDecimal("0.7")) >= 0) {
            return "EXACT";       // 高度疑似整段复制
        } else if (similarity.compareTo(new BigDecimal("0.4")) >= 0) {
            return "PARAPHRASE";  // 中等疑似改写
        } else {
            return "STRUCTURE";   // 低度疑似结构相似
        }
    }

    /**
     * 生成检测结果摘要
     */
    private String generateResultSummary(BigDecimal maxSimilarity, int highSimCount, int totalCompared) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次查重共与").append(totalCompared).append("份其他提交进行了比对。");
        sb.append("最高相似度为").append(maxSimilarity.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)).append("%。");
        if (highSimCount > 0) {
            sb.append("发现").append(highSimCount).append("处高相似度匹配（≥30%），建议重点关注。");
        } else {
            sb.append("未发现明显的高相似度匹配，原创性较好。");
        }
        return sb.toString();
    }

    /**
     * 构建查重详细结果JSON（含算法版本、匹配明细等结构化数据）
     * 使用 Jackson ObjectMapper 序列化，替代手写字符串拼接以防止转义/格式错误。
     */
    private String buildCheckDetailJson(List<Map<String, Object>> allMatches,
                                         BigDecimal maxSimilarity, int highSimCount, int totalCompared) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = objectMapper.createObjectNode();
            root.put("algorithmVersion", "Jaccard-CJKBigram-v2.0");
            root.put("algorithm", "Jaccard_Similarity");
            root.put("checkedAt", LocalDateTime.now().toString());
            root.put("totalCompared", totalCompared);
            root.put("maxSimilarity", maxSimilarity.setScale(4, RoundingMode.HALF_UP));
            root.put("highSimilarityCount", highSimCount);

            com.fasterxml.jackson.databind.node.ArrayNode matchesArray = root.putArray("matches");
            for (Map<String, Object> match : allMatches) {
                com.fasterxml.jackson.databind.node.ObjectNode matchNode = matchesArray.addObject();
                matchNode.put("matchedSubmissionId", match.get("matchedSubmissionId") != null
                        ? ((Number) match.get("matchedSubmissionId")).longValue() : 0);
                matchNode.put("matchedSource", String.valueOf(match.get("matchedSource")));
                matchNode.put("similarity", match.get("similarity") != null
                        ? ((BigDecimal) match.get("similarity")).doubleValue() : 0.0);
                matchNode.put("matchType", String.valueOf(match.get("matchType")));
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("查重结果JSON序列化失败，回退到安全默认值");
            return "{\"algorithmVersion\":\"Jaccard-CJKBigram-v2.0\",\"algorithm\":\"Jaccard_Similarity\",\"error\":\"序列化失败\"}";
        }
    }
}
