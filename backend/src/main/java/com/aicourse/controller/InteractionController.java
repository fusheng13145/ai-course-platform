package com.aicourse.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.entity.Vote;
import com.aicourse.entity.VoteRecord;
import com.aicourse.service.SignInService;
import com.aicourse.service.VoteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 课堂互动接口（签到 / 投票）
 * 基础路径：/api/interaction
 */
@RestController
@RequestMapping("/interaction")
@Tag(name = "课堂互动")
public class InteractionController {

    private static final Logger log = LoggerFactory.getLogger(InteractionController.class);

    private final SignInService signInService;
    private final VoteService voteService;

    public InteractionController(SignInService signInService, VoteService voteService) {
        this.signInService = signInService;
        this.voteService = voteService;
    }

    // ===================== 签到（教师 / 管理员） =====================

    /** 教师/管理员创建一条新的签到活动 */
    @PostMapping("/sign-in")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignIn> createSignIn(@Valid @RequestBody SignIn signIn, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("发起签到成功", signInService.createSignIn(signIn, userId, auth));
    }

    /** 发布签到活动，开始接受学生签到 */
    @PostMapping("/sign-in/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignIn> publishSignIn(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("已开始签到", signInService.publish(id, userId, auth));
    }

    /** 结束签到活动 */
    @PostMapping("/sign-in/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignIn> closeSignIn(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("已结束签到", signInService.close(id, userId, auth));
    }

    /** 列出某课程下的全部签到活动 */
    @GetMapping("/sign-in/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<SignIn>> listSignIns(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(signInService.listByCourse(courseId, userId, auth));
    }

    /** 查询某次签到的全部签到记录 */
    @GetMapping("/sign-in/{id}/records")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<SignInRecord>> listSignInRecords(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(signInService.listRecords(id, userId, auth));
    }

    /** 教师手动补签/标记某学生的签到状态 */
    @PostMapping("/sign-in/{id}/manual")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignInRecord> manualMark(@PathVariable Long id,
                                               @Valid @RequestBody Map<String, String> body,
                                               Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long studentId = Long.valueOf(body.get("studentId"));
        String status = body.get("status");
        return ApiResponse.success("标记成功", signInService.manualMark(id, studentId, status, userId, auth));
    }

    // ===================== 签到（学生） =====================

    /** 学生提交签到，记录 IP 与 UA 用于防代签 */
    @PostMapping("/sign-in/{id}/sign")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SignInRecord> sign(@PathVariable Long id,
                                       @Valid @RequestBody SignInRecord record,
                                       Authentication auth,
                                       HttpServletRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("签到成功",
                signInService.sign(id, record, userId, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    /** 查询当前学生在某次签到中的记录 */
    @GetMapping("/sign-in/{id}/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SignInRecord> mySignIn(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(signInService.myRecord(id, userId));
    }

    // ===================== 投票（教师 / 管理员） =====================

    /** 教师/管理员创建一条新的投票活动 */
    @PostMapping("/vote")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Vote> createVote(@Valid @RequestBody Vote vote, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("发起投票成功", voteService.createVote(vote, userId, auth));
    }

    /** 发布投票活动，开始接受学生投票 */
    @PostMapping("/vote/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Vote> publishVote(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("投票已发布", voteService.publish(id, userId, auth));
    }

    /** 结束投票活动 */
    @PostMapping("/vote/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Vote> closeVote(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("已结束投票", voteService.close(id, userId, auth));
    }

    /** 列出某课程下的全部投票活动 */
    @GetMapping("/vote/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<Vote>> listVotes(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(voteService.listByCourse(courseId, userId, auth));
    }

    // ===================== 投票（公开查询 / 学生） =====================

    /** 公开查询某个投票活动的详情 */
    @GetMapping("/vote/{id}")
    public ApiResponse<Vote> getVote(@PathVariable Long id) {
        return ApiResponse.success(voteService.getDetail(id));
    }

    /** 学生提交投票，记录 IP 与 UA 用于防代投 */
    @PostMapping("/vote/{id}/vote")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<VoteRecord> vote(@PathVariable Long id,
                                     @Valid @RequestBody VoteRecord record,
                                     Authentication auth,
                                     HttpServletRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("投票成功",
                voteService.vote(id, record, userId, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    /** 公开查询某个投票的实时结果统计 */
    @GetMapping("/vote/{id}/result")
    public ApiResponse<Map<String, Object>> voteResult(@PathVariable Long id) {
        return ApiResponse.success(voteService.result(id));
    }

    /** 查询当前学生在某个投票中的投票记录 */
    @GetMapping("/vote/{id}/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<VoteRecord> myVote(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(voteService.myVote(id, userId));
    }
}
