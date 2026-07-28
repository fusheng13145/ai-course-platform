package com.aicourse.service.impl;

import com.aicourse.entity.ProctorEvent;
import com.aicourse.entity.ProctorSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ProctorEventMapper;
import com.aicourse.mapper.ProctorSessionMapper;
import com.aicourse.service.ProctorService;
import com.aicourse.service.SystemConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线监考（考试防作弊）服务实现
 */
@Service
public class ProctorServiceImpl extends ServiceImpl<ProctorSessionMapper, ProctorSession> implements ProctorService {

    private final ProctorSessionMapper sessionMapper;
    private final ProctorEventMapper eventMapper;
    private final SystemConfigService systemConfigService;

    /** 阈值配置键名（持久化于 system_configs，group=proctor） */
    private static final String CFG_HIGH = "proctor.threshold.high";
    private static final String CFG_MEDIUM = "proctor.threshold.medium";
    private static final String CFG_W_LOW = "proctor.weight.low";
    private static final String CFG_W_MEDIUM = "proctor.weight.medium";
    private static final String CFG_W_HIGH = "proctor.weight.high";

    /** 阈值默认值（配置缺失时回退） */
    private static final int DEF_HIGH = 6;
    private static final int DEF_MEDIUM = 3;
    private static final int DEF_W_LOW = 1;
    private static final int DEF_W_MEDIUM = 2;
    private static final int DEF_W_HIGH = 3;

    public ProctorServiceImpl(ProctorSessionMapper sessionMapper, ProctorEventMapper eventMapper,
                             SystemConfigService systemConfigService) {
        this.sessionMapper = sessionMapper;
        this.eventMapper = eventMapper;
        this.systemConfigService = systemConfigService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProctorSession startSession(Long examSessionId, Long studentId) {
        LambdaQueryWrapper<ProctorSession> qw = new LambdaQueryWrapper<ProctorSession>()
                .eq(ProctorSession::getExamSessionId, examSessionId)
                .eq(ProctorSession::getStudentId, studentId)
                .eq(ProctorSession::getStatus, ProctorSession.STATUS_IN_PROGRESS);
        List<ProctorSession> list = this.list(qw);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        ProctorSession s = new ProctorSession();
        s.setExamSessionId(examSessionId);
        s.setStudentId(studentId);
        s.setStatus(ProctorSession.STATUS_IN_PROGRESS);
        s.setStartedAt(LocalDateTime.now());
        s.setRiskScore(0);
        s.setRiskLevel(ProctorSession.LEVEL_LOW);
        this.save(s);
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProctorEvent logEvent(Long sessionId, String type, String severity, String detail, String evidenceUrl) {
        ProctorSession s = this.getById(sessionId);
        if (s == null) {
            throw new BusinessException("监考会话不存在");
        }
        ProctorEvent e = new ProctorEvent();
        e.setProctorSessionId(sessionId);
        e.setType(type);
        e.setSeverity(severity);
        e.setDetail(detail);
        e.setEvidenceUrl(evidenceUrl);
        e.setCapturedAt(LocalDateTime.now());
        e.setStatus(ProctorEvent.STATUS_PENDING);
        eventMapper.insert(e);

        int score = (s.getRiskScore() == null ? 0 : s.getRiskScore()) + severityWeight(severity);
        s.setRiskScore(score);
        s.setRiskLevel(computeLevel(score));
        if (ProctorSession.LEVEL_HIGH.equals(s.getRiskLevel())
                && !ProctorSession.STATUS_ENDED.equals(s.getStatus())) {
            s.setStatus(ProctorSession.STATUS_FLAGGED);
        }
        this.updateById(s);
        return e;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProctorSession endSession(Long sessionId) {
        ProctorSession s = this.getById(sessionId);
        if (s == null) {
            throw new BusinessException("监考会话不存在");
        }
        s.setStatus(ProctorSession.STATUS_ENDED);
        s.setEndedAt(LocalDateTime.now());
        this.updateById(s);
        return s;
    }

    @Override
    public List<ProctorEvent> listEvents(Long sessionId) {
        return eventMapper.selectList(new LambdaQueryWrapper<ProctorEvent>()
                .eq(ProctorEvent::getProctorSessionId, sessionId)
                .orderByAsc(ProctorEvent::getCapturedAt));
    }

    @Override
    public ProctorSession getStudentSession(Long examSessionId, Long studentId) {
        List<ProctorSession> list = this.list(new LambdaQueryWrapper<ProctorSession>()
                .eq(ProctorSession::getExamSessionId, examSessionId)
                .eq(ProctorSession::getStudentId, studentId)
                .orderByDesc(ProctorSession::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<Map<String, Object>> riskSummary(Long examSessionId) {
        List<ProctorSession> sessions = this.list(new LambdaQueryWrapper<ProctorSession>()
                .eq(ProctorSession::getExamSessionId, examSessionId));
        List<Map<String, Object>> res = new ArrayList<>();
        for (ProctorSession s : sessions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sessionId", s.getId());
            m.put("studentId", s.getStudentId());
            m.put("status", s.getStatus());
            m.put("riskScore", s.getRiskScore());
            m.put("riskLevel", s.getRiskLevel());
            List<ProctorEvent> evs = listEvents(s.getId());
            m.put("eventCount", evs.size());
            Map<String, Long> typeCounts = new LinkedHashMap<>();
            for (ProctorEvent e : evs) {
                typeCounts.merge(e.getType(), 1L, Long::sum);
            }
            m.put("typeCounts", typeCounts);
            res.add(m);
        }
        return res;
    }

    @Override
    @Cacheable(cacheNames = "proctor-config", key = "'thresholds'")
    public Map<String, Object> getThresholds() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("high", parseInt(systemConfigService.getConfigValueOrDefault(CFG_HIGH, String.valueOf(DEF_HIGH))));
        m.put("medium", parseInt(systemConfigService.getConfigValueOrDefault(CFG_MEDIUM, String.valueOf(DEF_MEDIUM))));
        m.put("weightLow", parseInt(systemConfigService.getConfigValueOrDefault(CFG_W_LOW, String.valueOf(DEF_W_LOW))));
        m.put("weightMedium", parseInt(systemConfigService.getConfigValueOrDefault(CFG_W_MEDIUM, String.valueOf(DEF_W_MEDIUM))));
        m.put("weightHigh", parseInt(systemConfigService.getConfigValueOrDefault(CFG_W_HIGH, String.valueOf(DEF_W_HIGH))));
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "proctor-config", key = "'thresholds'")
    public void updateThresholds(Map<String, Integer> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            return;
        }
        upsert(CFG_HIGH, thresholds.get("high"), DEF_HIGH);
        upsert(CFG_MEDIUM, thresholds.get("medium"), DEF_MEDIUM);
        upsert(CFG_W_LOW, thresholds.get("weightLow"), DEF_W_LOW);
        upsert(CFG_W_MEDIUM, thresholds.get("weightMedium"), DEF_W_MEDIUM);
        upsert(CFG_W_HIGH, thresholds.get("weightHigh"), DEF_W_HIGH);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProctorEvent reviewEvent(Long eventId, String status, String reviewNote, Long reviewerId) {
        ProctorEvent e = eventMapper.selectById(eventId);
        if (e == null) {
            throw new BusinessException("监考事件不存在");
        }
        if (!ProctorEvent.STATUS_REVIEWED.equals(status) && !ProctorEvent.STATUS_EXEMPTED.equals(status)) {
            throw new BusinessException("复核状态非法，仅支持 REVIEWED / EXEMPTED");
        }
        e.setStatus(status);
        e.setReviewNote(reviewNote);
        e.setReviewedAt(LocalDateTime.now());
        e.setReviewerId(reviewerId);
        eventMapper.updateById(e);
        return e;
    }

    @Override
    public ProctorEvent getEvent(Long eventId) {
        return eventMapper.selectById(eventId);
    }

    // ===================== 内部工具 =====================

    private void upsert(String key, Integer value, int def) {
        int v = value != null ? value : def;
        String existing = systemConfigService.getConfigValue(key);
        if (existing == null) {
            com.aicourse.entity.SystemConfig cfg = new com.aicourse.entity.SystemConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(String.valueOf(v));
            cfg.setConfigGroup("proctor");
            cfg.setConfigType("NUMBER");
            cfg.setDescription(key);
            cfg.setIsPublic(false);
            cfg.setSortOrder(10);
            systemConfigService.save(cfg);
        } else {
            systemConfigService.updateConfig(key, String.valueOf(v));
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private int severityWeight(String severity) {
        Map<String, Object> t = getThresholds();
        if (ProctorEvent.SEV_HIGH.equals(severity)) {
            return (int) t.get("weightHigh");
        }
        if (ProctorEvent.SEV_MEDIUM.equals(severity)) {
            return (int) t.get("weightMedium");
        }
        return (int) t.get("weightLow");
    }

    private String computeLevel(int score) {
        Map<String, Object> t = getThresholds();
        int high = (int) t.get("high");
        int medium = (int) t.get("medium");
        if (score >= high) {
            return ProctorSession.LEVEL_HIGH;
        }
        if (score >= medium) {
            return ProctorSession.LEVEL_MEDIUM;
        }
        return ProctorSession.LEVEL_LOW;
    }
}
