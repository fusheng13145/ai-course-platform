package com.aicourse.service.component.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import com.aicourse.service.RAGService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * AiDialogueComponent 的提示词契约测试（DR-89）。
 *
 * <p>为什么要钉住提示词的字面：`generateQuestion` 过去只用中文散文描述"包含题目内容、选项和正确答案"，
 * **不点名 JSON 键**。真实 Key 下模型自选了 `stem` 之类的键名，`QuestionServiceImpl` 取 `content`
 * 取不到 → 落库成"题干为空"的题目并返回 200。那不是失败而是**假成功**，比 400 更坏。
 * 提示词点名键位是唯一能让"输出契约"在两侧对齐的地方，所以它得有一条不会随重构漂移的判据。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiDialogueComponentTest {

    @Mock private AiCoreComponent core;
    @Mock private RAGService ragService;

    @InjectMocks
    private AiDialogueComponent dialogue;

    private String captureQuestionPrompt() {
        when(core.buildMessages(any(), any())).thenAnswer(inv ->
                List.of(Map.of("role", "user", "content", String.valueOf((Object) inv.getArgument(0)))));
        when(core.completeAndSanitize(any(), anyDouble(), any())).thenReturn("{}");

        dialogue.generateQuestion(1L, "SINGLE_CHOICE", "EASY", 1);

        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(core).completeAndSanitize(captor.capture(), anyDouble(), eq("question"));
        return captor.getValue().get(0).get("content");
    }

    @Test
    @DisplayName("generateQuestion 提示词必须点名下游要读的每个 JSON 键")
    void generateQuestion_promptNamesEveryKey() {
        String prompt = captureQuestionPrompt();
        for (String key : List.of("content", "options", "correctAnswers", "explanation", "score")) {
            assertTrue(prompt.contains(key), "提示词必须列出键 " + key + "，实际：" + prompt);
        }
    }

    @Test
    @DisplayName("提示词必须禁止代码栅栏之外再包散文（```json 之外不得有解释文字）")
    void generateQuestion_promptForbidsProseAroundJson() {
        String prompt = captureQuestionPrompt();
        assertTrue(prompt.contains("JSON") && (prompt.contains("只") || prompt.contains("仅")),
                "提示词必须要求只返回 JSON，实际：" + prompt);
    }
}
