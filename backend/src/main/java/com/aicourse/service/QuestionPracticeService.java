package com.aicourse.service;

import com.aicourse.vo.QuestionPracticeResultVO;
import com.aicourse.vo.QuestionPracticeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.core.Authentication;

/**
 * 学生题库练习服务（P0-A1）。
 * 所有入口都先做课程成员/教师归属/管理员作用域校验，再检查课程级刷题开关。
 */
public interface QuestionPracticeService {

    /**
     * 学生练习题目分页（不含答案与解析）。
     */
    Page<QuestionPracticeVO> listPracticeQuestions(Long courseId, String type, String difficulty,
                                                   long page, long size, Authentication auth);

    /**
     * 学生提交答案并即时判分；正确答案与解析只在此时返回。
     */
    QuestionPracticeResultVO checkAnswer(Long questionId, String answer, Authentication auth);

    /**
     * 校验某课程当前是否允许学生刷题。
     * 教师/管理员可读该课程时不要求开关；学生必须已选课且教师已开放。
     */
    void assertPracticeAllowed(Long courseId, Authentication auth);
}
