package com.aicourse.service;

import com.aicourse.entity.Vote;
import com.aicourse.entity.VoteRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

/**
 * 课堂投票/问卷服务
 */
public interface VoteService extends IService<Vote> {

    /**
     * 教师发起投票/问卷
     */
    Vote createVote(Vote vote, Long userId, Authentication auth);

    /**
     * 结束投票
     */
    Vote close(Long id, Long userId, Authentication auth);

    /**
     * 查询投票详情（含选项）
     */
    Vote getDetail(Long id);

    /**
     * 按课程列出投票（教师）
     */
    List<Vote> listByCourse(Long courseId, Long userId, Authentication auth);

    /**
     * 学生投票
     * @param record 含 choice/textAnswer
     * @param ip 投票IP
     * @param ua 用户代理
     */
    VoteRecord vote(Long id, VoteRecord record, Long userId, String ip, String ua);

    /**
     * 统计结果：{ "total": N, "counts": { "a": 3, "b": 1 } }
     */
    Map<String, Object> result(Long id);

    /**
     * 查询当前学生本人的投票记录
     */
    VoteRecord myVote(Long id, Long userId);
}
