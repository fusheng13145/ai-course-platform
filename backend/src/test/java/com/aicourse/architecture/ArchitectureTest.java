package com.aicourse.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
}
