package com.vcfcf.adapters.vsanperf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity types a cluster does not offer (design "Learned types"): after a
 * {@code NotSupported}, or an {@code InvalidArgument} ending in
 * {@code entityRefId}, the type is not queried on that cluster for 24
 * hours, then probed again. An empty-type echo is not learned. Thread
 * safe: workers read and write it.
 */
final class LearnedTypes {

    static final long SKIP_MS = 24L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, Long> until = new ConcurrentHashMap<>();

    private static String key(String clusterMoid, EntityType type) {
        return clusterMoid + '\u0000' + type.id;
    }

    boolean skip(String clusterMoid, EntityType type, long nowMs) {
        Long u = until.get(key(clusterMoid, type));
        if (u == null) return false;
        if (nowMs >= u) {
            until.remove(key(clusterMoid, type), u);
            return false;
        }
        return true;
    }

    /** @return true the first time (log it once) */
    boolean learn(String clusterMoid, EntityType type, long nowMs) {
        return until.put(key(clusterMoid, type), nowMs + SKIP_MS) == null;
    }

    void clear() {
        until.clear();
    }
}
