package com.aicourse.service;

import com.aicourse.entity.Announcement;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 公告服务（B-12／P-08 WP8，V16）。
 * <p>发布（互斥目标 + 按受众扇出到 notifications）、按用户可见分页、详情访问控制。
 * 判据：拆包方案 WP8 验收 1–4。</p>
 */
public interface AnnouncementService extends com.baomidou.mybatisplus.extension.service.IService<Announcement> {

    /**
     * 发布公告并按受众扇出站内通知。
     *
     * @param courseId        课程目标（与 teachingClassId / adminClassId 互斥，可空）
     * @param teachingClassId 教学班目标（互斥，可空）
     * @param adminClassId    行政班目标（互斥，可空）
     * @param title           标题（非空）
     * @param content         正文（非空）
     * @param publisherId     发布人（控制器已做过对象级 ACL；本方法做目标唯一性兜底）
     * @param publisherRole   发布人角色（TEACHER / ADVISOR / ADMIN）
     * @return 落库后的公告（含扇出使用的主键）
     */
    Announcement publish(Long courseId, Long teachingClassId, Long adminClassId,
                         String title, String content, Long publisherId, String publisherRole);

    /**
     * 当前用户可见的公告分页（本人课程 / 教学班 / 行政班三个目标维度的并集）。
     */
    IPage<Announcement> pageVisible(long page, long size, Long userId);

    /**
     * 读取单条公告，非受众用户拒绝（拆包方案 WP8 验收 2：列表与详情均不可见）。
     *
     * @throws com.aicourse.exception.BusinessException 公告不存在 → 404；非受众 → 403
     */
    Announcement getVisible(Long id, Long userId);
}
