package com.aicourse.service.impl;

import com.aicourse.entity.Course;
import com.aicourse.entity.Vote;
import com.aicourse.entity.VoteRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.VoteMapper;
import com.aicourse.mapper.VoteRecordMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoteServiceImplTest {

    @Mock
    private VoteMapper voteMapper;          // baseMapper
    @Mock
    private VoteRecordMapper voteRecordMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private Authentication auth;

    private VoteServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Vote.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), VoteRecord.class);
        service = new VoteServiceImpl(courseMapper, voteRecordMapper);
        ReflectionTestUtils.setField(service, "baseMapper", voteMapper);
    }

    private Vote vote(Long id, Long courseId, String mode, String status) {
        Vote v = new Vote();
        v.setId(id);
        v.setCourseId(courseId);
        v.setVoteMode(mode);
        v.setStatus(status);
        return v;
    }

    private void asAdmin() {
        doReturn(Collections.<GrantedAuthority>singletonList(
                new SimpleGrantedAuthority("ROLE_ADMIN"))).when(auth).getAuthorities();
    }

    private void asTeacher(Long teacherId) {
        doReturn(Collections.<GrantedAuthority>emptyList()).when(auth).getAuthorities();
        Course c = new Course();
        c.setId(10L);
        c.setTeacherId(teacherId);
        when(courseMapper.selectById(10L)).thenReturn(c);
    }

    // ===== createVote =====
    @Test
    @DisplayName("createVote 正常：默认模式/状态并落库")
    void create_ok() {
        asTeacher(1L);
        Vote v = vote(null, 10L, null, null);
        v.setOptions("A,B,C");
        when(voteMapper.insert(any(Vote.class))).thenReturn(1);
        Vote r = service.createVote(v, 1L, auth);
        assertEquals(Vote.MODE_SINGLE, r.getVoteMode());
        assertEquals(Vote.STATUS_PENDING, r.getStatus());
        assertEquals(1L, r.getCreatorId());
        verify(voteMapper).insert(any(Vote.class));
    }

    @Test
    @DisplayName("createVote 选项为空抛异常")
    void create_blankOptions() {
        asTeacher(1L);
        Vote v = vote(null, 10L, Vote.MODE_SINGLE, null);
        v.setOptions("   ");
        assertThrows(BusinessException.class, () -> service.createVote(v, 1L, auth));
    }

    @Test
    @DisplayName("createVote 非课程属主抛异常")
    void create_notOwner() {
        doReturn(Collections.<GrantedAuthority>emptyList()).when(auth).getAuthorities();
        Course c = new Course();
        c.setId(10L);
        c.setTeacherId(2L);
        when(courseMapper.selectById(10L)).thenReturn(c);
        Vote v = vote(null, 10L, Vote.MODE_SINGLE, null);
        v.setOptions("A,B");
        assertThrows(BusinessException.class, () -> service.createVote(v, 1L, auth));
    }

    // ===== getDetail =====
    @Test
    @DisplayName("getDetail 不存在抛异常")
    void detail_notFound() {
        when(voteMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getDetail(9L));
    }

    @Test
    @DisplayName("getDetail 正常返回")
    void detail_ok() {
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_PENDING));
        assertNotNull(service.getDetail(1L));
    }

    // ===== close =====
    @Test
    @DisplayName("close 正常")
    void close_ok() {
        asTeacher(1L);
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_ONGOING));
        when(voteMapper.updateById(any(Vote.class))).thenReturn(1);
        Vote r = service.close(1L, 1L, auth);
        assertEquals(Vote.STATUS_CLOSED, r.getStatus());
        assertNotNull(r.getClosedAt());
    }

    @Test
    @DisplayName("close 已结束抛异常")
    void close_already() {
        asTeacher(1L);
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_CLOSED));
        assertThrows(BusinessException.class, () -> service.close(1L, 1L, auth));
    }

    // ===== publish =====
    @Test
    @DisplayName("publish 正常")
    void publish_ok() {
        asTeacher(1L);
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_PENDING));
        when(voteMapper.updateById(any(Vote.class))).thenReturn(1);
        Vote r = service.publish(1L, 1L, auth);
        assertEquals(Vote.STATUS_ONGOING, r.getStatus());
        assertNotNull(r.getStartedAt());
    }

    @Test
    @DisplayName("publish 已在进行中抛异常")
    void publish_alreadyOngoing() {
        asTeacher(1L);
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_ONGOING));
        assertThrows(BusinessException.class, () -> service.publish(1L, 1L, auth));
    }

    @Test
    @DisplayName("publish 已结束抛异常")
    void publish_closed() {
        asTeacher(1L);
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_CLOSED));
        assertThrows(BusinessException.class, () -> service.publish(1L, 1L, auth));
    }

    // ===== listByCourse =====
    @Test
    @DisplayName("listByCourse 正常")
    void list_ok() {
        asTeacher(1L);
        when(voteMapper.selectList(any())).thenReturn(
                List.of(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_PENDING)));
        List<Vote> r = service.listByCourse(10L, 1L, auth);
        assertEquals(1, r.size());
    }

    // ===== vote =====
    @Test
    @DisplayName("vote 首次投票插入")
    void vote_first() {
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_ONGOING));
        when(voteRecordMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(voteRecordMapper.insert(any(VoteRecord.class))).thenReturn(1);
        VoteRecord rec = new VoteRecord();
        rec.setChoice("A");
        VoteRecord r = service.vote(1L, rec, 5L, "1.1.1.1", "ua");
        assertEquals(1L, r.getVoteId());
        assertEquals(5L, r.getStudentId());
        verify(voteRecordMapper).insert(any(VoteRecord.class));
    }

    @Test
    @DisplayName("vote 重复投票更新")
    void vote_update() {
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_ONGOING));
        VoteRecord exist = new VoteRecord();
        exist.setId(100L);
        exist.setChoice("A");
        when(voteRecordMapper.selectOne(any(Wrapper.class))).thenReturn(exist);
        when(voteRecordMapper.updateById(any(VoteRecord.class))).thenReturn(1);
        VoteRecord rec = new VoteRecord();
        rec.setChoice("B");
        rec.setTextAnswer("hi");
        VoteRecord r = service.vote(1L, rec, 5L, "1.1.1.1", "ua");
        assertEquals("B", r.getChoice());
        verify(voteRecordMapper).updateById(any(VoteRecord.class));
    }

    @Test
    @DisplayName("vote 投票未在进行中抛异常")
    void vote_notOngoing() {
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_PENDING));
        VoteRecord rec = new VoteRecord();
        assertThrows(BusinessException.class, () -> service.vote(1L, rec, 5L, "1.1.1.1", "ua"));
    }

    // ===== result =====
    @Test
    @DisplayName("result 单选统计")
    void result_single() {
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_SINGLE, Vote.STATUS_CLOSED));
        VoteRecord r1 = new VoteRecord();
        r1.setChoice("A");
        VoteRecord r2 = new VoteRecord();
        r2.setChoice("B");
        r2.setTextAnswer("note");
        when(voteRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));
        com.aicourse.vo.VoteResultVO res = service.result(1L);
        assertEquals(2L, res.getTotal());
        Map<String, Long> counts = res.getCounts();
        assertEquals(1L, counts.get("A"));
        assertEquals(1L, counts.get("B"));
        List<String> texts = res.getTextAnswers();
        assertEquals(1, texts.size());
    }

    @Test
    @DisplayName("result 多选 JSON 统计")
    void result_multiple() {
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_MULTIPLE, Vote.STATUS_CLOSED));
        VoteRecord r1 = new VoteRecord();
        r1.setChoice("[\"A\",\"B\"]");
        VoteRecord r2 = new VoteRecord();
        r2.setChoice("[\"B\",\"C\"]");
        when(voteRecordMapper.selectList(any())).thenReturn(List.of(r1, r2));
        com.aicourse.vo.VoteResultVO res = service.result(1L);
        Map<String, Long> counts = res.getCounts();
        assertEquals(1L, counts.get("A"));
        assertEquals(2L, counts.get("B"));
        assertEquals(1L, counts.get("C"));
    }

    @Test
    @DisplayName("result 多选非法 JSON 回退原始串")
    void result_multipleBadJson() {
        when(voteMapper.selectById(1L)).thenReturn(vote(1L, 10L, Vote.MODE_MULTIPLE, Vote.STATUS_CLOSED));
        VoteRecord r1 = new VoteRecord();
        r1.setChoice("not-json");
        when(voteRecordMapper.selectList(any())).thenReturn(List.of(r1));
        com.aicourse.vo.VoteResultVO res = service.result(1L);
        Map<String, Long> counts = res.getCounts();
        assertEquals(1L, counts.get("not-json"));
    }

    // ===== myVote =====
    @Test
    @DisplayName("myVote 返回记录")
    void myVote_ok() {
        VoteRecord r = new VoteRecord();
        r.setId(77L);
        r.setChoice("A");
        when(voteRecordMapper.selectOne(any(Wrapper.class))).thenReturn(r);
        assertSame(r, service.myVote(1L, 5L));
    }
}
