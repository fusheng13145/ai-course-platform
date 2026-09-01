package com.aicourse.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aicourse.entity.Course;
import com.aicourse.entity.Vote;
import com.aicourse.entity.VoteRecord;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.CourseMapper;
import com.aicourse.mapper.VoteMapper;
import com.aicourse.mapper.VoteRecordMapper;
import com.aicourse.service.VoteService;
import com.aicourse.vo.VoteResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 课堂投票/问卷服务实现
 */
@Service
public class VoteServiceImpl extends ServiceImpl<VoteMapper, Vote> implements VoteService {

    private static final Logger log = LoggerFactory.getLogger(VoteServiceImpl.class);

    private final CourseMapper courseMapper;
    private final VoteRecordMapper voteRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VoteServiceImpl(CourseMapper courseMapper, VoteRecordMapper voteRecordMapper) {
        this.courseMapper = courseMapper;
        this.voteRecordMapper = voteRecordMapper;
    }


    /** 创建投票 */
    @Override
    public Vote createVote(Vote vote, Long userId, Authentication auth) {
        log.info("创建投票");

        assertCourseOwner(vote.getCourseId(), userId, auth);
        if (vote.getVoteMode() == null || vote.getVoteMode().isBlank()) {
            vote.setVoteMode(Vote.MODE_SINGLE);
        }
        if (vote.getOptions() == null || vote.getOptions().isBlank()) {
            throw new BusinessException("投票选项不能为空");
        }
        vote.setCreatorId(userId);
        vote.setStatus(Vote.STATUS_PENDING);
        this.save(vote);
        return vote;
    }


    /** 关闭投票 */
    @Override
    public Vote close(Long id, Long userId, Authentication auth) {
        Vote vote = getDetail(id);
        assertCourseOwner(vote.getCourseId(), userId, auth);
        if (Vote.STATUS_CLOSED.equals(vote.getStatus())) {
            throw new BusinessException("投票已结束");
        }
        vote.setStatus(Vote.STATUS_CLOSED);
        vote.setClosedAt(LocalDateTime.now());
        this.updateById(vote);
        return vote;
    }

    /**
     * 发布投票（修复 P1-15）：PENDING → ONGOING，发布后学生方可投票。
     * 仅课程所有者可发布；已发布/已结束的投票不可重复发布。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Vote publish(Long id, Long userId, Authentication auth) {
        log.info("发布投票");

        Vote vote = getDetail(id);
        assertCourseOwner(vote.getCourseId(), userId, auth);
        if (Vote.STATUS_ONGOING.equals(vote.getStatus())) {
            throw new BusinessException("投票已在进行中");
        }
        if (Vote.STATUS_CLOSED.equals(vote.getStatus())) {
            throw new BusinessException("投票已结束，无法发布");
        }
        vote.setStatus(Vote.STATUS_ONGOING);
        vote.setStartedAt(LocalDateTime.now());
        this.updateById(vote);
        return vote;
    }


    /** 查询投票 */
    @Override
    public Vote getDetail(Long id) {
        Vote vote = this.getById(id);
        if (vote == null) {
            throw new BusinessException("投票不存在");
        }
        return vote;
    }


    /** 查询投票 */
    @Override
    public List<Vote> listByCourse(Long courseId, Long userId, Authentication auth) {
        assertCourseOwner(courseId, userId, auth);
        LambdaQueryWrapper<Vote> qw = new LambdaQueryWrapper<Vote>()
                .eq(Vote::getCourseId, courseId)
                .orderByDesc(Vote::getCreatedAt);
        return this.list(qw);
    }


    /** 投票相关操作 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public VoteRecord vote(Long id, VoteRecord record, Long userId, String ip, String ua) {
        Vote vote = getDetail(id);
        if (!Vote.STATUS_ONGOING.equals(vote.getStatus())) {
            throw new BusinessException("投票未在进行中");
        }
        LambdaQueryWrapper<VoteRecord> qw = new LambdaQueryWrapper<VoteRecord>()
                .eq(VoteRecord::getVoteId, id)
                .eq(VoteRecord::getStudentId, userId);
        VoteRecord exist = voteRecordMapper.selectOne(qw);
        if (exist != null) {
            exist.setChoice(record.getChoice());
            exist.setTextAnswer(record.getTextAnswer());
            exist.setIp(ip);
            exist.setUserAgent(ua);
            voteRecordMapper.updateById(exist);
            return exist;
        }
        record.setVoteId(id);
        record.setStudentId(userId);
        record.setIp(ip);
        record.setUserAgent(ua);
        voteRecordMapper.insert(record);
        return record;
    }


    /** 投票相关操作 */
    @Override
    public VoteResultVO result(Long id) {
        Vote vote = getDetail(id);
        LambdaQueryWrapper<VoteRecord> qw = new LambdaQueryWrapper<VoteRecord>()
                .eq(VoteRecord::getVoteId, id);
        List<VoteRecord> records = voteRecordMapper.selectList(qw);
        Map<String, Long> counts = new LinkedHashMap<>();
        List<String> textAnswers = new ArrayList<>();
        for (VoteRecord r : records) {
            if (Vote.MODE_MULTIPLE.equals(vote.getVoteMode()) && r.getChoice() != null) {
                // 多选：choice 为 JSON 数组，按每个选项分别计数
                try {
                    List<String> choices = objectMapper.readValue(r.getChoice(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    for (String c : choices) {
                        counts.merge(c, 1L, Long::sum);
                    }
                } catch (Exception e) {
                    log.error("投票相关操作失败: {}", e.getMessage(), e);

                    counts.merge(r.getChoice(), 1L, Long::sum);
                }
            } else if (r.getChoice() != null) {
                counts.merge(r.getChoice(), 1L, Long::sum);
            }
            if (r.getTextAnswer() != null && !r.getTextAnswer().isBlank()) {
                textAnswers.add(r.getTextAnswer());
            }
        }
        return new VoteResultVO((long) records.size(), counts, textAnswers, vote.getVoteMode());
    }


    /** 投票相关操作 */
    @Override
    public VoteRecord myVote(Long id, Long userId) {
        LambdaQueryWrapper<VoteRecord> qw = new LambdaQueryWrapper<VoteRecord>()
                .eq(VoteRecord::getVoteId, id)
                .eq(VoteRecord::getStudentId, userId);
        return voteRecordMapper.selectOne(qw);
    }

    // ===================== 私有辅助 =====================

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
