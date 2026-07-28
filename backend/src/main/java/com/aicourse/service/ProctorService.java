package com.aicourse.service;

import com.aicourse.entity.ProctorEvent;
import com.aicourse.entity.ProctorSession;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 在线监考（考试防作弊）服务
 */
public interface ProctorService extends IService<ProctorSession> {

    /**
     * 考生开始监考会话（同一考试同一考生仅一个进行中会话）
     */
    ProctorSession startSession(Long examSessionId, Long studentId);

    /**
     * 上报一条风险事件，并实时累加会话风险评分
     */
    ProctorEvent logEvent(Long sessionId, String type, String severity, String detail, String evidenceUrl);

    /**
     * 结束监考会话
     */
    ProctorSession endSession(Long sessionId);

    /**
     * 查询某会话的全部风险事件
     */
    List<ProctorEvent> listEvents(Long sessionId);

    /**
     * 获取考生在某考试下的当前监考会话
     */
    ProctorSession getStudentSession(Long examSessionId, Long studentId);

    /**
     * 教师视角：某场考试的全部考生风险汇总
     */
    List<Map<String, Object>> riskSummary(Long examSessionId);

    /**
     * 读取当前监考风险判定阈值（管理员可调控）：
     * 返回 high/medium 阈值与 low/medium/high 事件权重，缺失配置时回退默认值。
     */
    Map<String, Object> getThresholds();

    /**
     * 更新监考风险判定阈值（管理员调控，持久化至 system_configs）
     */
    void updateThresholds(Map<String, Integer> thresholds);

    /**
     * 教师/管理员对单条违规事件进行复核：确认违规(REVIEWED) 或 豁免(EXEMPTED)
     *
     * @param eventId   事件ID
     * @param status    复核结论：ProctorEvent.STATUS_REVIEWED / STATUS_EXEMPTED
     * @param reviewNote 复核备注
     * @param reviewerId 复核人ID
     * @return 更新后的事件
     */
    ProctorEvent reviewEvent(Long eventId, String status, String reviewNote, Long reviewerId);

    /**
     * 查询单条违规事件（含复核状态），供治理页复核面板使用
     */
    ProctorEvent getEvent(Long eventId);
}
