package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.SignInMapper;
import com.aicourse.mapper.SignInRecordMapper;
import com.aicourse.service.SignInService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 课堂签到服务实现
 */
@Service
public class SignInServiceImpl extends ServiceImpl<SignInMapper, SignIn> implements SignInService {

    private final CourseMapper courseMapper;
    private final SignInRecordMapper signInRecordMapper;

    public SignInServiceImpl(CourseMapper courseMapper, SignInRecordMapper signInRecordMapper) {
        this.courseMapper = courseMapper;
        this.signInRecordMapper = signInRecordMapper;
    }

    @Override
    public SignIn createSignIn(SignIn signIn, Long userId, Authentication auth) {
        assertCourseOwner(signIn.getCourseId(), userId, auth);
        if (signIn.getType() == null || signIn.getType().isBlank()) {
            signIn.setType(SignIn.TYPE_NORMAL);
        }
        signIn.setCreatorId(userId);
        signIn.setStatus(SignIn.STATUS_PENDING);
        signIn.setSignCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        this.save(signIn);
        return signIn;
    }

    @Override
    public SignIn publish(Long id, Long userId, Authentication auth) {
        SignIn signIn = getDetail(id);
        assertCourseOwner(signIn.getCourseId(), userId, auth);
        if (!SignIn.STATUS_PENDING.equals(signIn.getStatus())) {
            throw new BusinessException("仅待开始的签到可以发布");
        }
        signIn.setStatus(SignIn.STATUS_ONGOING);
        signIn.setStartedAt(LocalDateTime.now());
        this.updateById(signIn);
        return signIn;
    }

    @Override
    public SignIn close(Long id, Long userId, Authentication auth) {
        SignIn signIn = requireOngoingCheck(id, userId, auth, true);
        signIn.setStatus(SignIn.STATUS_CLOSED);
        signIn.setClosedAt(LocalDateTime.now());
        this.updateById(signIn);
        return signIn;
    }

    @Override
    public SignIn getDetail(Long id) {
        SignIn signIn = this.getById(id);
        if (signIn == null) {
            throw new BusinessException("签到活动不存在");
        }
        return signIn;
    }

    @Override
    public List<SignIn> listByCourse(Long courseId, Long userId, Authentication auth) {
        assertCourseOwner(courseId, userId, auth);
        LambdaQueryWrapper<SignIn> qw = new LambdaQueryWrapper<SignIn>()
                .eq(SignIn::getCourseId, courseId)
                .orderByDesc(SignIn::getCreatedAt);
        return this.list(qw);
    }

    @Override
    public SignInRecord sign(Long id, SignInRecord record, Long userId, String ip, String ua) {
        SignIn signIn = this.getById(id);
        if (signIn == null || !SignIn.STATUS_ONGOING.equals(signIn.getStatus())) {
            throw new BusinessException("签到未在进行中");
        }
        // 签到窗口超时校验（进阶：durationMinutes 非空时按 开始时间+时长 限制）
        if (signIn.getStartedAt() != null && signIn.getDurationMinutes() != null
                && LocalDateTime.now().isAfter(signIn.getStartedAt().plusMinutes(signIn.getDurationMinutes()))) {
            throw new BusinessException("签到已超出有效时间窗口");
        }

        // 修复 P1-2：按签到类型校验凭证，杜绝"任意 ONGOING 签到直接成功"
        validateSignInCredential(signIn, record);

        // 已签到则更新，避免重复
        LambdaQueryWrapper<SignInRecord> qw = new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getSignInId, id)
                .eq(SignInRecord::getStudentId, userId);
        SignInRecord exist = signInRecordMapper.selectOne(qw);
        if (exist != null) {
            exist.setStatus(SignInRecord.STATUS_SIGNED);
            exist.setSignTime(LocalDateTime.now());
            exist.setIp(ip);
            exist.setUserAgent(ua);
            exist.setPhotoUrl(record.getPhotoUrl());
            exist.setLatitude(record.getLatitude());
            exist.setLongitude(record.getLongitude());
            exist.setSubmittedCode(record.getSubmittedCode());
            exist.setSubmittedGesture(record.getSubmittedGesture());
            signInRecordMapper.updateById(exist);
            return exist;
        }
        record.setSignInId(id);
        record.setStudentId(userId);
        record.setStatus(SignInRecord.STATUS_SIGNED);
        record.setSignTime(LocalDateTime.now());
        record.setIp(ip);
        record.setUserAgent(ua);
        signInRecordMapper.insert(record);
        return record;
    }

    @Override
    public List<SignInRecord> listRecords(Long id, Long userId, Authentication auth) {
        SignIn signIn = getDetail(id);
        assertCourseOwner(signIn.getCourseId(), userId, auth);
        LambdaQueryWrapper<SignInRecord> qw = new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getSignInId, id)
                .orderByDesc(SignInRecord::getSignTime);
        return signInRecordMapper.selectList(qw);
    }

    @Override
    public SignInRecord manualMark(Long id, Long studentId, String status, Long userId, Authentication auth) {
        SignIn signIn = getDetail(id);
        assertCourseOwner(signIn.getCourseId(), userId, auth);
        LambdaQueryWrapper<SignInRecord> qw = new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getSignInId, id)
                .eq(SignInRecord::getStudentId, studentId);
        SignInRecord record = signInRecordMapper.selectOne(qw);
        if (record == null) {
            record = new SignInRecord();
            record.setSignInId(id);
            record.setStudentId(studentId);
            record.setSignTime(LocalDateTime.now());
            signInRecordMapper.insert(record);
        }
        record.setStatus(status);
        signInRecordMapper.updateById(record);
        return record;
    }

    @Override
    public SignInRecord myRecord(Long id, Long userId) {
        LambdaQueryWrapper<SignInRecord> qw = new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getSignInId, id)
                .eq(SignInRecord::getStudentId, userId);
        return signInRecordMapper.selectOne(qw);
    }

    // ===================== 私有辅助 =====================

    /**
     * 修复 P1-2：按签到类型校验学生提交的凭证
     * - CODE / QR：提交的签到码必须与教师下发的 signCode 一致（忽略大小写）
     * - GESTURE：提交的手势必须与教师设置的 gesture 一致
     * - LOCATION：必须携带经纬度（地理围栏需教师配置目标坐标，此处先保证"开启定位"）
     * - NORMAL：无额外凭证
     */
    private void validateSignInCredential(SignIn signIn, SignInRecord record) {
        String type = signIn.getType();
        if (SignIn.TYPE_CODE.equals(type) || SignIn.TYPE_QR.equals(type)) {
            if (signIn.getSignCode() == null || signIn.getSignCode().isBlank()) {
                throw new BusinessException("该签到未配置签到码");
            }
            String submitted = record.getSubmittedCode();
            if (submitted == null || submitted.isBlank()
                    || !submitted.trim().equalsIgnoreCase(signIn.getSignCode().trim())) {
                throw new BusinessException("签到码错误，签到失败");
            }
        } else if (SignIn.TYPE_GESTURE.equals(type)) {
            if (signIn.getGesture() == null || signIn.getGesture().isBlank()) {
                throw new BusinessException("该签到未配置手势");
            }
            String submitted = record.getSubmittedGesture();
            if (submitted == null || submitted.isBlank()
                    || !submitted.trim().equalsIgnoreCase(signIn.getGesture().trim())) {
                throw new BusinessException("手势错误，签到失败");
            }
        } else if (SignIn.TYPE_LOCATION.equals(type)) {
            if (record.getLatitude() == null || record.getLongitude() == null) {
                throw new BusinessException("请开启定位并授权位置后签到");
            }
        }
        // NORMAL 无需校验凭证
    }

    private SignIn requireOngoingCheck(Long id, Long userId, Authentication auth, boolean needOngoing) {
        SignIn signIn = getDetail(id);
        assertCourseOwner(signIn.getCourseId(), userId, auth);
        if (needOngoing && !SignIn.STATUS_ONGOING.equals(signIn.getStatus())) {
            throw new BusinessException("签到未在进行中");
        }
        return signIn;
    }

    private void assertCourseOwner(Long courseId, Long userId, Authentication auth) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a instanceof SimpleGrantedAuthority
                        && "ROLE_ADMIN".equals(((SimpleGrantedAuthority) a).getAuthority()));
        if (!isAdmin && !userId.equals(course.getTeacherId())) {
            throw new BusinessException("无权操作该课程");
        }
    }
}
