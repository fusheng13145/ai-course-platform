package com.aicourse.service.component;

import com.aicourse.entity.ExamPaper;
import com.aicourse.entity.ExamSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ExamSessionMapper;
import com.aicourse.service.ExamPaperService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 考试会话作弊检测组件：综合检测 IP/设备变化、异常短时答题、超时提交，并记录作弊标记。
 * <p>拆分自原 ExamSessionServiceImpl（2026-08-29）。</p>
 */
@Slf4j
@Component
public class ExamSessionCheatingComponent {

    private final ExamPaperService examPaperService;
    private final ExamSessionMapper examSessionMapper;
    private final ObjectMapper objectMapper;

    public ExamSessionCheatingComponent(ExamPaperService examPaperService,
                                        ExamSessionMapper examSessionMapper,
                                        ObjectMapper objectMapper) {
        this.examPaperService = examPaperService;
        this.examSessionMapper = examSessionMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 检测作弊嫌疑
     */
    public List<String> detectCheating(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        List<String> suspicions = new ArrayList<>();

        // 检测IP地址变化：查询该学生在同一试卷的其他会话
        List<ExamSession> otherSessions = examSessionMapper.selectList(new LambdaQueryWrapper<ExamSession>()
                .eq(ExamSession::getExamPaperId, session.getExamPaperId())
                .eq(ExamSession::getStudentId, session.getStudentId())
                .ne(ExamSession::getId, sessionId));

        for (ExamSession other : otherSessions) {
            if (other.getIpAddress() != null && session.getIpAddress() != null &&
                    !other.getIpAddress().equals(session.getIpAddress())) {
                suspicions.add("检测到IP地址变化：" + other.getIpAddress() + " -> " + session.getIpAddress());
            }
            if (other.getUserAgent() != null && session.getUserAgent() != null &&
                    !other.getUserAgent().equals(session.getUserAgent())) {
                suspicions.add("检测到浏览器/设备变化");
            }
        }

        // 检测异常短的答题时间
        if (session.getTimeSpent() != null && session.getTimeSpent() < 60) {
            suspicions.add("答题时间过短（少于1分钟），可能存在异常");
        }

        // 检测是否超时后提交
        if (session.getSubmitTime() != null) {
            ExamPaper paper = examPaperService.getById(session.getExamPaperId());
            if (paper != null && paper.getEndTime() != null &&
                    session.getSubmitTime().isAfter(paper.getEndTime())) {
                suspicions.add("在考试结束后提交");
            }
        }

        return suspicions;
    }

    /**
     * 作弊检测与标记（综合检测IP变化、异常行为等，记录检测结果）
     * 检测项包括：IP地址变化、浏览器/设备变化、异常短时答题、超时提交等
     */
    public Map<String, Object> checkCheating(Long sessionId) {
        ExamSession session = examSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("考试会话不存在");
        }

        List<String> suspicions = detectCheating(sessionId);
        boolean hasSuspicion = !suspicions.isEmpty();

        // 将作弊检测结果以 JSON 树形式写入 answers 字段的扩展区域（复用已有字段存储标记信息）。
        // 修复 P0-1：原实现用 String.replace("}", ...) 字符串级注入，会破坏答案 JSON 中
        // 所有嵌套对象与含 "}" 的文本，导致该场考试无法自动阅卷。现改为 Jackson 树操作：
        // 将现有 answers 解析为 JsonNode，向根对象 put("cheatingFlags", ...) 后重新序列化，
        // 对任何合法 JSON 均幂等安全。
        if (hasSuspicion) {
            ObjectNode flags = objectMapper.createObjectNode();
            flags.put("detectedAt", LocalDateTime.now().toString());
            flags.put("flagCount", suspicions.size());
            com.fasterxml.jackson.databind.node.ArrayNode details = flags.putArray("details");
            for (String s : suspicions) {
                details.add(s);
            }

            try {
                JsonNode root = objectMapper.readTree(session.getAnswers() != null ? session.getAnswers() : "{}");
                ObjectNode merged;
                if (root == null || root.isNull() || root.isMissingNode() || !root.isObject()) {
                    merged = objectMapper.createObjectNode();
                } else {
                    merged = (ObjectNode) root;
                }
                merged.set("cheatingFlags", flags);
                session.setAnswers(objectMapper.writeValueAsString(merged));
                examSessionMapper.updateById(session);
            } catch (JsonProcessingException e) {
                // 答案数据损坏（历史遗留非法 JSON）时兜底：以作弊标记独立 JSON 保存，
                // 不覆盖也无法修复原答案，但绝不能把损坏数据再次写回。
                log.warn("checkCheating 解析 answers 失败，作弊标记将以独立 JSON 记录: sessionId={}", sessionId, e);
                session.setAnswers("{\"cheatingFlags\":" + flags.toString() + "}");
                examSessionMapper.updateById(session);
            }
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("hasSuspicion", hasSuspicion);
        result.put("suspicionCount", suspicions.size());
        result.put("suspicions", suspicions);
        result.put("riskLevel", determineRiskLevel(suspicions));
        result.put("checkedAt", LocalDateTime.now());

        return result;
    }

    /**
     * 根据可疑项数量判定风险等级
     */
    public String determineRiskLevel(List<String> suspicions) {
        if (suspicions.size() >= 3) return "HIGH";
        if (suspicions.size() >= 1) return "MEDIUM";
        return "LOW";
    }
}
