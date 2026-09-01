package com.aicourse.service.impl;

import com.aicourse.entity.ProctorEvent;
import com.aicourse.entity.ProctorSession;
import com.aicourse.entity.SystemConfig;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ProctorEventMapper;
import com.aicourse.mapper.ProctorSessionMapper;
import com.aicourse.service.SystemConfigService;
import com.aicourse.vo.ProctorRiskSummaryVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ProctorServiceImpl 单元测试（B 主战场 P1，缺口 718）。
 * extends ServiceImpl<ProctorSessionMapper, ProctorSession>：手动构造 + 注入 baseMapper(mock)；
 * ProctorSession / ProctorEvent lambda wrapper 需注册 TableInfo；
 * SystemConfigService 为 mock（getConfigValueOrDefault 默认回退默认值，updateThresholds 走 save/updateConfig）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProctorServiceImplTest {

    @Mock private ProctorSessionMapper sessionMapper;
    @Mock private ProctorEventMapper eventMapper;
    @Mock private SystemConfigService systemConfigService;

    private ProctorServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProctorSession.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProctorEvent.class);

        service = new ProctorServiceImpl(sessionMapper, eventMapper, systemConfigService);
        ReflectionTestUtils.setField(service, "baseMapper", sessionMapper);

        when(sessionMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sessionMapper.selectById(anyLong())).thenReturn(null);
        when(sessionMapper.insert(any(ProctorSession.class))).thenReturn(1);
        when(sessionMapper.updateById(any(ProctorSession.class))).thenReturn(1);

        when(eventMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(eventMapper.selectById(anyLong())).thenReturn(null);
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.insert(any(ProctorEvent.class))).thenReturn(1);
        when(eventMapper.updateById(any(ProctorEvent.class))).thenReturn(1);

        // getConfigValueOrDefault(key, def) 默认返回 def（即源码内建的默认值）
        when(systemConfigService.getConfigValueOrDefault(anyString(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(systemConfigService.getConfigValue(anyString())).thenReturn(null);
        when(systemConfigService.save(any(SystemConfig.class))).thenReturn(true);
        doNothing().when(systemConfigService).updateConfig(anyString(), anyString());
    }

    private ProctorSession session(Long id) {
        ProctorSession s = new ProctorSession();
        s.setId(id); s.setExamSessionId(100L); s.setStudentId(11L);
        s.setStatus(ProctorSession.STATUS_IN_PROGRESS);
        s.setRiskScore(0); s.setRiskLevel(ProctorSession.LEVEL_LOW);
        return s;
    }

    private ProctorEvent event(Long id) {
        ProctorEvent e = new ProctorEvent();
        e.setId(id); e.setProctorSessionId(1L); e.setType("FACE_MISSING");
        e.setSeverity(ProctorEvent.SEV_MEDIUM); e.setStatus(ProctorEvent.STATUS_PENDING);
        return e;
    }

    // ===================== startSession =====================

    @Test
    @DisplayName("startSession 无进行中会话则新建")
    void start_new() {
        when(sessionMapper.selectList(any())).thenReturn(new ArrayList<>());
        ProctorSession r = service.startSession(100L, 11L);
        assertEquals(ProctorSession.STATUS_IN_PROGRESS, r.getStatus());
        verify(sessionMapper).insert(any(ProctorSession.class));
    }

    @Test
    @DisplayName("startSession 已有进行中会话则复用")
    void start_existing() {
        when(sessionMapper.selectList(any())).thenReturn(List.of(session(1L)));
        ProctorSession r = service.startSession(100L, 11L);
        assertEquals(1L, r.getId());
        verify(sessionMapper, never()).insert(any(ProctorSession.class));
    }

    // ===================== logEvent =====================

    @Test
    @DisplayName("logEvent 正常记录并更新风险分")
    void logEvent_ok() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        ProctorEvent r = service.logEvent(1L, "FACE_MISSING", ProctorEvent.SEV_MEDIUM, "d", "u");
        assertEquals(ProctorEvent.STATUS_PENDING, r.getStatus());
        verify(eventMapper).insert(any(ProctorEvent.class));
        verify(sessionMapper).updateById(any(ProctorSession.class));
    }

    @Test
    @DisplayName("logEvent 会话不存在抛异常")
    void logEvent_null() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.logEvent(1L, "t", ProctorEvent.SEV_LOW, "d", "u"));
    }

    @Test
    @DisplayName("logEvent 高分触发高风险标记")
    void logEvent_highRisk() {
        // 将 high 阈值调低为 2，使单次中危(权重2)即达高风险
        when(systemConfigService.getConfigValueOrDefault(anyString(), anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if ("proctor.threshold.high".equals(key)) return "2";
            return inv.getArgument(1);
        });
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        service.logEvent(1L, "FACE_MISSING", ProctorEvent.SEV_MEDIUM, "d", "u");
        ArgumentCaptor<ProctorSession> cap = ArgumentCaptor.forClass(ProctorSession.class);
        verify(sessionMapper).updateById(cap.capture());
        assertEquals(ProctorSession.LEVEL_HIGH, cap.getValue().getRiskLevel());
        assertEquals(ProctorSession.STATUS_FLAGGED, cap.getValue().getStatus());
    }

    // ===================== endSession =====================

    @Test
    @DisplayName("endSession 正常结束")
    void end_ok() {
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        ProctorSession r = service.endSession(1L);
        assertEquals(ProctorSession.STATUS_ENDED, r.getStatus());
        verify(sessionMapper).updateById(any(ProctorSession.class));
    }

    @Test
    @DisplayName("endSession 会话不存在抛异常")
    void end_null() {
        when(sessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.endSession(1L));
    }

    // ===================== listEvents / getStudentSession =====================

    @Test
    @DisplayName("listEvents 返回事件列表")
    void listEvents_ok() {
        when(eventMapper.selectList(any())).thenReturn(List.of(event(1L)));
        assertEquals(1, service.listEvents(1L).size());
    }

    @Test
    @DisplayName("getStudentSession 无记录返回 null")
    void studentSession_none() {
        when(sessionMapper.selectList(any())).thenReturn(new ArrayList<>());
        assertNull(service.getStudentSession(100L, 11L));
    }

    @Test
    @DisplayName("getStudentSession 有记录返回")
    void studentSession_found() {
        when(sessionMapper.selectList(any())).thenReturn(List.of(session(1L)));
        assertNotNull(service.getStudentSession(100L, 11L));
    }

    // ===================== riskSummary =====================

    @Test
    @DisplayName("riskSummary 聚合会话与事件")
    void riskSummary_ok() {
        when(sessionMapper.selectList(any())).thenReturn(List.of(session(1L)));
        ProctorEvent e = event(1L); e.setType("FACE_MISSING");
        when(eventMapper.selectList(any())).thenReturn(List.of(e));
        List<ProctorRiskSummaryVO> r = service.riskSummary(100L);
        assertEquals(1, r.size());
        ProctorRiskSummaryVO m = r.get(0);
        assertEquals(1, m.getEventCount());
        Map<String, Long> tc = m.getTypeCounts();
        assertEquals(1L, tc.get("FACE_MISSING"));
    }

    // ===================== thresholds =====================

    @Test
    @DisplayName("getThresholds 返回默认阈值")
    void thresholds_ok() {
        com.aicourse.vo.ProctorThresholdVO r = service.getThresholds();
        assertEquals(6, r.getHigh());
        assertEquals(3, r.getMedium());
        assertEquals(1, r.getWeightLow());
        assertEquals(2, r.getWeightMedium());
        assertEquals(3, r.getWeightHigh());
    }

    @Test
    @DisplayName("updateThresholds 空参提前返回不写配置")
    void updateThresholds_empty() {
        service.updateThresholds(null);
        verify(systemConfigService, never()).save(any());
        service.updateThresholds(new HashMap<>());
        verify(systemConfigService, never()).save(any());
    }

    @Test
    @DisplayName("updateThresholds 正常写入五项配置")
    void updateThresholds_ok() {
        Map<String, Integer> t = new HashMap<>();
        t.put("high", 8); t.put("medium", 4); t.put("weightLow", 1);
        t.put("weightMedium", 2); t.put("weightHigh", 5);
        service.updateThresholds(t);
        verify(systemConfigService, times(5)).save(any(SystemConfig.class));
    }

    // ===================== reviewEvent / getEvent =====================

    @Test
    @DisplayName("reviewEvent 正常复核")
    void review_ok() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L));
        ProctorEvent r = service.reviewEvent(1L, ProctorEvent.STATUS_REVIEWED, "ok", 99L);
        assertEquals(ProctorEvent.STATUS_REVIEWED, r.getStatus());
        verify(eventMapper).updateById(any(ProctorEvent.class));
    }

    @Test
    @DisplayName("reviewEvent 事件不存在抛异常")
    void review_null() {
        when(eventMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.reviewEvent(1L, ProctorEvent.STATUS_REVIEWED, "ok", 99L));
    }

    @Test
    @DisplayName("reviewEvent 非法状态抛异常")
    void review_invalid() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L));
        assertThrows(BusinessException.class, () -> service.reviewEvent(1L, "BAD", "ok", 99L));
    }

    @Test
    @DisplayName("reviewEvent 豁免触发会话风险重算")
    void review_exempted() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L));
        when(sessionMapper.selectById(1L)).thenReturn(session(1L));
        when(eventMapper.selectList(any())).thenReturn(new ArrayList<>()); // 无未豁免事件 -> 风险归零
        ProctorEvent r = service.reviewEvent(1L, ProctorEvent.STATUS_EXEMPTED, "误报", 99L);
        assertEquals(ProctorEvent.STATUS_EXEMPTED, r.getStatus());
        verify(sessionMapper).updateById(any(ProctorSession.class));
    }

    @Test
    @DisplayName("getEvent 按ID查询")
    void getEvent_ok() {
        when(eventMapper.selectById(1L)).thenReturn(event(1L));
        assertNotNull(service.getEvent(1L));
    }
}
