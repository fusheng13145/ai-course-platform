package com.aicourse.controller;

import com.aicourse.common.ApiCode;
import com.aicourse.common.ApiResponse;
import com.aicourse.dto.FavoriteDTO;
import com.aicourse.dto.NoteRequestDTO;
import com.aicourse.entity.Note;
import com.aicourse.exception.BusinessException;
import com.aicourse.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 笔记与收藏控制器
 * 所有接口需登录访问，数据按 userId 隔离（对象级授权）。
 */
@RestController
@RequestMapping("/notes")
@Tag(name = "笔记与收藏", description = "用户的课程/章节/知识点笔记与收藏管理")
@Slf4j
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /**
     * 从 Authentication 中提取当前用户ID；匿名/未登录时抛 UNAUTHORIZED。
     */
    private Long currentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "未授权，请先登录");
        }
        return (Long) auth.getPrincipal();
    }

    /** 查询当前用户自己的笔记列表，支持按课程/章节/知识点/收藏/关键词筛选 */
    @GetMapping
    @Operation(summary = "获取我的笔记列表")
    public ApiResponse<List<Note>> list(Authentication auth,
                                         @RequestParam(required = false) Long courseId,
                                         @RequestParam(required = false) Long chapterId,
                                         @RequestParam(required = false) Long knowledgePointId,
                                         @RequestParam(required = false) Boolean favorite,
                                         @RequestParam(required = false) String keyword) {
        Long userId = currentUserId(auth);
        return ApiResponse.success(noteService.listNotes(userId, courseId, chapterId, knowledgePointId, favorite, keyword));
    }

    /** 获取当前用户自己的一条笔记详情 */
    @GetMapping("/{id}")
    @Operation(summary = "获取单条笔记")
    public ApiResponse<Note> getOne(Authentication auth, @PathVariable Long id) {
        Long userId = currentUserId(auth);
        return ApiResponse.success(noteService.getOwned(userId, id));
    }

    /** 新建或更新（按内容定位）当前用户的笔记 */
    @PostMapping
    @Operation(summary = "新建或更新笔记")
    public ApiResponse<Note> create(Authentication auth, @Valid @RequestBody NoteRequestDTO dto) {
        Long userId = currentUserId(auth);
        return ApiResponse.success(noteService.createOrUpdate(userId, dto));
    }

    /** 更新当前用户自己的一条笔记 */
    @PutMapping("/{id}")
    @Operation(summary = "更新笔记")
    public ApiResponse<Note> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody NoteRequestDTO dto) {
        Long userId = currentUserId(auth);
        return ApiResponse.success(noteService.updateOwned(userId, id, dto));
    }

    /** 删除当前用户自己的一条笔记 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除笔记")
    public ApiResponse<Void> delete(Authentication auth, @PathVariable Long id) {
        Long userId = currentUserId(auth);
        noteService.deleteOwned(userId, id);
        return ApiResponse.success();
    }

    /** 切换某条笔记的收藏状态 */
    @PatchMapping("/{id}/favorite")
    @Operation(summary = "切换笔记收藏状态")
    public ApiResponse<Note> toggleFavorite(Authentication auth, @PathVariable Long id, @Valid @RequestBody FavoriteDTO body) {
        Long userId = currentUserId(auth);
        return ApiResponse.success(noteService.setFavorite(userId, id, body.getFavorite()));
    }
}
