package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.SignInMapper;
import com.aicourse.mapper.SignInRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SignInServiceImpl 指令覆盖率强化测试。
 * 覆盖创建/发布/关闭/签到/记录查询/手动标记/权限校验/凭证校验全部分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignInServiceImplBoostTest {

    @Mock
    private CourseMapper courseMapper;
    @Mock
    private SignInRecordMapper signInRecordMapper;
    @Mock
    private SignInMapper signInMapper;

    private SignInServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SignInServiceImpl(courseMapper, signInRecordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", signInMapper);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), SignIn.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), SignInRecord.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), Course.class);
    }

    private Authentication adminAuth() {
        Authentication auth = mock(Authentication.class);
        doReturn(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(auth).getAuthorities();
        return auth;
    }

    private Authentication ownerAuth() {
        Authentication auth = mock(Authentication.class);
        doReturn(Collections.emptyList()).when(auth).getAuthorities();
        return auth;
    }

    private Course courseOf(Long teacherId) {
        Course c = new Course();
        c.setId(1L);
        c.setTeacherId(teacherId);
        return c;
    }

    // ==================== createSignIn ====================

    @Test
    @DisplayName("createSignIn: 类型为空/空白 -> NORMAL；管理员可创建")
    void createSignIn_admin() {
        when(courseMapper.selectById(1L)).thenReturn(courseOf(99L));
        SignIn s = new SignIn();
        s.setCourseId(1L);
        SignIn r = service.createSignIn(s, 7L, adminAuth());
        assertEquals(SignIn.TYPE_NORMAL, r.getType());
        assertEquals(SignIn.STATUS_PENDING, r.getStatus());
        assertNotNull(r.getSignCode());
        assertEquals(7L, r.getCreatorId());
        verify(signInMapper).insert(any(SignIn.class));
    }

    @Test
    @DisplayName("createSignIn: 拥有者创建，保留指定类型")
    void createSignIn_owner() {
        when(courseMapper.selectById(1L)).thenReturn(courseOf(7L));
        SignIn s = new SignIn();
        s.setCourseId(1L);
        s.setType(SignIn.TYPE_CODE);
        SignIn r = service.createSignIn(s, 7L, ownerAuth());
        assertEquals(SignIn.TYPE_CODE, r.getType());
    }

    @Test
    @DisplayName("createSignIn: 课程不存在/无权抛异常")
    void createSignIn_forbidden() {
        when(courseMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.createSignIn(new SignIn(), 7L, adminAuth()));

        when(courseMapper.selectById(1L)).thenReturn(courseOf(99L));
        assertThrows(BusinessException.class, () -> service.createSignIn(new SignIn(), 7L, ownerAuth()));
    }

    // ==================== publish ====================

    @Test
    @DisplayName("publish: 活动不存在/非待开始抛异常，否则发布")
    void publish() {
        when(signInMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.publish(1L, 7L, adminAuth()));

        SignIn s = new SignIn();
        s.setId(1L);
        s.setCourseId(1L);
        s.setStatus(SignIn.STATUS_ONGOING);
        when(signInMapper.selectById(1L)).thenReturn(s);
        when(courseMapper.selectById(1L)).thenReturn(courseOf(99L));
        assertThrows(BusinessException.class, () -> service.publish(1L, 7L, adminAuth()));

        SignIn pending = new SignIn();
        pending.setId(1L);
        pending.setCourseId(1L);
        pending.setStatus(SignIn.STATUS_PENDING);
        when(signInMapper.selectById(1L)).thenReturn(pending);
        SignIn r = service.publish(1L, 7L, adminAuth());
        assertEquals(SignIn.STATUS_ONGOING, r.getStatus());
        assertNotNull(r.getStartedAt());
        verify(signInMapper).updateById(any(SignIn.class));
    }

    // ==================== close ====================

    @Test
    @DisplayName("close: 非进行中抛异常，否则关闭")
    void close() {
        SignIn s = new SignIn();
        s.setId(1L);
        s.setCourseId(1L);
        s.setStatus(SignIn.STATUS_PENDING);
        when(signInMapper.selectById(1L)).thenReturn(s);
        when(courseMapper.selectById(1L)).thenReturn(courseOf(99L));
        assertThrows(BusinessException.class, () -> service.close(1L, 7L, adminAuth()));

        SignIn ongoing = new SignIn();
        ongoing.setId(1L);
        ongoing.setCourseId(1L);
        ongoing.setStatus(SignIn.STATUS_ONGOING);
        when(signInMapper.selectById(1L)).thenReturn(ongoing);
        SignIn r = service.close(1L, 7L, adminAuth());
        assertEquals(SignIn.STATUS_CLOSED, r.getStatus());
        assertNotNull(r.getClosedAt());
    }

    // ==================== getDetail / listByCourse ====================

    @Test
    @DisplayName("getDetail: 不存在抛异常")
    void getDetail_notFound() {
        when(signInMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getDetail(1L));
    }

    @Test
    @DisplayName("listByCourse: 返回课程签到列表")
    void listByCourse() {
        when(courseMapper.selectById(1L)).thenReturn(courseOf(7L));
        when(signInMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(new SignIn()));
        List<SignIn> list = service.listByCourse(1L, 7L, ownerAuth());
        assertEquals(1, list.size());
    }

    // ==================== sign ====================

    @Test
    @DisplayName("sign: 活动不存在/非进行中抛异常")
    void sign_notOngoing() {
        when(signInMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 7L, "ip", "ua"));

        SignIn s = new SignIn();
        s.setId(1L);
        s.setStatus(SignIn.STATUS_CLOSED);
        when(signInMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 7L, "ip", "ua"));
    }

    @Test
    @DisplayName("sign: 超出有效时间窗口抛异常")
    void sign_windowExpired() {
        SignIn s = new SignIn();
        s.setId(1L);
        s.setStatus(SignIn.STATUS_ONGOING);
        s.setStartedAt(LocalDateTime.now().minusMinutes(10));
        s.setDurationMinutes(5);
        when(signInMapper.selectById(1L)).thenReturn(s);
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 7L, "ip", "ua"));
    }

    @Test
    @DisplayName("sign: CODE 类型 —— 未配置码/码错误/正确三分支")
    void sign_codeCredential() {
        // 未配置签到码
        SignIn noCode = new SignIn();
        noCode.setId(1L);
        noCode.setStatus(SignIn.STATUS_ONGOING);
        noCode.setType(SignIn.TYPE_CODE);
        noCode.setSignCode(null);
        when(signInMapper.selectById(1L)).thenReturn(noCode);
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 7L, "ip", "ua"));

        // 码错误
        SignIn wrong = new SignIn();
        wrong.setId(1L);
        wrong.setStatus(SignIn.STATUS_ONGOING);
        wrong.setType(SignIn.TYPE_CODE);
        wrong.setSignCode("ABC123");
        when(signInMapper.selectById(1L)).thenReturn(wrong);
        SignInRecord rec = new SignInRecord();
        rec.setSubmittedCode("ZZZ");
        assertThrows(BusinessException.class, () -> service.sign(1L, rec, 7L, "ip", "ua"));

        // 码正确（大小写不敏感）
        SignIn ok = new SignIn();
        ok.setId(1L);
        ok.setStatus(SignIn.STATUS_ONGOING);
        ok.setType(SignIn.TYPE_CODE);
        ok.setSignCode("ABC123");
        when(signInMapper.selectById(1L)).thenReturn(ok);
        SignInRecord rec2 = new SignInRecord();
        rec2.setSubmittedCode("abc123");
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        SignInRecord r = service.sign(1L, rec2, 7L, "ip", "ua");
        assertEquals(SignInRecord.STATUS_SIGNED, r.getStatus());
        verify(signInRecordMapper).insert(any(SignInRecord.class));
    }

    @Test
    @DisplayName("sign: GESTURE 类型 —— 未配置/错误/正确")
    void sign_gestureCredential() {
        SignIn noG = new SignIn();
        noG.setId(1L);
        noG.setStatus(SignIn.STATUS_ONGOING);
        noG.setType(SignIn.TYPE_GESTURE);
        noG.setGesture(null);
        when(signInMapper.selectById(1L)).thenReturn(noG);
        assertThrows(BusinessException.class, () -> service.sign(1L, new SignInRecord(), 7L, "ip", "ua"));

        SignIn wrong = new SignIn();
        wrong.setId(1L);
        wrong.setStatus(SignIn.STATUS_ONGOING);
        wrong.setType(SignIn.TYPE_GESTURE);
        wrong.setGesture("L");
        when(signInMapper.selectById(1L)).thenReturn(wrong);
        SignInRecord rec = new SignInRecord();
        rec.setSubmittedGesture("R");
        assertThrows(BusinessException.class, () -> service.sign(1L, rec, 7L, "ip", "ua"));

        SignIn ok = new SignIn();
        ok.setId(1L);
        ok.setStatus(SignIn.STATUS_ONGOING);
        ok.setType(SignIn.TYPE_GESTURE);
        ok.setGesture("L");
        when(signInMapper.selectById(1L)).thenReturn(ok);
        SignInRecord rec2 = new SignInRecord();
        rec2.setSubmittedGesture("l");
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        service.sign(1L, rec2, 7L, "ip", "ua");
    }

    @Test
    @DisplayName("sign: LOCATION 类型 —— 无经纬度/有经纬度")
    void sign_locationCredential() {
        SignIn noLoc = new SignIn();
        noLoc.setId(1L);
        noLoc.setStatus(SignIn.STATUS_ONGOING);
        noLoc.setType(SignIn.TYPE_LOCATION);
        when(signInMapper.selectById(1L)).thenReturn(noLoc);
        SignInRecord rec = new SignInRecord();
        assertThrows(BusinessException.class, () -> service.sign(1L, rec, 7L, "ip", "ua"));

        SignIn ok = new SignIn();
        ok.setId(1L);
        ok.setStatus(SignIn.STATUS_ONGOING);
        ok.setType(SignIn.TYPE_LOCATION);
        when(signInMapper.selectById(1L)).thenReturn(ok);
        SignInRecord rec2 = new SignInRecord();
        rec2.setLatitude(12.3);
        rec2.setLongitude(45.6);
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        service.sign(1L, rec2, 7L, "ip", "ua");
    }

    @Test
    @DisplayName("sign: NORMAL 类型无需凭证；已存在记录则更新")
    void sign_normal_existing() {
        SignIn ok = new SignIn();
        ok.setId(1L);
        ok.setStatus(SignIn.STATUS_ONGOING);
        ok.setType(SignIn.TYPE_NORMAL);
        when(signInMapper.selectById(1L)).thenReturn(ok);

        SignInRecord exist = new SignInRecord();
        exist.setId(9L);
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exist);

        SignInRecord rec = new SignInRecord();
        rec.setPhotoUrl("u");
        SignInRecord r = service.sign(1L, rec, 7L, "ip", "ua");
        assertEquals(SignInRecord.STATUS_SIGNED, r.getStatus());
        verify(signInRecordMapper).updateById(any(SignInRecord.class));
    }

    // ==================== listRecords / manualMark / myRecord / listMyRecords ====================

    @Test
    @DisplayName("listRecords: 返回签到记录列表")
    void listRecords() {
        SignIn s = new SignIn();
        s.setId(1L);
        s.setCourseId(1L);
        when(signInMapper.selectById(1L)).thenReturn(s);
        when(courseMapper.selectById(1L)).thenReturn(courseOf(99L));
        when(signInRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(new SignInRecord()));
        assertEquals(1, service.listRecords(1L, 7L, adminAuth()).size());
    }

    @Test
    @DisplayName("manualMark: 记录存在则更新，不存在则新建")
    void manualMark() {
        SignIn s = new SignIn();
        s.setId(1L);
        s.setCourseId(1L);
        when(signInMapper.selectById(1L)).thenReturn(s);
        when(courseMapper.selectById(1L)).thenReturn(courseOf(99L));

        // 不存在 -> 新建
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        SignInRecord created = service.manualMark(1L, 7L, SignInRecord.STATUS_SIGNED, 7L, adminAuth());
        assertNull(created.getId()); // 新建对象无 id（mock insert 不回填主键）
        verify(signInRecordMapper).insert(any(SignInRecord.class));

        // 存在 -> 更新
        SignInRecord existing = new SignInRecord();
        existing.setId(9L);
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        SignInRecord updated = service.manualMark(1L, 7L, SignInRecord.STATUS_SIGNED, 7L, adminAuth());
        assertEquals(9L, updated.getId());
        verify(signInRecordMapper).updateById(any(SignInRecord.class));
    }

    @Test
    @DisplayName("myRecord: 返回我的记录")
    void myRecord() {
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new SignInRecord());
        assertNotNull(service.myRecord(1L, 7L));
    }

    @Test
    @DisplayName("listMyRecords: userId 为空返回空，否则返回列表")
    void listMyRecords() {
        assertTrue(service.listMyRecords(null).isEmpty());
        when(signInRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(new SignInRecord()));
        assertEquals(1, service.listMyRecords(7L).size());
    }
}
