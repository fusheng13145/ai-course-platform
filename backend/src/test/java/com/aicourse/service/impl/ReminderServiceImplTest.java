package com.aicourse.service.impl;

import com.aicourse.dto.ReminderUpdateDTO;
import com.aicourse.entity.Reminder;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.ReminderMapper;
import com.aicourse.service.NotificationService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReminderServiceImpl 单元测试（B 主战场新文件类，缺口 292）。
 * extends ServiceImpl<ReminderMapper, Reminder>：手动构造 + 注入 baseMapper(mock) + NotificationService(mock)；
 * 实体 lambda wrapper 涉及 Reminder → 注册 TableInfo；
 * save/list/getById/updateById 分别锚定；sendNotification 用 doNothing 覆盖 sendDue/update 路径。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReminderServiceImplTest {

    @Mock private NotificationService notificationService;
    @Mock private ReminderMapper reminderMapper;

    private ReminderServiceImpl service;
    private Reminder pending;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Reminder.class);

        service = new ReminderServiceImpl(notificationService);
        ReflectionTestUtils.setField(service, "baseMapper", reminderMapper);

        pending = new Reminder();
        pending.setId(1L);
        pending.setUserId(1L);
        pending.setStatus("PENDING");
        pending.setScheduledAt(LocalDateTime.now().minusMinutes(1));

        when(reminderMapper.selectById(any())).thenReturn(pending);
        when(reminderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pending));
        when(reminderMapper.insert(any(Reminder.class))).thenReturn(1);
        when(reminderMapper.updateById(any(Reminder.class))).thenReturn(1);
        when(notificationService.sendNotification(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(null);
    }

    private Reminder done(String status) {
        Reminder r = new Reminder();
        r.setId(1L);
        r.setUserId(1L);
        r.setStatus(status);
        return r;
    }

    // ===================== createReminder =====================

    @Test
    @DisplayName("createReminder 计算计划发送时间并落库")
    void create_ok() {
        LocalDateTime due = LocalDateTime.now().plusDays(3);
        Reminder r = service.createReminder(1L, "ASSIGNMENT", 9L, 5L, "标题", "内容", due, 1);
        assertEquals("PENDING", r.getStatus());
        assertNotNull(r.getScheduledAt());
        verify(reminderMapper).insert(any(Reminder.class));
    }

    // ===================== sendDueReminders =====================

    @Test
    @DisplayName("sendDueReminders 无到期则空")
    void send_none() {
        when(reminderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.sendDueReminders(LocalDateTime.now()).isEmpty());
        verify(notificationService, never()).sendNotification(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendDueReminders 逐个发送并置 SENT")
    void send_some() {
        Reminder a = done("PENDING");
        Reminder b = done("PENDING");
        when(reminderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));
        List<Reminder> sent = service.sendDueReminders(LocalDateTime.now());
        assertEquals(2, sent.size());
        assertEquals("SENT", sent.get(0).getStatus());
        verify(notificationService, times(2)).sendNotification(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
        verify(reminderMapper, times(2)).updateById(any(Reminder.class));
    }

    // ===================== getPendingReminders =====================

    @Test
    @DisplayName("getPendingReminders 返回待办")
    void pending_ok() {
        assertEquals(1, service.getPendingReminders(1L).size());
    }

    // ===================== cancelReminder =====================

    @Test
    @DisplayName("cancelReminder 正常取消")
    void cancel_ok() {
        service.cancelReminder(1L);
        assertEquals("CANCELLED", pending.getStatus());
        verify(reminderMapper).updateById(any(Reminder.class));
    }

    @Test
    @DisplayName("cancelReminder 不存在抛异常")
    void cancel_null() {
        when(reminderMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.cancelReminder(1L));
    }

    @Test
    @DisplayName("cancelReminder 已发送抛异常")
    void cancel_sent() {
        when(reminderMapper.selectById(any())).thenReturn(done("SENT"));
        assertThrows(BusinessException.class, () -> service.cancelReminder(1L));
    }

    @Test
    @DisplayName("cancelReminder 已取消抛异常")
    void cancel_cancelled() {
        when(reminderMapper.selectById(any())).thenReturn(done("CANCELLED"));
        assertThrows(BusinessException.class, () -> service.cancelReminder(1L));
    }

    // ===================== rescheduleReminder =====================

    @Test
    @DisplayName("rescheduleReminder 正常改期")
    void reschedule_ok() {
        LocalDateTime t = LocalDateTime.now().plusDays(2);
        service.rescheduleReminder(1L, t);
        assertEquals(t, pending.getScheduledAt());
        verify(reminderMapper).updateById(any(Reminder.class));
    }

    @Test
    @DisplayName("rescheduleReminder 不存在抛异常")
    void reschedule_null() {
        when(reminderMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.rescheduleReminder(1L, LocalDateTime.now()));
    }

    @Test
    @DisplayName("rescheduleReminder 已发送抛异常")
    void reschedule_sent() {
        when(reminderMapper.selectById(any())).thenReturn(done("SENT"));
        assertThrows(BusinessException.class, () -> service.rescheduleReminder(1L, LocalDateTime.now()));
    }

    // ===================== updateReminder =====================

    @Test
    @DisplayName("updateReminder 局部更新有效字段")
    void update_ok() {
        ReminderUpdateDTO d = new ReminderUpdateDTO();
        d.setTitle("新标题");
        d.setContent("新内容");
        d.setTargetUserId(2L);
        d.setRemindAt("2099-01-01T10:00:00");
        Reminder r = service.updateReminder(1L, d);
        assertEquals("新标题", r.getTitle());
        assertEquals("新内容", r.getMessage());
        assertEquals(2L, r.getUserId());
        assertNotNull(r.getScheduledAt());
        verify(reminderMapper).updateById(any(Reminder.class));
    }

    @Test
    @DisplayName("updateReminder 不存在抛异常")
    void update_null() {
        when(reminderMapper.selectById(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateReminder(1L, new ReminderUpdateDTO()));
    }

    @Test
    @DisplayName("updateReminder 非法时间抛业务异常")
    void update_badDate() {
        ReminderUpdateDTO d = new ReminderUpdateDTO();
        d.setRemindAt("不是时间");
        assertThrows(BusinessException.class, () -> service.updateReminder(1L, d));
    }

    @Test
    @DisplayName("updateReminder 已发送抛异常")
    void update_sent() {
        when(reminderMapper.selectById(any())).thenReturn(done("SENT"));
        assertThrows(BusinessException.class, () -> service.updateReminder(1L, new ReminderUpdateDTO()));
    }
}
