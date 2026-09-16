package com.aicourse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期 TLS 姿态守卫（B3，2026-09-16 上线前全检）。
 *
 * <p><b>背景</b>：{@code app.security.require-https=true} 时，Spring Security 经
 * {@code requiresChannel().requiresSecure()} 把一切 http 请求 302 到 https。仅当上游存在
 * <b>TLS 终止反代</b>且回传 {@code X-Forwarded-Proto: https}（本服务已置
 * {@code server.forward-headers-strategy=framework}）时，{@code /api/**} 才可正常访问。
 * 若在<b>纯明文栈</b>（如 {@code docker compose --profile full} 默认单机形态：nginx 仅 listen 80、
 * 无证书卷）上误置 true，则全站 API 会 302 到不存在的 443 而整体失效。</p>
 *
 * <p><b>为何只告警不 fail-fast</b>：启动期无法探测上游是否存在 TLS 终止，而
 * 「require-https=true」在其<b>合法</b>场景（TLS 反代之后）与<b>误配</b>场景（明文栈）下取值相同，
 * 贸然 fail-fast 会误杀合法生产部署、并令默认安全姿态（默认 true）下的 {@code @SpringBootTest}
 * 上下文无法启动。故此处以<b>显著 WARN</b> 使运维在日志中无法忽视该姿态，并给出可执行的自救开关，
 * 避免「静默 302、无从排查」。</p>
 */
@Slf4j
@Component
public class TlsPostureValidator implements ApplicationRunner {

    private final boolean requireHttps;
    private final Environment environment;

    public TlsPostureValidator(@Value("${app.security.require-https:true}") boolean requireHttps,
                               Environment environment) {
        this.requireHttps = requireHttps;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!requireHttps) {
            return;
        }
        String profiles = environment.getActiveProfiles().length == 0
                ? "(default)" : String.join(",", environment.getActiveProfiles());
        log.warn("[TLS 姿态] app.security.require-https=true：Spring Security 将把一切 http 请求 302 到 https。"
                + " 仅当上游存在 TLS 终止反代且回传 X-Forwarded-Proto: https 时，/api/** 才能正常访问；"
                + " 明文 HTTP 部署（如 docker compose --profile full 默认单机栈、nginx 仅 80）必须显式置 "
                + "REQUIRE_HTTPS=false 与 COOKIE_SECURE=false，否则全站 API 将因 302 到不存在的 443 而失效。"
                + " 当前激活 profile：{}", profiles);
    }
}
