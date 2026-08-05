package com.aicourse.service;

import com.aicourse.dto.NoteRequestDTO;
import com.aicourse.entity.Note;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 笔记与收藏服务接口
 */
public interface NoteService extends IService<Note> {

    /**
     * 创建或更新笔记（按 owner 校验归属）
     * @param userId 当前用户ID
     * @param dto    笔记请求数据
     * @return 持久化后的笔记
     */
    Note createOrUpdate(Long userId, NoteRequestDTO dto);

    /**
     * 查询当前用户的笔记列表，支持按课程/章节/知识点/收藏/关键词过滤
     */
    List<Note> listNotes(Long userId, Long courseId, Long chapterId, Long knowledgePointId, Boolean favorite, String keyword);

    /**
     * 获取单条笔记（校验归属，不存在或无权限抛 NOT_FOUND）
     */
    Note getOwned(Long userId, Long id);

    /**
     * 更新单条笔记（校验归属）
     */
    Note updateOwned(Long userId, Long id, NoteRequestDTO dto);

    /**
     * 删除单条笔记（校验归属）
     */
    void deleteOwned(Long userId, Long id);

    /**
     * 切换收藏状态（校验归属）
     * @param favorite true-收藏，false-取消收藏
     */
    Note setFavorite(Long userId, Long id, Boolean favorite);
}
