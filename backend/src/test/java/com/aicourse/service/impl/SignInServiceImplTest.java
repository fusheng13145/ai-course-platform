package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.SignInMapper;
import com.aicourse.mapper.SignInRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 课堂签到服务单元测试（B 第一批）。
 * 依赖 SignInMapper(baseMapper) + CourseMapper + SignInRecordMapper + Authentication。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignInServiceImplTest {

    @Mock private SignInMapper signInMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private SignInRecordMapper signInRecordMapper;
    @InjectMocks private SignInServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", signInMapper);
    }

    private Authentication auth(boolean admin) {
        Authentication a = mock(Authentication.class);
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (admin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        doReturn(authorities).when(a).getAuthorities();
        return a;
    }

    private Course course(Long teacherId) {
        Course c = new Course();
        c.setId(1L);
        c.setTeacherId(teacherId);
        return c;
    }

    private SignIn sign(Long id, String status, String type) {
        SignIn s = new SignIn();
        s.setId(id);
        s.setStatus(status);
        s.setType(type);
        s.setCourseId(1L);
        s.setSignCode("ABC123");
        return s;
    }

    @Test
    @DisplayName("创建签到-成功")
    void createSignIn_ok() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(signInMapper.insert(any(SignIn.class))).thenReturn(1);
        SignIn in = new SignIn();
        in.setCourseId(1L);
        SignIn r = service.createSignIn(in, 1L, auth(false));
        assertEquals(SignIn.STATUS_PENDING, r.getStatus());
        assertNotNull(r.getSignCode());
    }

    @Test
    @DisplayName("创建签到-类型空白默认 NORMAL")
    void createSignIn_blankType() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(signInMapper.insert(any(SignIn.class))).thenReturn(1);
        SignIn in = new SignIn();
        in.setCourseId(1L);
        in.setType("  ");
        SignIn r = service.createSignIn(in, 1L, auth(false));
        assertEquals(SignIn.TYPE_NORMAL, r.getType());
    }

    @Test
    @DisplayName("创建签到-课程不存在抛异常")
    void createSignIn_courseNotFound() {
        when(courseMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createSignIn(new SignIn(), 1L, auth(false)));
    }

    @Test
    @DisplayName("发布签到-成功")
    void publish_ok() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_PENDING, "NORMAL"));
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(signInMapper.updateById(any(SignIn.class))).thenReturn(1);
        SignIn r = service.publish(1L, 1L, auth(false));
        assertEquals(SignIn.STATUS_ONGOING, r.getStatus());
    }

    @Test
    @DisplayName("发布签到-非待开始抛异常")
    void publish_notPending() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_ONGOING, "NORMAL"));
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        assertThrows(BusinessException.class, () -> service.publish(1L, 1L, auth(false)));
    }

    @Test
    @DisplayName("关闭签到-成功")
    void close_ok() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_ONGOING, "NORMAL"));
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(signInMapper.updateById(any(SignIn.class))).thenReturn(1);
        SignIn r = service.close(1L, 1L, auth(false));
        assertEquals(SignIn.STATUS_CLOSED, r.getStatus());
    }

    @Test
    @DisplayName("查询签到-不存在抛异常")
    void getDetail_notFound() {
        when(signInMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getDetail(9L));
    }

    @Test
    @DisplayName("按课程列出签到-成功")
    void listByCourse_ok() {
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(signInMapper.selectList(any())).thenReturn(List.of(sign(1L, "ONGOING", "NORMAL")));
        List<SignIn> r = service.listByCourse(1L, 1L, auth(false));
        assertEquals(1, r.size());
    }

    @Test
    @DisplayName("签到-普通类型成功")
    void sign_ok_normal() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_ONGOING, "NORMAL"));
        when(signInRecordMapper.selectOne(any())).thenReturn(null);
        when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);
        SignInRecord rec = new SignInRecord();
        rec.setSignInId(1L);
        rec.setStudentId(2L);
        SignInRecord r = service.sign(1L, rec, 2L, "ip", "ua");
        assertEquals(SignInRecord.STATUS_SIGNED, r.getStatus());
    }

    @Test
    @DisplayName("签到-签到码错误抛异常")
    void sign_codeMismatch() {
        SignIn s = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_CODE);
        s.setSignCode("ABC123");
        when(signInMapper.selectById(1L)).thenReturn(s);
        SignInRecord rec = new SignInRecord();
        rec.setSubmittedCode("WRONG");
        assertThrows(BusinessException.class, () -> service.sign(1L, rec, 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("签到-非进行中抛异常")
    void sign_notOngoing() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_CLOSED, "NORMAL"));
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("列出签到记录-成功")
    void listRecords_ok() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_ONGOING, "NORMAL"));
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(signInRecordMapper.selectList(any())).thenReturn(List.of(new SignInRecord()));
        assertEquals(1, service.listRecords(1L, 1L, auth(false)).size());
    }

    @Test
    @DisplayName("手动标记-新建记录")
    void manualMark_insert() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_ONGOING, "NORMAL"));
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        when(signInRecordMapper.selectOne(any())).thenReturn(null);
        when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);
        SignInRecord r = service.manualMark(1L, 5L, "SIGNED", 1L, auth(false));
        assertEquals("SIGNED", r.getStatus());
    }

    @Test
    @DisplayName("我的签到记录-成功")
    void myRecord_ok() {
        when(signInRecordMapper.selectOne(any())).thenReturn(new SignInRecord());
        assertNotNull(service.myRecord(1L, 2L));
    }

    @Test
    @DisplayName("我的签到记录列表- userId 为空返回空")
    void listMyRecords_emptyWhenNull() {
        assertTrue(service.listMyRecords(null).isEmpty());
    }

    @Test
    @DisplayName("我的签到记录列表-成功")
    void listMyRecords_ok() {
        when(signInRecordMapper.selectList(any())).thenReturn(List.of(new SignInRecord()));
        assertEquals(1, service.listMyRecords(2L).size());
    }
}
