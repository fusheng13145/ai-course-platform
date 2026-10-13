package com.aicourse.service.impl;

import com.aicourse.entity.AiGenerationRestriction;
import com.aicourse.mapper.AiGenerationRestrictionMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * AiGenerationRestrictionServiceImpl 单元测试（角色矩阵 ④ 修订：逐人限制名单）。
 *
 * <p>钉两条口径：① 加入名单是**幂等 upsert**——同一教师只保留一行，重复加入只更新原因与操作人、
 * 且**保留首次加入时间**（"何时开始被限制"不可被覆盖）；② 移出返回是否真的删到，
 * 供上层区分"解除成功"与"本来就不在名单"。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiGenerationRestrictionServiceImplTest {

    @Mock
    private AiGenerationRestrictionMapper mapper;

    private AiGenerationRestrictionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiGenerationRestrictionServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    @DisplayName("isRestricted：null 直接判否且不查库（未认证不该产生查询）")
    void isRestricted_null_shortCircuit() {
        assertFalse(service.isRestricted(null));
        verify(mapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("isRestricted：名单内为真、名单外为假（按 userId 精确计数）")
    void isRestricted_byUserId() {
        when(mapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.isRestricted(7L));
        when(mapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.isRestricted(8L));
    }

    @Test
    @DisplayName("restrict：首次加入 → 落库一行，原因与操作人留痕")
    void restrict_inserts() {
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(mapper.insert(any(AiGenerationRestriction.class))).thenReturn(1);

        AiGenerationRestriction saved = service.restrict(7L, "多次生成重复题", 1L);

        ArgumentCaptor<AiGenerationRestriction> captor =
                ArgumentCaptor.forClass(AiGenerationRestriction.class);
        verify(mapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("多次生成重复题", captor.getValue().getReason());
        assertEquals(1L, captor.getValue().getRestrictedBy());
        assertEquals(7L, saved.getUserId());
    }

    @Test
    @DisplayName("restrict：已在名单 → 幂等更新原因与操作人，不新增行、不覆盖首次加入时间")
    void restrict_updatesExisting() {
        LocalDateTime firstAddedAt = LocalDateTime.of(2026, 3, 1, 10, 0);
        AiGenerationRestriction existing = new AiGenerationRestriction();
        existing.setId(5L);
        existing.setUserId(7L);
        existing.setReason("旧原因");
        existing.setRestrictedBy(2L);
        existing.setCreatedAt(firstAddedAt);
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(existing);
        when(mapper.updateById(any(AiGenerationRestriction.class))).thenReturn(1);

        AiGenerationRestriction saved = service.restrict(7L, "新原因", 1L);

        verify(mapper, never()).insert(any(AiGenerationRestriction.class));
        ArgumentCaptor<AiGenerationRestriction> captor =
                ArgumentCaptor.forClass(AiGenerationRestriction.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("新原因", captor.getValue().getReason());
        assertEquals(1L, captor.getValue().getRestrictedBy());
        assertEquals(firstAddedAt, saved.getCreatedAt(), "首次加入时间必须保留");
    }

    @Test
    @DisplayName("unrestrict：删到行返回 true；原本不在名单返回 false；null 直接返回 false")
    void unrestrict_reportsWhetherRemoved() {
        when(mapper.delete(any())).thenReturn(1);
        assertTrue(service.unrestrict(7L));

        when(mapper.delete(any())).thenReturn(0);
        assertFalse(service.unrestrict(7L));

        assertFalse(service.unrestrict(null));
    }

    @Test
    @DisplayName("listAll：透传按加入时间倒序的名单")
    void listAll_passthrough() {
        AiGenerationRestriction row = new AiGenerationRestriction();
        row.setUserId(9L);
        when(mapper.selectList(any())).thenReturn(List.of(row));
        assertEquals(1, service.listAll().size());
        verify(mapper).selectList(any());
    }

    @Test
    @DisplayName("restrict：userId 为 null 时不抛异常（角色/存在性校验在控制器侧完成，见 AiGovernanceControllerTest）")
    void restrict_nullUserId_doesNotThrow() {
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);
        when(mapper.insert(any(AiGenerationRestriction.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.restrict(null, "r", 1L));

        verify(mapper).insert(any(AiGenerationRestriction.class));
        verify(mapper, never()).updateById(any(AiGenerationRestriction.class));
    }
}
