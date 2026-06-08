package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 错题本接口控制器
 */
@RestController
@RequestMapping("/wrong-questions")
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    /**
     * 查询当前用户错题列表
     * @param auth 认证信息
     * @return 错题集合
     */
    @GetMapping
    public ApiResponse<List<WrongQuestion>> myWrongQuestions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        LambdaQueryWrapper<WrongQuestion> queryWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, userId);
        List<WrongQuestion> wrongQuestionList = wrongQuestionService.list(queryWrapper);
        return ApiResponse.success(wrongQuestionList);
    }

    /**
     * 新增错题（学生/管理员权限）
     * @param wq 错题信息
     * @param auth 认证信息
     * @return 新增错题数据
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<WrongQuestion> add(@RequestBody WrongQuestion wq, Authentication auth) {
        wq.setStudentId((Long) auth.getPrincipal());
        wrongQuestionService.save(wq);
        return ApiResponse.success("添加成功", wq);
    }

    /**
     * 修改错题（学生/管理员权限）
     * @param id 错题ID
     * @param wq 待更新错题数据
     * @return 更新后错题信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<WrongQuestion> update(@PathVariable Long id, @RequestBody WrongQuestion wq) {
        wq.setId(id);
        wrongQuestionService.updateById(wq);
        WrongQuestion updatedWq = wrongQuestionService.getById(id);
        return ApiResponse.success("更新成功", updatedWq);
    }

    /**
     * 删除错题（学生/管理员权限）
     * @param id 错题ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        wrongQuestionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 标记错题为已掌握
     * @param id 错题ID
     * @return 更新后错题信息
     */
    @PutMapping("/{id}/reviewed")
    public ApiResponse<WrongQuestion> markAsReviewed(@PathVariable Long id) {
        WrongQuestion wq = wrongQuestionService.getById(id);
        if (wq != null) {
            wq.setMastered(true);
            wrongQuestionService.updateById(wq);
        }
        return ApiResponse.success("标记已复习成功", wq);
    }

    /**
     * 查询错题统计数据
     * @param auth 认证信息
     * @return 总数、已掌握数量
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        LambdaQueryWrapper<WrongQuestion> totalWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, userId);
        long total = wrongQuestionService.count(totalWrapper);

        LambdaQueryWrapper<WrongQuestion> masteredWrapper = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, userId)
                .eq(WrongQuestion::getMastered, true);
        long mastered = wrongQuestionService.count(masteredWrapper);

        Map<String, Object> result = Map.of("total", total, "mastered", mastered);
        return ApiResponse.success(result);
    }
}