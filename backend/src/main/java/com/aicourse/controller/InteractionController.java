package com.aicourse.controller;

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

    private final SignInService signInService;
    private final VoteService voteService;

    public InteractionController(SignInService signInService, VoteService voteService) {
        this.signInService = signInService;
        this.voteService = voteService;
    }

    // ===================== 签到（教师 / 管理员） =====================

    @PostMapping("/sign-in")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignIn> createSignIn(@RequestBody SignIn signIn, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("发起签到成功", signInService.createSignIn(signIn, userId, auth));
    }

    @PostMapping("/sign-in/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignIn> publishSignIn(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("已开始签到", signInService.publish(id, userId, auth));
    }

    @PostMapping("/sign-in/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignIn> closeSignIn(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("已结束签到", signInService.close(id, userId, auth));
    }

    @GetMapping("/sign-in/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<SignIn>> listSignIns(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(signInService.listByCourse(courseId, userId, auth));
    }

    @GetMapping("/sign-in/{id}/records")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<SignInRecord>> listSignInRecords(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(signInService.listRecords(id, userId, auth));
    }

    @PostMapping("/sign-in/{id}/manual")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<SignInRecord> manualMark(@PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long studentId = Long.valueOf(body.get("studentId"));
        String status = body.get("status");
        return ApiResponse.success("标记成功", signInService.manualMark(id, studentId, status, userId, auth));
    }

    // ===================== 签到（学生） =====================

    @PostMapping("/sign-in/{id}/sign")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SignInRecord> sign(@PathVariable Long id,
                                       @RequestBody SignInRecord record,
                                       Authentication auth,
                                       HttpServletRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("签到成功",
                signInService.sign(id, record, userId, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @GetMapping("/sign-in/{id}/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SignInRecord> mySignIn(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(signInService.myRecord(id, userId));
    }

    // ===================== 投票（教师 / 管理员） =====================

    @PostMapping("/vote")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Vote> createVote(@RequestBody Vote vote, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("发起投票成功", voteService.createVote(vote, userId, auth));
    }

    @PostMapping("/vote/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<Vote> closeVote(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("已结束投票", voteService.close(id, userId, auth));
    }

    @GetMapping("/vote/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<Vote>> listVotes(@PathVariable Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(voteService.listByCourse(courseId, userId, auth));
    }

    // ===================== 投票（公开查询 / 学生） =====================

    @GetMapping("/vote/{id}")
    public ApiResponse<Vote> getVote(@PathVariable Long id) {
        return ApiResponse.success(voteService.getDetail(id));
    }

    @PostMapping("/vote/{id}/vote")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<VoteRecord> vote(@PathVariable Long id,
                                     @RequestBody VoteRecord record,
                                     Authentication auth,
                                     HttpServletRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success("投票成功",
                voteService.vote(id, record, userId, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @GetMapping("/vote/{id}/result")
    public ApiResponse<Map<String, Object>> voteResult(@PathVariable Long id) {
        return ApiResponse.success(voteService.result(id));
    }

    @GetMapping("/vote/{id}/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<VoteRecord> myVote(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(voteService.myVote(id, userId));
    }
}
