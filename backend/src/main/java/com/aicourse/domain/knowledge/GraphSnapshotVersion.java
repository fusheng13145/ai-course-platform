package com.aicourse.domain.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 知识图谱快照标识。
 *
 * <p>全景图、层级树和章节子图必须能证明自己基于同一数据集。历史实现只返回
 * nodes/links 计数，两个接口并发时前端可能一次读旧计数、一次读新边集。
 * 该值由课程、节点/边数量和最大更新时间共同生成；数据变化必然换版本，
 * 让前端和集成测试可以拒绝跨版本拼接。</p>
 */
public final class GraphSnapshotVersion {

    private GraphSnapshotVersion() {
    }

    /**
     * 生成快照标识与生成时间。
     *
     * @param courseId   课程 ID；全平台口径可为 null
     * @param nodeCount  节点数
     * @param edgeCount  边数
     * @param nodeUpdatedAt 节点最大更新时间，可空
     * @param edgeUpdatedAt 边最大更新时间，可空
     */
    public static Snapshot of(Long courseId, int nodeCount, int edgeCount,
                              LocalDateTime nodeUpdatedAt, LocalDateTime edgeUpdatedAt) {
        String material = String.valueOf(courseId) + '|' + nodeCount + '|' + edgeCount
                + '|' + String.valueOf(nodeUpdatedAt) + '|' + String.valueOf(edgeUpdatedAt);
        return new Snapshot("v1:" + sha256(material).substring(0, 16), LocalDateTime.now());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Snapshot(String version, LocalDateTime generatedAt) {
    }
}
