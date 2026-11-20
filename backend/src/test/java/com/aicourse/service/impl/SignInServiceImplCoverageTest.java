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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 签到服务剩余分支：凭证类型矩阵、时间窗、已有记录更新、权限矩阵与手动标记更新。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignInServiceImplCoverageTest {

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
        List<GrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of();
        doReturn(authorities).when(a).getAuthorities();
        return a;
    }

    private Authentication authWithCustomAuthority() {
        Authentication a = mock(Authentication.class);
        GrantedAuthority custom = () -> "ROLE_ADMIN";
        doReturn(List.of(custom)).when(a).getAuthorities();
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

    private SignInRecord record(String code, String gesture, Double lat, Double lon) {
        SignInRecord r = new SignInRecord();
        r.setSubmittedCode(code);
        r.setSubmittedGesture(gesture);
        r.setLatitude(lat);
        r.setLongitude(lon);
        return r;
    }

    @Test
    @DisplayName("创建：非空类型保留，管理员可创建非本人课程")
    void createNonBlankTypeAsAdmin() {
        when(courseMapper.selectById(1L)).thenReturn(course(2L));
        when(signInMapper.insert(any(SignIn.class))).thenReturn(1);
        SignIn in = new SignIn();
        in.setCourseId(1L); in.setType("CODE");

        SignIn result = service.createSignIn(in, 3L, auth(true));

        assertEquals("CODE", result.getType());
        verify(signInMapper).insert(any(SignIn.class));
    }

    @Test
    @DisplayName("签到：活动不存在与窗口超时均拒绝")
    void signMissingAndExpired() {
        when(signInMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 2L, "ip", "ua"));

        SignIn expired = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_NORMAL);
        expired.setStartedAt(LocalDateTime.now().minusMinutes(10));
        expired.setDurationMinutes(5);
        when(signInMapper.selectById(1L)).thenReturn(expired);
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("签到码：QR 类型有效码成功，缺码/空提交/错误码拒绝")
    void codeCredentialBranches() {
        SignIn qr = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_QR);
        when(signInMapper.selectById(1L)).thenReturn(qr);
        when(signInRecordMapper.selectOne(any())).thenReturn(null);
        when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);
        assertDoesNotThrow(() -> service.sign(1L, record(" abc123 ", null, null, null), 2L, "ip", "ua"));

        SignIn noCode = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_CODE);
        noCode.setSignCode(null);
        when(signInMapper.selectById(1L)).thenReturn(noCode);
        assertThrows(BusinessException.class, () -> service.sign(1L, record("x", null, null, null), 2L, "ip", "ua"));

        SignIn code = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_CODE);
        when(signInMapper.selectById(1L)).thenReturn(code);
        assertThrows(BusinessException.class, () -> service.sign(1L, record(" ", null, null, null), 2L, "ip", "ua"));
        assertThrows(BusinessException.class, () -> service.sign(1L, record("WRONG", null, null, null), 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("手势：有效手势成功，缺配置/空提交/错误手势拒绝")
    void gestureCredentialBranches() {
        SignIn gesture = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_GESTURE);
        gesture.setGesture("CIRCLE");
        when(signInMapper.selectById(1L)).thenReturn(gesture);
        when(signInRecordMapper.selectOne(any())).thenReturn(null);
        when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);
        assertDoesNotThrow(() -> service.sign(1L, record(null, " circle ", null, null), 2L, "ip", "ua"));

        SignIn noGesture = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_GESTURE);
        noGesture.setGesture(null);
        when(signInMapper.selectById(1L)).thenReturn(noGesture);
        assertThrows(BusinessException.class, () -> service.sign(1L, record(null, "X", null, null), 2L, "ip", "ua"));

        SignIn wrongGesture = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_GESTURE);
        wrongGesture.setGesture("CIRCLE");
        when(signInMapper.selectById(1L)).thenReturn(wrongGesture);
        assertThrows(BusinessException.class, () -> service.sign(1L, record(null, " ", null, null), 2L, "ip", "ua"));
        assertThrows(BusinessException.class, () -> service.sign(1L, record(null, "TRIANGLE", null, null), 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("定位：经纬度齐全成功，缺失拒绝")
    void locationCredentialBranches() {
        SignIn location = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_LOCATION);
        when(signInMapper.selectById(1L)).thenReturn(location);
        when(signInRecordMapper.selectOne(any())).thenReturn(null);
        when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);
        assertDoesNotThrow(() -> service.sign(1L, record(null, null, 31.2, 121.5), 2L, "ip", "ua"));

        assertThrows(BusinessException.class, () -> service.sign(1L, record(null, null, null, null), 2L, "ip", "ua"));
    }

    @Test
    @DisplayName("已有记录：重复签到走更新，不重复插入")
    void signExistingRecordUpdates() {
        SignIn s = sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_NORMAL);
        when(signInMapper.selectById(1L)).thenReturn(s);
        SignInRecord existing = record(null, null, null, null);
        existing.setSignInId(1L); existing.setStudentId(2L);
        when(signInRecordMapper.selectOne(any())).thenReturn(existing);
        when(signInRecordMapper.updateById(any(SignInRecord.class))).thenReturn(1);

        SignInRecord result = service.sign(1L, record("abc", "g", 1.0, 2.0), 2L, "ip", "ua");

        assertEquals(SignInRecord.STATUS_SIGNED, result.getStatus());
        assertEquals("ip", result.getIp());
        assertEquals("ua", result.getUserAgent());
        verify(signInRecordMapper).updateById(any(SignInRecord.class));
        verify(signInRecordMapper, never()).insert(any(SignInRecord.class));
    }

    @Test
    @DisplayName("关闭：非进行中拒绝")
    void closeNotOngoing() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_PENDING, SignIn.TYPE_NORMAL));
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        assertThrows(BusinessException.class, () -> service.close(1L, 1L, auth(false)));
    }

    @Test
    @DisplayName("权限：本人教师可操作，非本人 403，管理员放行")
    void ownerMatrix() {
        when(courseMapper.selectById(1L)).thenReturn(course(2L));
        when(signInMapper.insert(any(SignIn.class))).thenReturn(1);
        SignIn in = new SignIn(); in.setCourseId(1L);
        assertDoesNotThrow(() -> service.createSignIn(in, 2L, auth(false)));

        SignIn in2 = new SignIn(); in2.setCourseId(1L);
        assertThrows(BusinessException.class, () -> service.createSignIn(in2, 3L, auth(false)));

        SignIn in3 = new SignIn(); in3.setCourseId(1L);
        assertDoesNotThrow(() -> service.createSignIn(in3, 3L, auth(true)));
        SignIn in4 = new SignIn(); in4.setCourseId(1L);
        assertThrows(BusinessException.class, () -> service.createSignIn(in4, 3L, authWithCustomAuthority()));
    }

    @Test
    @DisplayName("手动标记：已有记录走更新")
    void manualMarkUpdatesExisting() {
        when(signInMapper.selectById(1L)).thenReturn(sign(1L, SignIn.STATUS_ONGOING, SignIn.TYPE_NORMAL));
        when(courseMapper.selectById(1L)).thenReturn(course(1L));
        SignInRecord existing = new SignInRecord();
        existing.setSignInId(1L); existing.setStudentId(5L);
        when(signInRecordMapper.selectOne(any())).thenReturn(existing);
        when(signInRecordMapper.updateById(any(SignInRecord.class))).thenReturn(1);

        SignInRecord result = service.manualMark(1L, 5L, "ABSENT", 1L, auth(false));

        assertEquals("ABSENT", result.getStatus());
        verify(signInRecordMapper).updateById(any(SignInRecord.class));
        verify(signInRecordMapper, never()).insert(any(SignInRecord.class));
    }
}
