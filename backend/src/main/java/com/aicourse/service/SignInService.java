package com.aicourse.service;

import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * 课堂签到服务
 */
public interface SignInService extends IService<SignIn> {

    /**
     * 教师发起签到
     * @param signIn 签到配置
     * @param userId 当前用户ID
     * @param auth 认证信息（用于课程归属校验）
     * @return 创建的签到
     */
    SignIn createSignIn(SignIn signIn, Long userId, Authentication auth);

    /**
     * 开始签到（进入进行中状态，生成签到码/二维码内容）
     */
    SignIn publish(Long id, Long userId, Authentication auth);

    /**
     * 结束签到
     */
    SignIn close(Long id, Long userId, Authentication auth);

    /**
     * 查询签到详情
     */
    SignIn getDetail(Long id);

    /**
     * 按课程列出签到（教师）
     */
    List<SignIn> listByCourse(Long courseId, Long userId, Authentication auth);

    /**
     * 学生签到
     * @param record 含拍照/位置/手势等核验信息
     * @param ip 签到IP
     * @param ua 用户代理
     */
    SignInRecord sign(Long id, SignInRecord record, Long userId, String ip, String ua);

    /**
     * 查询某次签到的所有记录（教师）
     */
    List<SignInRecord> listRecords(Long id, Long userId, Authentication auth);

    /**
     * 教师手动标记（到课/缺勤/病假）
     */
    SignInRecord manualMark(Long id, Long studentId, String status, Long userId, Authentication auth);

    /**
     * 查询当前学生本人在某次签到的记录
     */
    SignInRecord myRecord(Long id, Long userId);

    /**
     * 批量查询某学生的全部签到记录（修复 dashboard 等场景的 N+1：原在循环内逐次 myRecord）
     * @param userId 学生ID
     * @return 该学生所有签到记录
     */
    List<SignInRecord> listMyRecords(Long userId);
}
