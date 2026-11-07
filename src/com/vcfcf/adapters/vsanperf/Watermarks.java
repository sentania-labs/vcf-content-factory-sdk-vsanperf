package com.vcfcf.adapters.vsanperf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Duplicate-bucket suppression: the newest sample time pushed per
 * ({@code entityRefId}, vSAN label), for the life of the adapter instance
 * (design "Value selection"). A 7-minute window often holds two 5-minute
 * buckets and the cycle is not aligned to them, so without this the same
 * bucket would be pushed twice.
 *
 * <p>Per label, not per entity or type: hosts lag each other by a bucket,
 * and within one entity a label can be {@code None} at the newest bucket
 * while another label has it. With a coarser mark, the first label moving
 * the mark to bucket T would make the second label's T value, arriving next
 * cycle, look old and be skipped for good. A mark is recorded only after
 * every push of the cycle came back clean (review N5). About 33,000 marks at
 * 70 clusters of 6 hosts and 12 NICs (one long each). Single-threaded: only
 * the cycle's caller thread uses it.
 */
final class Watermarks {

    /** Marks older than this (sample time) are dropped: the entity is gone. */
    static final long RETAIN_MS = 24L * 60 * 60 * 1000;

    private final Map<String, Long> marks = new HashMap<>();

    /** The {@code entityRefId} (type prefix included, node uuids are unique). */
    static String key(EntityRef ref) {
        return ref.type.id + ':' + ref.node + (ref.instance == null ? "" : "|" + ref.instance);
    }

    /** The mark of one label of one entity. */
    static String key(String entityRefId, String label) {
        return entityRefId + '\u0000' + label;
    }

    /** The last pushed sample time for {@code key}, or {@code Long.MIN_VALUE}. */
    long last(String key) {
        Long v = marks.get(key);
        return v == null ? Long.MIN_VALUE : v;
    }

    void record(String key, long ts) {
        marks.merge(key, ts, Math::max);
    }

    void prune(long nowMs) {
        for (Iterator<Long> it = marks.values().iterator(); it.hasNext(); ) {
            if (it.next() < nowMs - RETAIN_MS) it.remove();
        }
    }

    int size() {
        return marks.size();
    }

    void clear() {
        marks.clear();
    }
}
