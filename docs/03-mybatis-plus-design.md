# MyBatis-Plus 设计说明

## 一、依赖配置

后端使用 Spring Boot 3.2.0 + Java 17，核心依赖如下：

```xml
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
  <version>3.5.7</version>
</dependency>
```

明确不加入 `spring-boot-starter-data-jpa`，不创建 `JpaRepository`。

## 二、包结构

```text
com.aicourse
  config          全局配置、Security、Redis、Web、MP分页插件
  controller      REST接口
  dto             请求/响应DTO
  entity          MP实体
  enums           业务枚举
  exception       统一异常
  mapper          BaseMapper接口
  service         IService接口
  service.impl    ServiceImpl实现
  security        JWT过滤器和认证入口
  util            JWT、IP、类型转换、相似度等工具
```

## 三、实体设计规则

| 表 | 实体 | 主键策略 | 关键说明 |
|---|---|---|---|
| users | User | AUTO | 角色、账号状态、学生/教师扩展字段、登录锁定 |
| user_sessions | UserSession | AUTO | JWT会话、设备、IP、过期时间 |
| login_logs | LoginLog | AUTO | 登录审计 |
| verification_codes | VerificationCode | AUTO | 注册/找回密码验证码，生产建议Redis化 |
| classrooms | Classroom | AUTO | 班级、专业、年级、班主任 |
| courses | Course | AUTO | 课程、审核、选课类型、分类 |
| course_enrollments | CourseEnrollment | AUTO | 唯一键 student_id + course_id |
| course_chapters | CourseChapter | AUTO | 多级章节 |
| resources | Resource | AUTO | 教学资源 |
| resource_versions | ResourceVersion | AUTO | 资源版本 |
| tasks | Task | AUTO | 作业/任务 |
| submissions | Submission | AUTO | 提交与评分 |
| plagiarism_checks | PlagiarismCheck | AUTO | 查重记录 |
| plagiarism_matches | PlagiarismMatch | AUTO | 查重匹配 |
| reminders | Reminder | AUTO | 催交提醒 |
| exam_papers | ExamPaper | AUTO | 试卷 |
| exam_questions | ExamQuestion | AUTO | 试卷-题目关联 |
| exam_sessions | ExamSession | AUTO | 考试作答会话 |
| questions | Question | AUTO | 题库 |
| knowledge_points | KnowledgePoint | AUTO | 知识点 |
| knowledge_point_relations | KnowledgePointRelation | AUTO | 知识点关系 |
| chapter_knowledge_points | ChapterKnowledgePoint | AUTO | 章节-知识点关联 |
| wrong_questions | WrongQuestion | AUTO | 错题本 |
| learning_paths | LearningPath | AUTO | 学习路径 |
| learning_path_nodes | LearningPathNode | AUTO | 学习路径节点 |
| learning_records | LearningRecord | AUTO | 学习行为 |
| video_learning_records | VideoLearningRecord | AUTO | 视频学习汇总 |
| video_watch_sessions | VideoWatchSession | AUTO | 视频观看会话 |
| notifications | Notification | AUTO | 通知 |
| role_permissions | RolePermission | AUTO | RBAC权限 |
| system_configs | SystemConfig | AUTO | 系统配置 |
| file_upload_sessions | FileUploadSession | AUTO | 分片上传会话 |

## 四、典型MP写法

```java
@TableName("courses")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("teacher_id")
    private Long teacherId;
}
```

```java
public interface CourseMapper extends BaseMapper<Course> {
}
```

```java
public Page<Course> pageActiveCourses(long current, long size, String keyword) {
    LambdaQueryWrapper<Course> wrapper = Wrappers.lambdaQuery(Course.class)
        .eq(Course::getIsActive, true)
        .like(StringUtils.hasText(keyword), Course::getName, keyword)
        .orderByDesc(Course::getCreatedAt);
    return page(Page.of(current, size), wrapper);
}
```

## 五、迁移重点

- JPA 派生查询统一改为 `LambdaQueryWrapper` 条件组合。
- JPA `@Query` 聚合查询改为 Mapper XML 或 `@Select`。
- JPA 关联对象不保留为实体字段，Service 层按 ID 查询并组装 DTO。
- 原 `Pageable` 替换为 MP `Page`。
- 原 `Optional<T>` 查询风格改为 `getById` + 空值判断，或 mapper `selectOne`。
