package com.vcfcf.adapters.vsanperf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity types not queried on a cluster for a while (v1 "Learned types",
 * v2 B7 and Q7 as clarified in review, 2026-11-06). Two ways in:
 * <ul>
 * <li><b>Fault</b> (Build 1): {@code NotSupported}, or an
 *     {@code InvalidArgument} ending in {@code entityRefId}: skipped for 24 h
 *     plus the same per-(cluster, type) offset (build 11, review N1).
 * <li><b>Empty echo</b> (Build 2): after {@link #EMPTY_CYCLES} consecutive
 *     empty answers on that cluster, skipped for 24 h plus a per-(cluster,
 *     type) offset of {@code stableHash mod 60} minutes, so clusters learned
 *     in one cycle do not all re-probe in one cycle the next day. A re-probe
 *     that comes back empty re-learns at once (no new 3-cycle count); one
 *     that returns data, or any non-empty answer or fault, resets the count.
 * <li><b>Fault strikes</b> (Build 19, design v3 Q5): the types flagged
 *     {@link EntityType#faultStrikes} (the nine that fault
 *     {@code InvalidArgument: entityRefId} on every ref form on 9.1.1, P3)
 *     are learned like an empty echo: after {@link #FAULT_STRIKES}
 *     consecutive type-unavailable faults on that cluster, with the same
 *     re-probe spread; a re-probe that faults again re-learns at once. Any
 *     other answer starts the count over.
 * <li><b>Server faults</b> (1.2.1, design D8): any other server fault on
 *     any type (a parsed vSphere fault; build 24: not an HTTP error without
 *     a fault body or another transport failure) strikes the same way, core
 *     types included: learned after {@link #FAULT_STRIKES} consecutive
 *     faulting cycles, re-probed with the spread. {@code ioinsight} is exempt.
 * </ul>
 * Never learned: the three core types on an empty echo
 * ({@link EntityType#learnsEmpty}), and {@code ioinsight} at all, which is
 * queried every cycle, best-effort ({@link #exempt}). Thread safe: workers
 * read and write it.
 */
final class LearnedTypes {

    static final long SKIP_MS = 24L * 60 * 60 * 1000;
    /** Consecutive empty cycles before an empty type is learned. */
    static final int EMPTY_CYCLES = 3;
    /** Consecutive type-unavailable faults before a strike type is learned (v3 Q5). */
    static final int FAULT_STRIKES = 3;
    /** Re-probe spread: the offset is {@code stableHash mod} this many minutes. */
    static final int SPREAD_MINUTES = 60;

    private final ConcurrentHashMap<String, Long> until = new ConcurrentHashMap<>();
    /** Consecutive empty answers; at or above the threshold after a learned period ends. */
    private final ConcurrentHashMap<String, Integer> emptyStreak = new ConcurrentHashMap<>();
    /** Consecutive type-unavailable faults of a strike type (v3 Q5). */
    private final ConcurrentHashMap<String, Integer> faultStreak = new ConcurrentHashMap<>();

    private static String key(String clusterMoid, EntityType type) {
        return clusterMoid + '\u0000' + type.id;
    }

    /** {@code ioinsight}: never learned, queried every cycle (review clarification). */
    static boolean exempt(EntityType type) {
        return type == EntityType.IOINSIGHT;
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

    /** A type-unavailable fault. @return true the first time (log it once) */
    boolean learn(String clusterMoid, EntityType type, long nowMs) {
        if (exempt(type)) return false;
        emptyStreak.remove(key(clusterMoid, type));
        // Review N1: fault-learned types re-probe with the same spread.
        return until.put(key(clusterMoid, type), nowMs + SKIP_MS + offsetMs(clusterMoid, type))
                == null;
    }

    /**
     * An empty echo for {@code type} on this cluster.
     *
     * @return true when this answer learned the type (log it)
     */
    boolean empty(String clusterMoid, EntityType type, long nowMs) {
        if (exempt(type) || !type.learnsEmpty()) return false;
        String k = key(clusterMoid, type);
        int n = emptyStreak.merge(k, 1, Integer::sum);
        if (n < EMPTY_CYCLES) return false;
        // Held at the threshold, so the next re-probe that is empty re-learns at once.
        emptyStreak.put(k, EMPTY_CYCLES);
        until.put(k, nowMs + SKIP_MS + offsetMs(clusterMoid, type));
        return true;
    }

    /**
     * A type-unavailable fault on a strike type (v3 Q5).
     *
     * @return true when this fault learned the type (log it)
     */
    boolean strike(String clusterMoid, EntityType type, long nowMs) {
        if (exempt(type)) return false;
        String k = key(clusterMoid, type);
        emptyStreak.remove(k);
        int n = faultStreak.merge(k, 1, Integer::sum);
        if (n < FAULT_STRIKES) return false;
        faultStreak.put(k, FAULT_STRIKES);
        until.put(k, nowMs + SKIP_MS + offsetMs(clusterMoid, type));
        return true;
    }

    /** Anything but a type-unavailable fault: the strike count starts over. */
    void noStrike(String clusterMoid, EntityType type) {
        faultStreak.remove(key(clusterMoid, type));
    }

    /** Consecutive strikes recorded (tests). */
    int strikes(String clusterMoid, EntityType type) {
        Integer n = faultStreak.get(key(clusterMoid, type));
        return n == null ? 0 : n;
    }

    /** Data, or a fault: the empty count starts over. */
    void answered(String clusterMoid, EntityType type) {
        emptyStreak.remove(key(clusterMoid, type));
    }

    /** The per-(cluster, type) re-probe offset, 0 to 59 minutes. */
    static long offsetMs(String clusterMoid, EntityType type) {
        long h = Watermarks.hash(clusterMoid + '\u0000' + type.id);
        return Math.floorMod(h, (long) SPREAD_MINUTES) * 60_000L;
    }

    /** Consecutive empty answers recorded (tests). */
    int streak(String clusterMoid, EntityType type) {
        Integer n = emptyStreak.get(key(clusterMoid, type));
        return n == null ? 0 : n;
    }

    /**
     * 1.2.1 (design D2): forget everything learned on one cluster (its
     * Performance Service was turned back on), so its first enabled cycle
     * re-probes every type.
     */
    void clearCluster(String clusterMoid) {
        String prefix = clusterMoid + '\u0000';
        until.keySet().removeIf(k -> k.startsWith(prefix));
        emptyStreak.keySet().removeIf(k -> k.startsWith(prefix));
        faultStreak.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** The type is learned on that cluster now (tests). */
    boolean learned(String clusterMoid, EntityType type, long nowMs) {
        Long u = until.get(key(clusterMoid, type));
        return u != null && nowMs < u;
    }

    void clear() {
        until.clear();
        emptyStreak.clear();
        faultStreak.clear();
    }
}
