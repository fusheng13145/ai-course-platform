package com.aicourse.service.component.learning;

import com.aicourse.entity.LearningPath;
import com.aicourse.entity.LearningPathNode;
import com.aicourse.exception.BusinessException;
import com.aicourse.mapper.LearningPathMapper;
import com.aicourse.service.LearningPathNodeService;
import com.aicourse.vo.LearningPathDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 学习路径查询组件：每日任务 / 完整详情 / 路径列表。
 * <p>拆分自原 LearningPathServiceImpl（2026-08-29）。</p>
 */
@Component
public class LearningPathQueryComponent {

    private static final Logger log = LoggerFactory.getLogger(LearningPathQueryComponent.class);

    private final LearningPathNodeService nodeService;
    private final LearningPathMapper learningPathMapper;
    private final ObjectMapper objectMapper;

    public LearningPathQueryComponent(LearningPathNodeService nodeService,
                                      LearningPathMapper learningPathMapper,
                                      ObjectMapper objectMapper) {
        this.nodeService = nodeService;
        this.learningPathMapper = learningPathMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取每日任务清单（从dailyTasks JSON解析）
     */
    public List<Map<String, Object>> getDailyTasks(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null || path.getDailyTasks() == null || path.getDailyTasks().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(path.getDailyTasks(),
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("查询学习路径失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取学习路径完整详情（含节点列表及统计信息）
     * 解析stages和dailyTasks的JSON字段并返回结构化数据
     */
    public LearningPathDetailVO getPathDetail(Long pathId) {
        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null) {
            throw new BusinessException("学习路径不存在: " + pathId);
        }

        // 查询所有节点（按orderIndex排序）
        List<LearningPathNode> nodes = nodeService.getPathNodes(pathId);

        // 统计各状态节点数
        Map<String, Long> statusStats = nodeService.getNodeStatusStats(pathId);

        // 解析stages JSON字段为结构化数据（stagesParsed 与 stagesRaw 互斥，null 即键缺失）
        List<Map<String, Object>> stagesParsed = null;
        String stagesRaw = null;
        if (path.getStages() != null && !path.getStages().isEmpty()) {
            try {
                stagesParsed = objectMapper.readValue(path.getStages(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.error("查询学习路径失败: {}", e.getMessage(), e);
                // JSON解析失败时返回原始字符串
                stagesRaw = path.getStages();
            }
        }

        // 解析planDetail JSON字段（planDetailParsed 与 planDetailRaw 互斥，null 即键缺失）
        Map<String, Object> planDetailParsed = null;
        String planDetailRaw = null;
        if (path.getPlanDetail() != null && !path.getPlanDetail().isEmpty()) {
            try {
                planDetailParsed = objectMapper.readValue(path.getPlanDetail(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("查询学习路径失败: {}", e.getMessage(), e);
                planDetailRaw = path.getPlanDetail();
            }
        }

        return new LearningPathDetailVO(
                path, nodes, statusStats, nodes.size(),
                stagesParsed, stagesRaw, planDetailParsed, planDetailRaw);
    }

    /**
     * 获取学生的学习路径列表（指定课程下）
     */
    public List<LearningPath> getStudentPaths(Long studentId, Long courseId) {
        LambdaQueryWrapper<LearningPath> wrapper = new LambdaQueryWrapper<LearningPath>()
                .eq(LearningPath::getStudentId, studentId)
                .eq(LearningPath::getCourseId, courseId)
                .orderByDesc(LearningPath::getCreatedAt);
        return learningPathMapper.selectList(wrapper);
    }
}
