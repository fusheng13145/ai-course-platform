package com.aicourse.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LLM 供应商端点可配性的静态守卫（DR-84）。
 *
 * <p><b>为什么需要这条守卫</b>：三家的 {@code base-url} 与 {@code model} 在
 * {@code application.yaml} 里是<b>写死的字面量</b>，而 {@code LLMProviderConfig} 与各 Provider
 * 早已支持 {@code @Value("${ai.deepseek.base-url:...}")} 注入——于是<b>非 DeepSeek 域名的
 * OpenAI 兼容 Key 在本仓无处可填</b>：把 Key 写进 {@code DEEPSEEK_API_KEY} 后请求仍打到
 * {@code api.deepseek.com}，必然 401，而现象与"Key 无效"完全同形，运维只能得出"密钥是坏的"这个错误结论。
 * 实测（2026-10-18）：一枚 50 字符、{@code sk-} 开头但非 DeepSeek 形态（{@code sk-}+32hex）的
 * OpenAI 兼容 Key 即属此类。</p>
 *
 * <p>口径：端点/模型可被环境变量覆盖，<b>默认值必须保持现有行为不变</b>（不填即仍是 DeepSeek 官方）；
 * 且键位要一路通到编排层——只加 yml 占位符而 {@code docker-compose.yml} 不注入，就是 DR-73 的复发。</p>
 */
class LlmProviderEndpointConfigGuardTest {

    private static final Path RES = Path.of("src/main/resources");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[A-Z0-9_]+:[^}]*}");

    private static String read(String name) throws IOException {
        return Files.readString(RES.resolve(name), StandardCharsets.UTF_8);
    }



    @Test
    @DisplayName("供应商的 base-url / model 一律必须带 env 占位符（默认值不变）")
    void providerEndpointsAreEnvOverridable() throws IOException {
        String yaml = read("application.yaml");
        // 取 ai: 段（从 "provider:" 到下一个顶层键），逐行核查——只查第一条会让 openai/qwen 白过
        int from = yaml.indexOf("\n  provider: ${AI_PROVIDER:");
        assertThat(from).as("ai.provider 键本身不见了").isGreaterThan(0);
        int to = yaml.indexOf("\n  # ---------- AI 用量配额", from);
        String block = yaml.substring(from, to > 0 ? to : from + 1200);
        List<String> urls = List.of(block.split("\n")).stream()
                .filter(l -> l.strip().startsWith("base-url:")).toList();
        List<String> models = List.of(block.split("\n")).stream()
                .filter(l -> l.strip().startsWith("model:")).toList();
        assertThat(urls).as("ai 段应含三家供应商的 base-url").hasSizeGreaterThanOrEqualTo(3);
        assertThat(models).as("ai 段应含三家供应商的 model").hasSizeGreaterThanOrEqualTo(3);
        for (String l : urls) {
            assertThat(l).as("base-url 写死字面量＝非 DeepSeek 的网关 Key 无处可填").containsPattern(PLACEHOLDER);
        }
        for (String l : models) {
            assertThat(l).as("model 写死＝换供应商还得改代码").containsPattern(PLACEHOLDER);
        }
    }

    @Test
    @DisplayName(".env.example 必须给出端点与供应商选择的键行（否则键位形同不存在）")
    void exampleDocumentsEndpointKeys() throws IOException {
        String example = Files.readString(
                Path.of("..", ".env.example"), StandardCharsets.UTF_8);
        assertThat(example).as("缺 AI_PROVIDER 键行").containsPattern("(?m)^AI_PROVIDER=");
        assertThat(example).as("缺 DEEPSEEK_BASE_URL 键行").containsPattern("(?m)^DEEPSEEK_BASE_URL=");
    }

    @Test
    @DisplayName("compose 必须把这些键注入 backend（yml 有落点、编排到不了＝DR-73 同族复发）")
    void composeInjectsEndpointKeys() throws IOException {
        String compose = Files.readString(Path.of("..", "docker-compose.yml"), StandardCharsets.UTF_8);
        assertThat(compose).as("compose 未注入 AI_PROVIDER").contains("AI_PROVIDER:");
        assertThat(compose).as("compose 未注入 DEEPSEEK_BASE_URL").contains("DEEPSEEK_BASE_URL:");
    }
}
