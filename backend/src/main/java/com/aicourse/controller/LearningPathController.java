package com.aicourse.controller;

import com.aicourse.common.ApiResponse;
import com.aicourse.entity.*;
import com.aicourse.service.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/learning-paths")
public class LearningPathController {
    private final LearningPathService learningPathService;
    private final LearningPathNodeService learningPathNodeService;
    private final CourseEnrollmentService enrollmentService;

    public LearningPathController(LearningPathService learningPathService,
                                  LearningPathNodeService learningPathNodeService,
                                  CourseEnrollmentService enrollmentService) {
        this.learningPathService = learningPathService;
        this.learningPathNodeService = learningPathNodeService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public ApiResponse<List<LearningPath>> myPaths(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ApiResponse.success(learningPathService.list(
            new LambdaQueryWrapper<LearningPath>().eq(LearningPath::getStudentId, userId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<LearningPath> getById(@PathVariable Long id) {
        return ApiResponse.success(learningPathService.getById(id));
    }

    @PostMapping
    public ApiResponse<LearningPath> create(@RequestBody LearningPath path, Authentication auth) {
        path.setStudentId((Long) auth.getPrincipal());
        learningPathService.save(path);
        return ApiResponse.success("鍒涘缓鎴愬姛", path);
    }

    @PutMapping("/{id}")
    public ApiResponse<LearningPath> update(@PathVariable Long id, @RequestBody LearningPath path) {
        path.setId(id);
        learningPathService.updateById(path);
        return ApiResponse.success("鏇存柊鎴愬姛", learningPathService.getById(id));
    }

    @GetMapping("/{id}/nodes")
    public ApiResponse<List<LearningPathNode>> getNodes(@PathVariable Long id) {
        return ApiResponse.success(learningPathNodeService.list(
            new LambdaQueryWrapper<LearningPathNode>().eq(LearningPathNode::getLearningPathId, id)));
    }

    @PostMapping("/{id}/nodes")
    public ApiResponse<LearningPathNode> addNode(@PathVariable Long id, @RequestBody LearningPathNode node) {
        node.setLearningPathId(id);
        learningPathNodeService.save(node);
        return ApiResponse.success("娣诲姞鎴愬姛", node);
    }

    @PutMapping("/nodes/{nodeId}")
    public ApiResponse<LearningPathNode> updateNode(@PathVariable Long nodeId, @RequestBody LearningPathNode node) {
        node.setId(nodeId);
        learningPathNodeService.updateById(node);
        return ApiResponse.success("鏇存柊鎴愬姛", learningPathNodeService.getById(nodeId));
    }

    @GetMapping("/generate")
    public ApiResponse<List<LearningPathNode>> generate(@RequestParam Long courseId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // Simple auto-generation logic
        List<LearningPathNode> nodes = new ArrayList<>();
        String[] topics = {"鍩虹姒傚康", "鏍稿績鍘熺悊", "杩涢樁搴旂敤", "缁煎悎瀹炶返"};
        for (int i = 0; i < topics.length; i++) {
            LearningPathNode node = new LearningPathNode();
            node.setTitle(topics[i]);
            node.setNodeType("study");
            node.setStatus("PENDING");
            node.setOrderIndex(i);
            node.setEstimatedTime(60);
            nodes.add(node);
        }
        return ApiResponse.success(nodes);
    }
}
