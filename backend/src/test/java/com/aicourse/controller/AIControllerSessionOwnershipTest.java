package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.dto.QaAskDTO;
import com.aicourse.dto.QaContinueDTO;
import com.aicourse.entity.AiChatSession;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.AIService;
import com.aicourse.service.AnalyticsService;
import com.aicourse.service.AiChatSessionService;
import com.aicourse.service.CourseEnrollmentService;
import com.aicourse.service.CourseService;
import com.aicourse.service.LearningPathService;
import com.aicourse.service.LearningRecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 会话归属断言测试（2026-11-06 验收批，B-05 对象级 ACL）。
 * <p>
 * 改前事实（代码级，已在验收报告登记）：{@code AiChatSessionServiceImpl#getBySessionId}
 * 的谓词只有 sessionId + deleted，而 {@code qaAsk}/{@code qaContinue} 直接使用客户端传入的
 * sessionId —— 拿到他人 sessionId 即可把他人的历史读进 prompt，并把新消息写进他人会话。
 * sessionId 是 UUID、不可枚举，但一次抓包或一条转发链接即失控，故必须做归属比对。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI 会话归属：AIController")
class AIControllerSessionOwnershipTest {

    @Mock private AIService aiService;
    @Mock private AnalyticsService analyticsService;
    @Mock private LearningPathService learningPathService;
    @Mock private CourseEnrollmentService courseEnrollmentService;
    @Mock private CourseService courseService;
    @Mock private AiChatSessionService aiChatSessionService;
    @Mock private LearningRecommendationService learningRecommendationService;
    @Mock private Authentication auth;

    private AIController controller;

    @BeforeEach
    void setUp() {
        controller = new AIController(aiService, analyticsService, learningPathService,
                courseEnrollmentService, courseService, aiChatSessionService,
                learningRecommendationService);
        when(auth.getPrincipal()).thenReturn(5001L);
    }

    private static AiChatSession sessionOf(String sessionId, Long ownerId) {
        AiChatSession session = new AiChatSession();
        session.setSessionId(sessionId);
        session.setUserId(ownerId);
        return session;
    }

    @Test
    @DisplayName("qaAsk 带他人 sessionId：403，且不向他人会话写入任何消息")
    void qaAskRejectsForeignSession() {
        when(aiChatSessionService.getBySessionId("foreign-sid"))
                .thenReturn(sessionOf("foreign-sid", 9999L));

        QaAskDTO dto = new QaAskDTO();
        dto.setQuestion("这道题怎么解");
        dto.setSessionId("foreign-sid");

        assertThatThrownBy(() -> controller.qaAsk(dto, auth).call())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问他人的 AI 会话");

        verify(aiChatSessionService, never()).appendMessage(eq("foreign-sid"), anyString(), anyString());
        verify(aiChatSessionService, never()).createSession(any(), any(), any());
    }

    @Test
    @DisplayName("qaContinue 带他人 sessionId：403，且他人历史不进 prompt")
    void qaContinueRejectsForeignSession() {
        when(aiChatSessionService.getBySessionId("foreign-sid"))
                .thenReturn(sessionOf("foreign-sid", 9999L));

        QaContinueDTO dto = new QaContinueDTO();
        dto.setMessage("继续");
        dto.setSessionId("foreign-sid");

        assertThatThrownBy(() -> controller.qaContinue(dto, auth).call())
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCode.FORBIDDEN));

        verify(aiChatSessionService, never()).getMessages("foreign-sid");
        verify(aiService, never()).chat(any(), any());
    }

    @Test
    @DisplayName("qaAsk 不带 sessionId：为本人为新建会话，正常返回")
    void qaAskCreatesOwnSessionWhenAbsent() throws Exception {
        when(aiChatSessionService.createSession(eq(5001L), any(), any()))
                .thenReturn(sessionOf("own-sid", 5001L));
        when(aiService.ragAsk(anyString(), any())).thenReturn(null);

        QaAskDTO dto = new QaAskDTO();
        dto.setQuestion("你好");

        Map<String, Object> data = controller.qaAsk(dto, auth).call().getData();

        assertThat(data.get("sessionId")).isEqualTo("own-sid");
        verify(aiChatSessionService).appendMessage("own-sid", "user", "你好");
    }

    @Test
    @DisplayName("qaContinue 带本人 sessionId：读取历史并写回，行为不变")
    void qaContinueAcceptsOwnSession() throws Exception {
        when(aiChatSessionService.getBySessionId("own-sid"))
                .thenReturn(sessionOf("own-sid", 5001L));
        when(aiChatSessionService.getMessages("own-sid")).thenReturn(List.of());
        when(aiService.chat(any(), eq(5001L))).thenReturn("回答");

        QaContinueDTO dto = new QaContinueDTO();
        dto.setMessage("继续讲");
        dto.setSessionId("own-sid");

        Map<String, Object> data = controller.qaContinue(dto, auth).call().getData();

        assertThat(data.get("answer")).isEqualTo("回答");
        verify(aiChatSessionService).appendMessage("own-sid", "user", "继续讲");
    }
}
