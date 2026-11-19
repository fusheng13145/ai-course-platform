package com.aicourse.service.component.learning;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.KnowledgePointService;
import com.aicourse.service.LearningPathNodeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LearningPathGenerateComponent} 序列化失败契约测试（2026-11-19 错误处理收口）。
 *
 * <p>此前 stages/dailyTasks/planDetail 三处 JSON 序列化 catch 会把 {@code [{"error":"序列化失败"}]}
 * 等错误字面量持久化进 learning_paths——路径「创建成功」但内容损坏且永久固化，
 * 调用方拿到 200 成功行。现契约：序列化失败必须抛 {@link BusinessException}(500)，
 * 由 {@code LearningPathServiceImpl} 的 {@code @Transactional(rollbackFor)} 整体回滚，不落半成品。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("学习路径生成：序列化失败必须整体失败，不得落错误字面量")
class LearningPathGenerateComponentTest {

    @Mock private KnowledgePointService knowledgePointService;
    @Mock private LearningPathNodeService nodeService;
    @Mock private LearningPathMapper learningPathMapper;
    @Mock private LearningPathMasteryComponent masteryComponent;
    @Mock private LearningPathStateComponent stateComponent;
    @Mock private ObjectMapper objectMapper;

    private LearningPathGenerateComponent component;

    @BeforeEach
    void setUp() {
        component = new LearningPathGenerateComponent(knowledgePointService, nodeService,
                learningPathMapper, masteryComponent, stateComponent, objectMapper);
        KnowledgePoint root = new KnowledgePoint();
        root.setId(1L);
        root.setName("计算机网络概述");
        root.setCourseId(2L);
        when(knowledgePointService.getRootPoints(2L)).thenReturn(List.of(root));
        when(knowledgePointService.getChildPoints(1L)).thenReturn(List.of());
        when(nodeService.save(any(LearningPathNode.class))).thenReturn(true);
    }

    @Test
    @DisplayName("generatePath：stages 序列化失败抛 500 业务异常，不落任何路径行")
    void generatePath_serializationFailure_throwsAndDoesNotPersist() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.generatePath(1L, 2L, "路径", "目标"));
        assertEquals(ApiCode.INTERNAL_SERVER_ERROR, ex.getCode());
        verify(learningPathMapper, never()).updateById(any(LearningPath.class));
    }

    @Test
    @DisplayName("generatePathPersonalized：stages 序列化失败抛 500 业务异常，不落任何路径行")
    void generatePathPersonalized_serializationFailure_throwsAndDoesNotPersist() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });
        BusinessException ex = assertThrows(BusinessException.class,
                () -> component.generatePathPersonalized(1L, 2L, "路径", "目标",
                        Map.of("计算机网络概述", 30.0)));
        assertEquals(ApiCode.INTERNAL_SERVER_ERROR, ex.getCode());
        verify(learningPathMapper, never()).updateById(any(LearningPath.class));
    }
}
