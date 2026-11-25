package com.aicourse.architecture;

import com.aicourse.entity.User;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 架构守护：在编译期校验包/层依赖边界，固化阶段一的模块化重构基线。
 * 规则聚焦于“解耦”——禁止下层/平层对上层或外部存储的直接依赖，
 * 防止控制器穿透到 mapper、实体被业务层反向污染等耦合回归。
 */
@AnalyzeClasses(packages = "com.aicourse", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** 控制器不得直接依赖 mapper（必须经 service 层） */
    @ArchTest
    static final ArchRule controllerShouldNotDependOnMapper =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..");

    /** service 层不得反向依赖 controller 层 */
    @ArchTest
    static final ArchRule serviceShouldNotDependOnController =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..");

    /** 实体不得依赖 service / controller / mapper（实体须为纯数据载体） */
    @ArchTest
    static final ArchRule entityShouldNotDependOnServiceOrControllerOrMapper =
            noClasses().that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..mapper..");

    /** 工具类不得依赖 service / controller / mapper（保持无状态、可独立测试） */
    @ArchTest
    static final ArchRule utilShouldNotDependOnServiceOrControllerOrMapper =
            noClasses().that().resideInAPackage("..util..")
                    .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..mapper..");

    /** 配置类不得依赖 controller（配置只装配基础设施，不耦合业务入口） */
    @ArchTest
    static final ArchRule configShouldNotDependOnController =
            noClasses().that().resideInAPackage("..config..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..");

    /** 安全组件不得依赖 controller（鉴权与作用域判定须可脱离 Web 入口独立测试） */
    @ArchTest
    static final ArchRule securityShouldNotDependOnController =
            noClasses().that().resideInAPackage("..security..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..");

    /** 控制器不得直接读写 {@code User.adminScope} 字段：作用域判定必须统一经 AdminScopeGuard，
     * 防止各端点各自解释作用域语义（B-12-P-08 WP2）。 */
    @ArchTest
    static final ArchRule controllerShouldNotAccessAdminScopeField =
            noClasses().that().resideInAPackage("..controller..")
                    .should().accessField(User.class, "adminScope");

    /** service 实现类不得依赖其他 service 实现类（服务间只允许依赖接口，保持解耦）。
     * 源/目标均限定精确包 com.aicourse.service.impl + *Impl 结尾 + 非内部类；
     * 目标条件用 JavaClass.Predicates.and 组合（andShould 实测为 OR 语义，不可用于组合条件）：
     * 排除 MyBatis-Plus 基类（包不符）、子包协作者（impl.exam）、匿名类自引用（$ 不以 Impl 结尾）。 */
    @ArchTest
    static final ArchRule serviceImplShouldNotDependOnOtherServiceImpl =
            noClasses().that().resideInAPackage("com.aicourse.service.impl")
                    .and().haveSimpleNameNotContaining("$")
                    .and().haveSimpleNameEndingWith("Impl")
                    .should().dependOnClassesThat(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates
                                    .resideInAPackage("com.aicourse.service.impl")
                                    .and(com.tngtech.archunit.core.domain.JavaClass.Predicates
                                            .simpleNameEndingWith("Impl")));

    /** 安全层不得直接依赖 service.impl：鉴权/配额等横切能力只能依赖 service 接口或
     * service.component 下的公开协作者，避免内部实现泄漏到安全链路。 */
    @ArchTest
    static final ArchRule securityShouldNotDependOnServiceImpl =
            noClasses().that().resideInAPackage("..security..")
                    .should().dependOnClassesThat().resideInAPackage("com.aicourse.service.impl");

    /** service.component 不得直接依赖 service.impl：组件是可复用的业务协作者，
     * 反向依赖实现包会破坏「实现包只做装配」的边界。 */
    @ArchTest
    static final ArchRule serviceComponentShouldNotDependOnServiceImpl =
            noClasses().that().resideInAPackage("..service.component..")
                    .should().dependOnClassesThat().resideInAPackage("com.aicourse.service.impl");

    // ==================== 阶段3：controller → application → domain 分层契约 ====================

    /** 控制器不得依赖 service 实现包（只允许依赖接口）。 */
    @ArchTest
    static final ArchRule controllerShouldNotDependOnServiceImpl =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("com.aicourse.service.impl");

    /** 控制器不得直接依赖服务组件：组件是领域内部协作者，控制器只能接触接口/公共契约类型。
     * （{@code AiCallContext} 已迁至 {@code common} 以解除唯一一处例外） */
    @ArchTest
    static final ArchRule controllerShouldNotDependOnServiceComponent =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..service.component..");

    /** 控制器不得直接依赖下游客户端（rag/kg/deepgraph/support）：HTTP 客户端只允许经
     * service/application 调用。{@code com.aicourse.integration} 根包的契约常量除外。 */
    @ArchTest
    static final ArchRule controllerShouldNotDependOnDownstreamClients =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..integration.rag..", "..integration.kg..",
                            "..integration.deepgraph..", "..integration.support..");

    /** 事务边界不得落在控制器上：{@code @Transactional} 只能出现在 application 层或域服务。
     * 控制器上挂事务会让「换个调用顺序」悄悄改变一致性语义（阶段3 迁移用户管理时移除了唯一一处）。 */
    @ArchTest
    static final ArchRule controllerMethodsShouldNotBeTransactional =
            noMethods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                    .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);

    /** application 层不得反向依赖 web 层（controller）或持久化细节（mapper）。
     * 应用层可以用查询 DSL，但不得自己拿 mapper。 */
    @ArchTest
    static final ArchRule applicationShouldStayWebAndMapperFree =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..", "..mapper..", "jakarta.servlet..");

    /** application 层不得携带 Web 类型（MultipartFile / HttpServletRequest / ResponseEntity）：
     * 这类类型出现在应用层意味着「用例」被写成了「端点」。 */
    @ArchTest
    static final ArchRule applicationShouldNotUseWebTypes =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web.multipart..", "jakarta.servlet.http..")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName(
                            "org.springframework.http.ResponseEntity");

    /** 控制器不得持有异步执行器：线程池注入到 Web 层会让「副作用」绑死在某个 HTTP 入口上
     * （3E-02 前 {@code SubmissionController} 独占 {@code aiGradingExecutor}，学生入口因此没有预批改）。 */
    @ArchTest
    static final ArchRule controllerShouldNotOwnAsyncExecutors =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.Executor")
                    .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.ExecutorService");

    /** 作业提交用例层不得依赖领域内部组件：它只编排门面服务与授权组件。
     * 一旦这一层开始直接调 {@code SubmissionWriteComponent}，3C-3 提交锁就脱离了门面事务边界。 */
    @ArchTest
    static final ArchRule submissionApplicationShouldNotTouchDomainComponents =
            noClasses().that().resideInAPackage("..application.submission..")
                    .should().dependOnClassesThat().resideInAPackage("..service.component..");

    /** 作业提交用例层不得自带事务：{@code SubmissionSubmitGuard} 的锁释放挂在门面事务的同步回调上，
     * 用例层再开一层事务会把锁的释放点往后挪（同一 (taskId, studentId) 的并发窗口被拉长）。 */
    @ArchTest
    static final ArchRule submissionApplicationShouldStayNonTransactional =
            noMethods().that().areDeclaredInClassesThat().resideInAPackage("..application.submission..")
                    .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);

    /** 试卷域控制器不得再拼查询 DSL：3E-03A 已把列表条件、学生可见性过滤、教学班范围与
     * {@code courseName} 回填全部下移到 {@code application.exam}，这里只是把「别再写回来」固定住。 */
    @ArchTest
    static final ArchRule examPaperControllersShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleNameStartingWith("ExamPaper")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 考试会话控制器不得再拼查询 DSL：3E-03B 已把阅卷台名单查询、监考列表课程过滤与
     * 全部对象级授权下移到 {@code application.exam}。分两条规则而不是合并成前缀判断，
     * 是为了失败时报出具体是哪个域又把 wrapper 写回来了。 */
    @ArchTest
    static final ArchRule examSessionControllersShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleNameStartingWith("ExamSession")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 题库域控制器不得再拼查询 DSL：3E-03C 已把列表筛选、教师可见范围（本人课程 ∪ 本人创建）
     * 与导出取数下移到 {@code application.question}。按精确类名而不是前缀限定，
     * 是为了不把仍在使用 wrapper 的 {@code QuestionAiController} 一并卷入（另批处理）。 */
    @ArchTest
    static final ArchRule questionControllersShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("QuestionController")
                    .or().haveSimpleName("TeacherQuestionController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 作业任务控制器不得再拼查询 DSL：3E-04A 已把列表条件、学生可见性、教学班范围追加
     * 与 8 处「取任务 + 课程管理权」守卫下移到 {@code application.course}。 */
    @ArchTest
    static final ArchRule taskControllerShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("TaskController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 课程控制器不得再拼查询 DSL：3E-04B 已把列表条件、学院范围过滤、
     * 归属/学院两级守卫与分页转 VO 下移到 {@code application.course}。 */
    @ArchTest
    static final ArchRule courseControllerShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("CourseController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 管理员统计控制器不得再拼查询 DSL：3E-05A 已把 7 处「校级 / 院系」双口径分支、
     * 20 处 wrapper 与报表组装下移到 {@code application.admin}。 */
    @ArchTest
    static final ArchRule adminStatisticsControllerShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("AdminStatisticsController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 学生端控制器不得再拼查询 DSL：3E-05B 已把「有效选课」口径、待办判定、详情/教学班
     * 与两处分页转 VO 下移到 {@code application.student}。 */
    @ArchTest
    static final ArchRule studentControllerShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("StudentController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 知识点控制器不得再拼查询 DSL：3E-05C 已把可见范围过滤、分页回显、图谱快照版本
     * 与 CRUD 守卫编排下移到 {@code application.knowledge}。 */
    @ArchTest
    static final ArchRule knowledgeControllerShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("KnowledgeController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 教师导入控制器不得再拼查询 DSL：3E-05C 已把四条导入路径的逐条容错、重复判定
     * 与两遍写入（父级回填）下移到 {@code application.teacher}。 */
    @ArchTest
    static final ArchRule teacherImportControllerShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("TeacherImportController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");

    /** 操作日志 / 知识点检索 / 选课管理三个控制器不得再拼查询 DSL：3E-05D 已把筛选条件、
     * 院系收口、关键字学生ID集解析、审核状态机与概览统计下移到各自 application 层。 */
    @ArchTest
    static final ArchRule auditAndEnrollmentControllersShouldNotUseQueryDsl =
            noClasses().that().resideInAPackage("..controller..")
                    .and().haveSimpleName("OperationLogController")
                    .or().haveSimpleName("KnowledgeSearchController")
                    .or().haveSimpleName("CourseEnrollmentController")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper");
}
