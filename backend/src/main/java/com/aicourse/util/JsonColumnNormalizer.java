package com.aicourse.util;

import com.aicourse.common.ApiCode;
import com.aicourse.common.JsonUtils;
import com.aicourse.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL JSON 列的入参规范化。
 *
 * <p><b>为什么需要这一层</b>：{@code questions.options}／{@code correct_answers}／
 * {@code knowledge_points} 三列在库里都是 {@code JSON} 类型，而实体把它们声明为
 * {@code String}。MyBatis 写入的是字符串字面量，MySQL 会按 JSON 文本解析：合法 JSON 才能落库，
 * 纯文本（如 {@code A}）会直接 {@code MysqlDataTruncation: Invalid JSON text} → 400。
 * 前端曾长期因此建题失败，只能自己在提交前编码；把编码责任留在客户端意味着
 * 「换一个客户端就再踩一次」，所以改由后端在入口统一规范化。</p>
 *
 * <p><b>规则</b>（对 null 一律保持 null，避免把「未提供」写成空值）：</p>
 * <ol>
 *   <li>入参是结构化对象（数组／Map／数字／布尔）→ 序列化为 JSON 文本；</li>
 *   <li>入参是字符串且本身已是合法 JSON → 原样使用（已编码过的客户端不必改）；</li>
 *   <li>入参是字符串、非 JSON → 当作 JSON 字符串字面量编码（{@code A} → {@code "A"}）——
 *       **仅用于答案列**；{@code options}／{@code knowledgePoints} 走
 *       {@link #normalizeArrayColumn(Object, String)}，非数组直接 400；</li>
 *   <li>多选的纯文本逗号分隔（{@code A,B}）→ 拆成数组 {@code ["A","B"]}；</li>
 *   <li>空白字符串 → {@code []}（本仓既有约定：空数组表示「没有」，见题库清空知识点的用法）。</li>
 * </ol>
 */
public final class JsonColumnNormalizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonColumnNormalizer() {
    }

    /**
     * 规范化普通 JSON 列（options／knowledgePoints）。
     *
     * @param raw 客户端入参，可为 String／List／Map 等
     * @return 可直接写入 JSON 列的文本；null 入参返回 null
     */
    public static String normalize(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return "[]";
            }
            if (isValidJson(trimmed)) {
                return trimmed;
            }
            return JsonUtils.toJson(trimmed);
        }
        return JsonUtils.toJson(raw);
    }

    /**
     * 规范化**必须是数组**的 JSON 列（{@code options}／{@code knowledgePoints}）。
     *
     * <p>与 {@link #normalize(Object)} 的差别是**只收数组**：这两个列的下游（题面渲染、Excel 导出、
     * 组卷）都按「遍历选项数组」消费，若把纯文本包成 JSON 字符串 {@code "A. 选项"}，读出来的是
     * 标量而不是数组，前端只能退回按换行拆——形状不一致、还会把引号带进界面。
     * 所以这里宁可 400，也不静默存一个形状不对的值。</p>
     *
     * @param raw   客户端入参（数组，或数组形态的 JSON 文本）
     * @param field 字段名，用于拼错误信息（如 {@code options}）
     * @return 可直接写入 JSON 列的文本；null 入参返回 null，空白串返回 {@code []}
     * @throws com.aicourse.exception.BusinessException 入参不是 JSON 数组（400）
     */
    public static String normalizeArrayColumn(Object raw, String field) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return "[]";
            }
            if (!isValidJson(trimmed) || !isJsonArray(trimmed)) {
                throw new BusinessException(ApiCode.BAD_REQUEST,
                        field + " 必须是 JSON 数组（例：[\"选项1\",\"选项2\"]）；收到的是非数组文本");
            }
            return trimmed;
        }
        String json = JsonUtils.toJson(raw);
        if (!isJsonArray(json)) {
            throw new BusinessException(ApiCode.BAD_REQUEST,
                    field + " 必须是 JSON 数组；收到的是 " + raw.getClass().getSimpleName());
        }
        return json;
    }

    /**
     * 规范化答案列：多选的纯文本逗号分隔会拆成数组，其余交给 {@link #normalize(Object)}。
     *
     * @param raw  客户端入参
     * @param type 题型（大写枚举，可为 null）
     * @return 可直接写入 JSON 列的文本；null 入参返回 null
     */
    public static String normalizeAnswers(Object raw, String type) {
        if (raw instanceof String text && "MULTIPLE_CHOICE".equals(type)) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty() && !isValidJson(trimmed) && trimmed.contains(",")) {
                List<String> parts = Arrays.stream(trimmed.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                return JsonUtils.toJson(parts);
            }
        }
        return normalize(raw);
    }

    /** 字符串是否为 JSON 数组。 */
    public static boolean isJsonArray(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            return MAPPER.readTree(text).isArray();
        } catch (Exception e) {
            return false;
        }
    }

    /** 字符串是否为合法 JSON（标量、数组、对象均算）。 */
    public static boolean isValidJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            MAPPER.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
