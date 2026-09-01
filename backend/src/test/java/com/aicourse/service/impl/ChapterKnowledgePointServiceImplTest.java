package com.aicourse.service.impl;

import com.aicourse.entity.ChapterKnowledgePoint;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.mapper.ChapterKnowledgePointMapper;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChapterKnowledgePointServiceImpl 单元测试（纯 Mockito）。
 * <p>覆盖章节-知识点映射查询（空映射兜底 / 批量取点并过滤空值）与批量设置（删旧插新 / 空列表不新增）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChapterKnowledgePointServiceImplTest {

    @Mock
    private KnowledgePointService knowledgePointService;

    @Mock
    private ChapterKnowledgePointMapper chapterKnowledgePointMapper;

    private ChapterKnowledgePointServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ChapterKnowledgePoint.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KnowledgePoint.class);
        service = new ChapterKnowledgePointServiceImpl(knowledgePointService);
        ReflectionTestUtils.setField(service, "baseMapper", chapterKnowledgePointMapper);
    }

    // ===================== getPointsByChapter =====================
    @Test
    @DisplayName("getPointsByChapter 无映射返回空列表且不查询知识点")
    void getPointsByChapter_empty() {
        when(chapterKnowledgePointMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.getPointsByChapter(1L).isEmpty());
        verify(knowledgePointService, never()).listByIds(anyCollection());
    }

    @Test
    @DisplayName("getPointsByChapter 批量查询知识点详情（listByIds 一次取回）")
    void getPointsByChapter_normal() {
        ChapterKnowledgePoint m1 = new ChapterKnowledgePoint();
        m1.setChapterId(1L);
        m1.setKnowledgePointId(11L);
        ChapterKnowledgePoint m2 = new ChapterKnowledgePoint();
        m2.setChapterId(1L);
        m2.setKnowledgePointId(12L);
        when(chapterKnowledgePointMapper.selectList(any())).thenReturn(Arrays.asList(m1, m2));

        KnowledgePoint kp1 = new KnowledgePoint();
        kp1.setId(11L);
        kp1.setName("知识点A");
        when(knowledgePointService.listByIds(anyCollection())).thenReturn(Collections.singletonList(kp1));

        List<KnowledgePoint> points = service.getPointsByChapter(1L);
        assertEquals(1, points.size());
        assertEquals("知识点A", points.get(0).getName());
        verify(knowledgePointService).listByIds(anyCollection());
    }

    // ===================== batchSetMapping =====================
    @Test
    @DisplayName("batchSetMapping 先删旧映射再批量新增")
    void batchSetMapping_normal() {
        service.batchSetMapping(1L, Arrays.asList(11L, 12L));
        verify(chapterKnowledgePointMapper).delete(any());
        verify(chapterKnowledgePointMapper, times(2)).insert(any(ChapterKnowledgePoint.class));
    }

    @Test
    @DisplayName("batchSetMapping null/空列表仅删除不新增")
    void batchSetMapping_empty() {
        service.batchSetMapping(1L, null);
        verify(chapterKnowledgePointMapper).delete(any());
        verify(chapterKnowledgePointMapper, never()).insert(any(ChapterKnowledgePoint.class));

        reset(chapterKnowledgePointMapper);
        service.batchSetMapping(1L, Collections.emptyList());
        verify(chapterKnowledgePointMapper).delete(any());
        verify(chapterKnowledgePointMapper, never()).insert(any(ChapterKnowledgePoint.class));
    }
}
