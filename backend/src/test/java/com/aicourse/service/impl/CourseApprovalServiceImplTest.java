package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.CourseApprovalRecord;
import com.aicourse.entity.User;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseApprovalRecordMapper;
import com.aicourse.service.CourseService;
import com.aicourse.service.UserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 课程审批编排单测（V13 / WP3）：状态变更与流水的一致性。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseApprovalServiceImplTest {

    @Mock
    private CourseService courseService;
    @Mock
    private CourseApprovalRecordMapper recordMapper;
    @Mock
    private UserService userService;

    private CourseApprovalServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseApprovalRecord.class);
        service = new CourseApprovalServiceImpl(courseService, recordMapper, userService);
    }

    private Course course(Long id, String approvalStatus) {
        Course c = new Course();
        c.setId(id);
        c.setApprovalStatus(approvalStatus);
        return c;
    }

    private CourseApprovalRecord capturedRecord() {
        ArgumentCaptor<CourseApprovalRecord> captor = ArgumentCaptor.forClass(CourseApprovalRecord.class);
        verify(recordMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("applyOwnerCollege 按负责人学院回填，覆盖客户端值")
    void applyOwnerCollege_setsOwnerCollege() {
        User owner = new User();
        owner.setId(7L);
        owner.setCollegeId(10L);
        when(userService.getById(7L)).thenReturn(owner);

        Course course = new Course();
        course.setCollegeId(99L);
        service.applyOwnerCollege(course, 7L);
        assertEquals(10L, course.getCollegeId());
    }

    @Test
    @DisplayName("applyOwnerCollege 负责人无学院时置空")
    void applyOwnerCollege_ownerWithoutCollege_setsNull() {
        when(userService.getById(7L)).thenReturn(null);
        Course course = new Course();
        course.setCollegeId(99L);
        service.applyOwnerCollege(course, 7L);
        assertNull(course.getCollegeId());
    }

    @Test
    @DisplayName("recordSubmission 追加 SUBMIT 流水")
    void recordSubmission_recordsSubmit() {
        service.recordSubmission(1L, 7L);
        CourseApprovalRecord record = capturedRecord();
        assertEquals("SUBMIT", record.getAction());
        assertEquals(1L, record.getCourseId());
        assertEquals(7L, record.getOperatorId());
        assertNull(record.getFromStatus());
        assertEquals("PENDING", record.getToStatus());
    }

    @Test
    @DisplayName("approve 落审批人并追加 APPROVE 流水")
    void approve_recordsApprove() {
        Course before = course(1L, "PENDING");
        when(courseService.getById(1L)).thenReturn(before);
        Course after = course(1L, "APPROVED");
        when(courseService.approveCourse(1L, 2L)).thenReturn(after);

        Course result = service.approve(1L, 2L);
        assertSame(after, result);

        CourseApprovalRecord record = capturedRecord();
        assertEquals("APPROVE", record.getAction());
        assertEquals("PENDING", record.getFromStatus());
        assertEquals("APPROVED", record.getToStatus());
        assertEquals(2L, record.getOperatorId());
    }

    @Test
    @DisplayName("approve 已通过时不重复写流水")
    void approve_alreadyApproved_noDuplicateRecord() {
        Course before = course(1L, "APPROVED");
        before.setStatus("ACTIVE");
        when(courseService.getById(1L)).thenReturn(before);
        when(courseService.approveCourse(1L, 2L)).thenReturn(before);

        service.approve(1L, 2L);
        verify(recordMapper, never()).insert(any(CourseApprovalRecord.class));
    }

    @Test
    @DisplayName("reject 追加 REJECT 流水并带原因")
    void reject_recordsReject() {
        when(courseService.getById(1L)).thenReturn(course(1L, "PENDING"));
        when(courseService.rejectCourse(1L, "资料不全")).thenReturn(course(1L, "REJECTED"));

        service.reject(1L, 2L, "资料不全");

        CourseApprovalRecord record = capturedRecord();
        assertEquals("REJECT", record.getAction());
        assertEquals("PENDING", record.getFromStatus());
        assertEquals("REJECTED", record.getToStatus());
        assertEquals("资料不全", record.getComment());
    }

    @Test
    @DisplayName("resubmit 追加 RESUBMIT 流水，历史保留（只增不改）")
    void resubmit_recordsResubmit() {
        when(courseService.getById(1L)).thenReturn(course(1L, "REJECTED"));
        when(courseService.resubmitCourse(1L)).thenReturn(course(1L, "PENDING"));

        service.resubmit(1L, 7L);

        CourseApprovalRecord record = capturedRecord();
        assertEquals("RESUBMIT", record.getAction());
        assertEquals("REJECTED", record.getFromStatus());
        assertEquals("PENDING", record.getToStatus());
        assertEquals(7L, record.getOperatorId());
    }

    @Test
    @DisplayName("approve 课程不存在抛异常且不写流水")
    void approve_notFound() {
        when(courseService.getById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.approve(1L, 2L));
        verify(recordMapper, never()).insert(any(CourseApprovalRecord.class));
    }

    @Test
    @DisplayName("listByCourse 委托 mapper 查询")
    void listByCourse_delegates() {
        CourseApprovalRecord record = new CourseApprovalRecord();
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        assertEquals(1, service.listByCourse(1L).size());
        verify(recordMapper).selectList(any());
    }
}
