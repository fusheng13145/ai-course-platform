package com.aicourse.service.impl;

import com.aicourse.common.ApiCode;
import com.aicourse.entity.KnowledgePoint;
import com.aicourse.entity.KnowledgePointRelation;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.KnowledgePointRelationMapper;
import com.aicourse.service.KnowledgePointService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B-13 ⑪ 的回归断言：{@code relationType} 写入归一化。
 *
 * <p>修复前的缺陷：{@code createRelation} 把入参原样落库，而
 * {@code KnowledgePointServiceImpl#getPrerequisites} 用
 * {@code eq(relation_type, "PREREQUISITE")} 精确匹配 ⇒ 前端传
 * {@code prerequisite} 时「创建成功但前置列表查不回来」，且库里会同时存在
 * 大小写两份同义边。</p>
 *
 * <p>修复后：写入侧 trim + 大写；读取侧 {@code UPPER(relation_type) = ?}。
 * 本类断言写入侧的三条行为（大写归一 / 去空白 / 空值 400）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("B-13 ⑪：知识点关系类型归一化")
class KnowledgePointRelationTypeNormalizeTest {

    @Mock
    private KnowledgePointRelationMapper relationMapper;

    @Mock
    private KnowledgePointService knowledgePointService;

    private KnowledgePointRelationServiceImpl service;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 依赖实体元数据（列名解析）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KnowledgePointRelation.class);
        service = new KnowledgePointRelationServiceImpl(knowledgePointService);
        ReflectionTestUtils.setField(service, "baseMapper", relationMapper);

        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(1L);
        when(knowledgePointService.getById(any())).thenReturn(kp);
        when(relationMapper.selectCount(any())).thenReturn(0L);
        when(relationMapper.insert(any(KnowledgePointRelation.class))).thenReturn(1);
    }

    @Test
    @DisplayName("小写 prerequisite 落库为大写 PREREQUISITE（否则 getPrerequisites 查不回）")
    void createRelation_normalizesLowercaseToUppercase() {
        service.createRelation(1L, 2L, "prerequisite", new BigDecimal("1.0"), "小写入参");

        ArgumentCaptor<KnowledgePointRelation> captor = ArgumentCaptor.forClass(KnowledgePointRelation.class);
        verify(relationMapper).insert(captor.capture());
        assertEquals("PREREQUISITE", captor.getValue().getRelationType(),
                "落库必须是大写，否则按大写精确匹配的读取路径查不回这条边");
    }

    @Test
    @DisplayName("混合大小写与首尾空白一并归一化")
    void createRelation_trimsAndUppercases() {
        service.createRelation(1L, 2L, "  Prerequisite  ", null, null);

        ArgumentCaptor<KnowledgePointRelation> captor = ArgumentCaptor.forClass(KnowledgePointRelation.class);
        verify(relationMapper).insert(captor.capture());
        assertEquals("PREREQUISITE", captor.getValue().getRelationType());
    }

    @Test
    @DisplayName("空类型返回 400，而不是落一条空类型边")
    void createRelation_blankType_returns400() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createRelation(1L, 2L, "   ", null, null));
        assertEquals(ApiCode.BAD_REQUEST, ex.getCode());
        assertTrue(ex.getMessage().contains("关联类型不能为空"));
    }
}
