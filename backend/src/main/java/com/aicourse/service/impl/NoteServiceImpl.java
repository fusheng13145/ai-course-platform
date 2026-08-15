package com.aicourse.service.impl;


import com.aicourse.common.ApiCode;
import com.aicourse.dto.NoteRequestDTO;
import com.aicourse.entity.Note;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.NoteMapper;
import com.aicourse.service.NoteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记与收藏服务实现
 */
@Service
@Slf4j
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements NoteService {


    /**
     * 自愈合：应用启动时确保 notes 表存在，兼容未重跑 index.sql 的已运行实例。
     */
    @PostConstruct
    public void ensureTable() {
        try {
            baseMapper.createTableIfNotExists();
            log.info("notes 表自愈合建表完成（已存在则跳过）");
        } catch (Exception e) {
            // 表已存在或数据库暂不可用时不应阻断启动
            log.warn("notes 表自愈合建表失败（若表已存在可忽略）：{}", e.getMessage());
        }
    }


    /** 创建笔记 */
    @Override
    public Note createOrUpdate(Long userId, NoteRequestDTO dto) {
        log.info("创建笔记");

        if (dto.getId() != null) {
            return updateOwned(userId, dto.getId(), dto);
        }
        Note note = new Note();
        applyDto(note, dto);
        note.setUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        this.save(note);
        return note;
    }


    /** 查询笔记 */
    @Override
    public List<Note> listNotes(Long userId, Long courseId, Long chapterId,
                                 Long knowledgePointId, Boolean favorite, String keyword) {
        LambdaQueryWrapper<Note> q = new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId);
        if (courseId != null) {
            q.eq(Note::getCourseId, courseId);
        }
        if (chapterId != null) {
            q.eq(Note::getChapterId, chapterId);
        }
        if (knowledgePointId != null) {
            q.eq(Note::getKnowledgePointId, knowledgePointId);
        }
        if (favorite != null && favorite) {
            q.eq(Note::getIsFavorite, 1);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(Note::getTitle, kw).or().like(Note::getContent, kw));
        }
        q.orderByDesc(Note::getUpdatedAt);
        return this.list(q);
    }


    /** 查询笔记 */
    @Override
    public Note getOwned(Long userId, Long id) {
        Note note = this.getById(id);
        if (note == null || !userId.equals(note.getUserId())) {
            throw new BusinessException(ApiCode.NOT_FOUND, "笔记不存在或无权访问");
        }
        return note;
    }


    /** 更新笔记 */
    @Override
    public Note updateOwned(Long userId, Long id, NoteRequestDTO dto) {
        log.info("更新笔记");

        Note existing = getOwned(userId, id);
        applyDto(existing, dto);
        existing.setUpdatedAt(LocalDateTime.now());
        this.updateById(existing);
        return existing;
    }


    /** 删除笔记 */
    @Override
    public void deleteOwned(Long userId, Long id) {
        log.info("删除笔记");

        getOwned(userId, id);
        this.removeById(id);
    }


    /** 设置笔记 */
    @Override
    public Note setFavorite(Long userId, Long id, Boolean favorite) {
        log.info("设置笔记");

        Note existing = getOwned(userId, id);
        existing.setIsFavorite(favorite != null && favorite ? 1 : 0);
        existing.setUpdatedAt(LocalDateTime.now());
        this.updateById(existing);
        return existing;
    }

    private void applyDto(Note note, NoteRequestDTO dto) {
        note.setCourseId(dto.getCourseId());
        note.setChapterId(dto.getChapterId());
        note.setKnowledgePointId(dto.getKnowledgePointId());
        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        note.setIsFavorite(dto.getFavorite() != null && dto.getFavorite() ? 1 : 0);
    }
}
