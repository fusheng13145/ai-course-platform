package com.aicourse.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I-1：引擎不可用判定的契约唯一来源（原为 HttpRagProvider 私有方法）。
 */
class ContractPathsTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static JsonNode json(String s) throws Exception {
        return OM.readTree(s);
    }

    @Test
    void code_503_is_unavailable() throws Exception {
        assertTrue(ContractPaths.isUnavailable(
                json("{\"code\":503,\"mode\":\"UNAVAILABLE\",\"error\":\"服务未接入\"}")));
    }

    @Test
    void mode_unavailable_alone_is_unavailable() throws Exception {
        assertTrue(ContractPaths.isUnavailable(json("{\"mode\":\"UNAVAILABLE\",\"results\":[]}")));
    }

    @Test
    void engine_mode_is_not_unavailable() throws Exception {
        assertFalse(ContractPaths.isUnavailable(json("{\"mode\":\"ENGINE\",\"results\":[]}")));
    }

    @Test
    void local_mode_is_not_unavailable() throws Exception {
        assertFalse(ContractPaths.isUnavailable(json("{\"mode\":\"LOCAL\"}")));
    }

    @Test
    void null_non_object_and_empty_are_not_unavailable() throws Exception {
        assertFalse(ContractPaths.isUnavailable(null));
        assertFalse(ContractPaths.isUnavailable(json("[1,2]")));
        assertFalse(ContractPaths.isUnavailable(json("{}")));
    }
}
