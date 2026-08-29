package com.aicourse.service.impl;

import com.aicourse.dto.NoteRequestDTO;
import com.aicourse.entity.Note;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.NoteMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NoteServiceImpl 单元测试（B 主战场新文件类，缺口 233）。
 * extends ServiceImpl<NoteMapper, Note>：手动构造 + 注入 baseMapper(mock)；
 * 实体 lambda wrapper 涉及 Note → 注册 TableInfo；
 * save/list/getById/removeById/deleteById 分别锚定；
 * ensureTable 调 baseMapper.createTableIfNotExists()（void，用 doNothing/doThrow 覆盖 try/catch 两分支）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoteServiceImplTest {

    @Mock private NoteMapper noteMapper;

    private NoteServiceImpl service;
    private Note owned;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Note.class);

        service = new NoteServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", noteMapper);

        owned = new Note();
        owned.setId(10L);
        owned.setUserId(1L);
        owned.setCourseId(5L);
        owned.setContent("我的笔记");

        when(noteMapper.selectById(anyLong())).thenReturn(owned);
        when(noteMapper.selectList(any(Wrapper.class))).thenReturn(List.of(owned));
        when(noteMapper.insert(any(Note.class))).thenReturn(1);
        when(noteMapper.updateById(any(Note.class))).thenReturn(1);
        when(noteMapper.deleteById(anyLong())).thenReturn(1);
        doNothing().when(noteMapper).createTableIfNotExists();
    }

    private NoteRequestDTO dto(Long id) {
        NoteRequestDTO d = new NoteRequestDTO();
        d.setId(id);
        d.setCourseId(5L);
        d.setChapterId(6L);
        d.setKnowledgePointId(7L);
        d.setTitle("标题");
        d.setContent("正文");
        d.setFavorite(true);
        return d;
    }

    // ===================== ensureTable =====================

    @Test
    @DisplayName("ensureTable 正常建表不抛异常")
    void ensure_ok() {
        service.ensureTable();
        verify(noteMapper).createTableIfNotExists();
    }

    @Test
    @DisplayName("ensureTable 建表异常被吞没不影响启动")
    void ensure_failSwallowed() {
        doThrow(new RuntimeException("table err")).when(noteMapper).createTableIfNotExists();
        assertDoesNotThrow(() -> service.ensureTable());
    }

    // ===================== createOrUpdate =====================

    @Test
    @DisplayName("createOrUpdate 无 id 则新建笔记")
    void create_new() {
        Note r = service.createOrUpdate(1L, dto(null));
        assertEquals(1L, r.getUserId());
        verify(noteMapper).insert(any(Note.class));
        verify(noteMapper, never()).updateById(any(Note.class));
    }

    @Test
    @DisplayName("createOrUpdate 有 id 则走更新路径")
    void create_update() {
        Note r = service.createOrUpdate(1L, dto(10L));
        assertEquals(10L, r.getId());
        verify(noteMapper, never()).insert(any(Note.class));
        verify(noteMapper).updateById(any(Note.class));
    }

    // ===================== listNotes =====================

    @Test
    @DisplayName("listNotes 全条件过滤")
    void list_allFilters() {
        List<Note> r = service.listNotes(1L, 5L, 6L, 7L, true, "关键字");
        assertEquals(1, r.size());
        verify(noteMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("listNotes 仅按用户过滤")
    void list_minimal() {
        List<Note> r = service.listNotes(1L, null, null, null, null, null);
        assertEquals(1, r.size());
    }

    // ===================== getOwned =====================

    @Test
    @DisplayName("getOwned 正常返回")
    void owned_ok() {
        Note r = service.getOwned(1L, 10L);
        assertEquals(10L, r.getId());
    }

    @Test
    @DisplayName("getOwned 不存在抛异常")
    void owned_null() {
        when(noteMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getOwned(1L, 10L));
    }

    @Test
    @DisplayName("getOwned 越权访问抛异常")
    void owned_wrongUser() {
        Note other = new Note();
        other.setId(10L);
        other.setUserId(99L);
        when(noteMapper.selectById(anyLong())).thenReturn(other);
        assertThrows(BusinessException.class, () -> service.getOwned(1L, 10L));
    }

    // ===================== deleteOwned =====================

    @Test
    @DisplayName("deleteOwned 正常删除")
    void delete_ok() {
        service.deleteOwned(1L, 10L);
        verify(noteMapper).deleteById(10L);
    }

    @Test
    @DisplayName("deleteOwned 不存在抛异常")
    void delete_null() {
        when(noteMapper.selectById(anyLong())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteOwned(1L, 10L));
    }

    // ===================== setFavorite =====================

    @Test
    @DisplayName("setFavorite true 置 1")
    void fav_true() {
        Note r = service.setFavorite(1L, 10L, true);
        assertEquals(1, r.getIsFavorite());
        verify(noteMapper).updateById(any(Note.class));
    }

    @Test
    @DisplayName("setFavorite false 置 0")
    void fav_false() {
        Note r = service.setFavorite(1L, 10L, false);
        assertEquals(0, r.getIsFavorite());
    }

    @Test
    @DisplayName("setFavorite null 置 0")
    void fav_null() {
        Note r = service.setFavorite(1L, 10L, null);
        assertEquals(0, r.getIsFavorite());
    }
}
