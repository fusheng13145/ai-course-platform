package com.aicourse.controller;
import com.aicourse.controller.InteractionController;
import com.aicourse.entity.SignIn;
import com.aicourse.entity.SignInRecord;
import com.aicourse.entity.Vote;
import com.aicourse.entity.VoteRecord;
import com.aicourse.service.SignInService;
import com.aicourse.service.VoteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * InteractionController 接口测试（升级版：真实业务断言）。
 * 对每个 endpoint 打桩对应的 service 方法并断言业务字段透传，verify 确认路由正确委托。
 * 所有 @PreAuthorize 限定的 endpoint 均新增了越权 403 测试。
 */
@WebMvcTest(InteractionController.class)
class InteractionControllerTest extends ControllerTestBase {

    @MockitoBean
    private com.aicourse.service.SignInService signInService;
    @MockitoBean
    private com.aicourse.service.VoteService voteService;

    // ===================== 签到（教师/管理员） =====================

    @Test
    @DisplayName("POST /interaction/sign-in - 教师发起签到返回200，ID透传")
    void createSignIn_authorized_passes_security() throws Exception {
        SignIn signIn = new SignIn();
        signIn.setId(1L);
        when(signInService.createSignIn(any(), eq(1L), any())).thenReturn(signIn);
        mockMvc.perform(post("/interaction/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"签到\"}").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(signInService).createSignIn(any(), eq(1L), any());
    }

    @Test
    @DisplayName("POST /interaction/sign-in - 未认证返回401")
    void createSignIn_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/sign-in").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/sign-in - 学生越权返回403")
    void createSignIn_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/interaction/sign-in").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"签到\"}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/publish - 教师发布签到返回200，ID透传")
    void publishSignIn_authorized_passes_security() throws Exception {
        SignIn signIn = new SignIn();
        signIn.setId(1L);
        when(signInService.publish(eq(1L), eq(1L), any())).thenReturn(signIn);
        mockMvc.perform(post("/interaction/sign-in/1/publish").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(signInService).publish(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/publish - 未认证返回401")
    void publishSignIn_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/publish"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/publish - 学生越权返回403")
    void publishSignIn_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/publish").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/close - 教师结束签到返回200，ID透传")
    void closeSignIn_authorized_passes_security() throws Exception {
        SignIn signIn = new SignIn();
        signIn.setId(1L);
        when(signInService.close(eq(1L), eq(1L), any())).thenReturn(signIn);
        mockMvc.perform(post("/interaction/sign-in/1/close").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(signInService).close(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/close - 未认证返回401")
    void closeSignIn_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/close"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/close - 学生越权返回403")
    void closeSignIn_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/close").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/course/1 - 教师查询签到列表返回200，数组透传")
    void listSignIns_authorized_passes_security() throws Exception {
        SignIn signIn = new SignIn();
        signIn.setId(1L);
        when(signInService.listByCourse(eq(1L), eq(1L), any())).thenReturn(List.of(signIn));
        mockMvc.perform(get("/interaction/sign-in/course/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(signInService).listByCourse(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/course/1 - 未认证返回401")
    void listSignIns_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/interaction/sign-in/course/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/course/1 - 学生越权返回403")
    void listSignIns_forbidden_for_student() throws Exception {
        mockMvc.perform(get("/interaction/sign-in/course/1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/1/records - 教师查询签到记录返回200，数组透传")
    void listSignInRecords_authorized_passes_security() throws Exception {
        SignInRecord rec = new SignInRecord();
        rec.setId(1L);
        when(signInService.listRecords(eq(1L), eq(1L), any())).thenReturn(List.of(rec));
        mockMvc.perform(get("/interaction/sign-in/1/records").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(signInService).listRecords(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/1/records - 未认证返回401")
    void listSignInRecords_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/interaction/sign-in/1/records"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/1/records - 学生越权返回403")
    void listSignInRecords_forbidden_for_student() throws Exception {
        mockMvc.perform(get("/interaction/sign-in/1/records").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/manual - 教师手动标记返回200，ID透传")
    void manualMark_authorized_passes_security() throws Exception {
        SignInRecord rec = new SignInRecord();
        rec.setId(1L);
        when(signInService.manualMark(eq(1L), eq(2L), eq("PRESENT"), eq(1L), any())).thenReturn(rec);
        mockMvc.perform(post("/interaction/sign-in/1/manual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"2\",\"status\":\"PRESENT\"}").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(signInService).manualMark(eq(1L), eq(2L), eq("PRESENT"), eq(1L), any());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/manual - 未认证返回401")
    void manualMark_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/manual").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/manual - 学生越权返回403")
    void manualMark_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/manual").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ===================== 签到（学生） =====================

    @Test
    @DisplayName("POST /interaction/sign-in/1/sign - 学生签到返回200，ID透传")
    void sign_authorized_passes_security() throws Exception {
        SignInRecord rec = new SignInRecord();
        rec.setId(1L);
        when(signInService.sign(eq(1L), any(), eq(1L), anyString(), anyString())).thenReturn(rec);
        mockMvc.perform(post("/interaction/sign-in/1/sign").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("User-Agent", "JUnit")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(signInService).sign(eq(1L), any(), eq(1L), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/sign - 未认证返回401")
    void sign_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/sign").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/sign-in/1/sign - 教师越权返回403")
    void sign_forbidden_for_teacher() throws Exception {
        mockMvc.perform(post("/interaction/sign-in/1/sign").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/1/my - 学生查询我的签到返回200，ID透传")
    void mySignIn_authorized_passes_security() throws Exception {
        SignInRecord rec = new SignInRecord();
        rec.setId(1L);
        when(signInService.myRecord(eq(1L), eq(1L))).thenReturn(rec);
        mockMvc.perform(get("/interaction/sign-in/1/my").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(signInService).myRecord(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /interaction/sign-in/1/my - 未认证返回401")
    void mySignIn_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/interaction/sign-in/1/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /interaction/sign-in/1/my - 教师越权返回403")
    void mySignIn_forbidden_for_teacher() throws Exception {
        mockMvc.perform(get("/interaction/sign-in/1/my").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    // ===================== 投票（教师/管理员） =====================

    @Test
    @DisplayName("POST /interaction/vote - 教师发起投票返回200，ID透传")
    void createVote_authorized_passes_security() throws Exception {
        Vote vote = new Vote();
        vote.setId(1L);
        when(voteService.createVote(any(), eq(1L), any())).thenReturn(vote);
        mockMvc.perform(post("/interaction/vote").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"投票\"}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(voteService).createVote(any(), eq(1L), any());
    }

    @Test
    @DisplayName("POST /interaction/vote - 未认证返回401")
    void createVote_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/vote").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/vote - 学生越权返回403")
    void createVote_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/interaction/vote").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"投票\"}")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /interaction/vote/1/close - 教师结束投票返回200，ID透传")
    void closeVote_authorized_passes_security() throws Exception {
        Vote vote = new Vote();
        vote.setId(1L);
        when(voteService.close(eq(1L), eq(1L), any())).thenReturn(vote);
        mockMvc.perform(post("/interaction/vote/1/close").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(voteService).close(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("POST /interaction/vote/1/close - 未认证返回401")
    void closeVote_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/vote/1/close"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/vote/1/close - 学生越权返回403")
    void closeVote_forbidden_for_student() throws Exception {
        mockMvc.perform(post("/interaction/vote/1/close").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /interaction/vote/course/1 - 教师查询投票列表返回200，数组透传")
    void listVotes_authorized_passes_security() throws Exception {
        Vote vote = new Vote();
        vote.setId(1L);
        when(voteService.listByCourse(eq(1L), eq(1L), any())).thenReturn(List.of(vote));
        mockMvc.perform(get("/interaction/vote/course/1").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
        verify(voteService).listByCourse(eq(1L), eq(1L), any());
    }

    @Test
    @DisplayName("GET /interaction/vote/course/1 - 未认证返回401")
    void listVotes_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/interaction/vote/course/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /interaction/vote/course/1 - 学生越权返回403")
    void listVotes_forbidden_for_student() throws Exception {
        mockMvc.perform(get("/interaction/vote/course/1").with(withUser(1L, "STUDENT")))
                .andExpect(status().isForbidden());
    }

    // ===================== 投票（公开/学生） =====================

    @Test
    @DisplayName("GET /interaction/vote/1 - 查询投票详情返回200，ID透传")
    void getVote_authorized_passes_security() throws Exception {
        Vote vote = new Vote();
        vote.setId(1L);
        when(voteService.getDetail(eq(1L))).thenReturn(vote);
        mockMvc.perform(get("/interaction/vote/1").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(voteService).getDetail(eq(1L));
    }

    @Test
    @DisplayName("GET /interaction/vote/1 - 未认证返回401")
    void getVote_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/interaction/vote/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/vote/1/vote - 学生投票返回200，ID透传")
    void vote_authorized_passes_security() throws Exception {
        VoteRecord rec = new VoteRecord();
        rec.setId(1L);
        when(voteService.vote(eq(1L), any(), eq(1L), anyString(), anyString())).thenReturn(rec);
        mockMvc.perform(post("/interaction/vote/1/vote").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("User-Agent", "JUnit")
                        .with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(voteService).vote(eq(1L), any(), eq(1L), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /interaction/vote/1/vote - 未认证返回401")
    void vote_unauthorized_returns_401() throws Exception {
        mockMvc.perform(post("/interaction/vote/1/vote").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /interaction/vote/1/vote - 教师越权返回403")
    void vote_forbidden_for_teacher() throws Exception {
        mockMvc.perform(post("/interaction/vote/1/vote").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /interaction/vote/1/result - 查询投票结果返回200，计数字段透传")
    void voteResult_authorized_passes_security() throws Exception {
        when(voteService.result(eq(1L))).thenReturn(Map.of("count", 5));
        mockMvc.perform(get("/interaction/vote/1/result").with(withUser(1L, "STUDENT", "TEACHER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.count").value(5));
        verify(voteService).result(eq(1L));
    }

    @Test
    @DisplayName("GET /interaction/vote/1/result - 未认证返回401")
    void voteResult_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/interaction/vote/1/result"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /interaction/vote/1/my - 学生查询我的投票返回200，ID透传")
    void myVote_authorized_passes_security() throws Exception {
        VoteRecord rec = new VoteRecord();
        rec.setId(1L);
        when(voteService.myVote(eq(1L), eq(1L))).thenReturn(rec);
        mockMvc.perform(get("/interaction/vote/1/my").with(withUser(1L, "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
        verify(voteService).myVote(eq(1L), eq(1L));
    }

    @Test
    @DisplayName("GET /interaction/vote/1/my - 未认证返回401")
    void myVote_unauthorized_returns_401() throws Exception {
        mockMvc.perform(get("/interaction/vote/1/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /interaction/vote/1/my - 教师越权返回403")
    void myVote_forbidden_for_teacher() throws Exception {
        mockMvc.perform(get("/interaction/vote/1/my").with(withUser(1L, "TEACHER", "ADMIN")))
                .andExpect(status().isForbidden());
    }
}
