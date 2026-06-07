package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.WrongQuestion;
import com.aicourse.service.WrongQuestionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wrong-questions")
public class WrongQuestionController {
    private final WrongQuestionService wrongQuestionService;

    public WrongQuestionController(WrongQuestionService wrongQuestionService) {
        this.wrongQuestionService = wrongQuestionService;
    }

    @GetMapping
    public ApiResponse<List<WrongQuestion>> myWrongQuestions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(wrongQuestionService.list(
            new LambdaQueryWrapper<WrongQuestion>().eq(WrongQuestion::getStudentId, userId)));
    }

    @PostMapping
    public ApiResponse<WrongQuestion> add(@RequestBody WrongQuestion wq, Authentication auth) {
        wq.setStudentId((Long) auth.getPrincipal());
        wrongQuestionService.save(wq);
        return ApiResponse.success("添加成功", wq);
    }

    @PutMapping("/{id}")
    public ApiResponse<WrongQuestion> update(@PathVariable Long id, @RequestBody WrongQuestion wq) {
        wq.setId(id);
        wrongQuestionService.updateById(wq);
        return ApiResponse.success("更新成功", wrongQuestionService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        wrongQuestionService.removeById(id);
        return ApiResponse.success("删除成功", null);
    }
}
