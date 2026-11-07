package com.vcfcf.adapters.vsanperf;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Duplicate-bucket suppression: the newest sample time pushed per
 * ({@code entityRefId}, vSAN label), for the life of the adapter instance
 * (v1 "Value selection"). A 7-minute window often holds two 5-minute
 * buckets and the cycle is not aligned to them, so without this the same
 * bucket would be pushed twice.
 *
 * <p>Per label, not per entity or type: hosts lag each other by a bucket,
 * and within one entity a label can be {@code None} at the newest bucket
 * while another label has it. With a coarser mark, the first label moving
 * the mark to bucket T would make the second label's T value, arriving next
 * cycle, look old and be skipped for good. A mark is recorded only after
 * every push of the cycle came back clean (review N5).
 *
 * <p><b>Compact scheme (v2 "Heap", requirement 1).</b> Build 1 kept one
 * {@code String} key and a boxed {@code Long} per label (~160 B each, ~16 MB
 * at the 70-cluster example with the default tiers). Here one entity is a
 * 64-bit hash of its {@code entityRefId} in an open-addressing table, with
 * an {@code int[]} of marks indexed by the label's catalog position: about
 * 40 B per entity plus 4 B per label (~0.9 MB at the example). A mark is
 * the sample time in whole seconds since {@link #BASE_S}; sample times are
 * whole seconds ({@code yyyy-MM-dd HH:mm:ss}), so the comparison is exact,
 * as in Build 1. A time that cannot be stored (before the base, after 2094,
 * or with milliseconds) is not recorded, so it can be pushed again but is
 * never skipped. A 64-bit hash collision at 10^4 to 10^5 entities is
 * negligible (~10^-10); the mark is still compared per label.
 *
 * <p>Single-threaded: only the cycle's caller thread uses it.
 */
final class Watermarks {

    /** Marks older than this (sample time) are dropped: the entity is gone. */
    static final long RETAIN_MS = 24L * 60 * 60 * 1000;

    /** 2026-01-01T00:00:00Z: mark seconds are counted from here (an int lasts to 2094). */
    static final long BASE_S = 1_767_225_600L;

    /** No mark in a slot. */
    private static final int NONE = Integer.MIN_VALUE;

    private long[] keys = new long[64];
    private int[][] vals = new int[64][];
    private int entities;
    private int labels;

    // ------------------------------------------------------------------
    // Keys and times
    // ------------------------------------------------------------------

    /** The 64-bit FNV-1a hash of an {@code entityRefId} (UTF-8), finalised; never 0. */
    static long hash(String entityRefId) {
        long h = 0xcbf29ce484222325L;
        for (byte b : entityRefId.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h == 0 ? 1 : h;
    }

    /** Epoch ms to mark seconds, or {@link #NONE} when not storable. */
    static int toMark(long epochMs) {
        if (epochMs % 1000 != 0) return NONE;
        long s = epochMs / 1000 - BASE_S;
        if (s < 0 || s >= Integer.MAX_VALUE) return NONE;
        return (int) s;
    }

    private static long fromMark(int mark) {
        return (mark + BASE_S) * 1000;
    }

    // ------------------------------------------------------------------
    // Table
    // ------------------------------------------------------------------

    private int slot(long key) {
        int mask = keys.length - 1;
        int i = (int) (key ^ (key >>> 32)) & mask;
        while (keys[i] != 0 && keys[i] != key) i = (i + 1) & mask;
        return i;
    }

    /**
     * The last pushed sample time (epoch ms) for one label of one entity,
     * or {@code Long.MIN_VALUE} when none is recorded.
     */
    long last(long entity, int position) {
        int i = slot(entity);
        if (keys[i] == 0) return Long.MIN_VALUE;
        int[] m = vals[i];
        if (position < 0 || position >= m.length || m[position] == NONE) return Long.MIN_VALUE;
        return fromMark(m[position]);
    }

    /**
     * Record {@code ts} for one label of one entity whose type has
     * {@code width} labels; keeps the newer of the two marks.
     */
    void record(long entity, int width, int position, long ts) {
        int mark = toMark(ts);
        if (mark == NONE || position < 0 || position >= width) return;
        if ((entities + 1) * 10L >= keys.length * 6L) grow();
        int i = slot(entity);
        if (keys[i] == 0) {
            keys[i] = entity;
            int[] m = new int[width];
            Arrays.fill(m, NONE);
            vals[i] = m;
            entities++;
        } else if (vals[i].length != width) {
            // A hash shared with an entity of another type: start over
            // (never skip a value on a foreign mark).
            labels -= used(vals[i]);
            int[] m = new int[width];
            Arrays.fill(m, NONE);
            vals[i] = m;
        }
        int[] m = vals[i];
        if (m[position] == NONE) {
            labels++;
            m[position] = mark;
        } else if (mark > m[position]) {
            m[position] = mark;
        }
    }

    private static int used(int[] m) {
        int n = 0;
        for (int v : m) if (v != NONE) n++;
        return n;
    }

    private void grow() {
        rebuild(keys.length * 2, Long.MIN_VALUE);
    }

    /** Re-insert every entity into a table of {@code size}, dropping marks older than {@code cutoffMark}. */
    private void rebuild(int size, long cutoffMs) {
        long[] oldK = keys;
        int[][] oldV = vals;
        keys = new long[size];
        vals = new int[size][];
        entities = 0;
        labels = 0;
        int cut = cutoffMs == Long.MIN_VALUE ? NONE : toMarkFloor(cutoffMs);
        for (int j = 0; j < oldK.length; j++) {
            if (oldK[j] == 0) continue;
            int[] m = oldV[j];
            int n = 0;
            for (int p = 0; p < m.length; p++) {
                if (m[p] != NONE && cut != NONE && m[p] < cut) m[p] = NONE;
                if (m[p] != NONE) n++;
            }
            if (n == 0) continue;
            int i = slot(oldK[j]);
            keys[i] = oldK[j];
            vals[i] = m;
            entities++;
            labels += n;
        }
    }

    /** Mark seconds for a cutoff, clamped into the storable range. */
    private static int toMarkFloor(long epochMs) {
        long s = Math.floorDiv(epochMs, 1000L) - BASE_S;
        if (s <= 0) return 0;
        return s >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) s;
    }

    /** Drop marks older than {@link #RETAIN_MS}, and entities left with none. */
    void prune(long nowMs) {
        int cut = toMarkFloor(nowMs - RETAIN_MS);
        boolean stale = false;
        for (int j = 0; j < keys.length && !stale; j++) {
            if (keys[j] == 0) continue;
            for (int v : vals[j]) {
                if (v != NONE && v < cut) {
                    stale = true;
                    break;
                }
            }
        }
        if (!stale) return;
        int size = keys.length;
        while (size > 64 && entities * 10L < size * 2L) size /= 2;
        rebuild(size, nowMs - RETAIN_MS);
    }

    /** Label marks held (the Build 1 count). */
    int size() {
        return labels;
    }

    /** Entities held. */
    int entities() {
        return entities;
    }

    /**
     * Estimated heap held (for {@code PERF cycle} and the memory test): the
     * two slot arrays and per entity an {@code int[]}, 8-byte aligned
     * (64-bit JVM, compressed oops). Review N4 (build 11): the slot arrays
     * are large enough to be G1 humongous objects, which take whole regions;
     * leaving that out under-reported the VM tier at the example by ~31%
     * (14.6 MB estimated, 21.2 MB measured). The region size is G1's
     * ergonomic one for this JVM's max heap (heap / 2048, rounded up to a
     * power of two, 1 to 32 MB); on another collector the figure is slightly
     * high.
     */
    long bytesEstimate() {
        long region = g1RegionBytes(Runtime.getRuntime().maxMemory());
        long b = onHeap(16 + 8L * keys.length, region) + onHeap(16 + 4L * vals.length, region);
        for (int j = 0; j < keys.length; j++) {
            if (keys[j] != 0) b += align8(16 + 4L * vals[j].length);
        }
        return b;
    }

    private static long align8(long n) {
        return (n + 7) & ~7L;
    }

    /** An array's footprint: humongous (at least half a region) takes whole regions. */
    static long onHeap(long bytes, long region) {
        long n = align8(bytes);
        if (n < region / 2) return n;
        return (n + region - 1) / region * region;
    }

    /** G1's ergonomic region size for a max heap: heap / 2048 rounded up to a power of 2, 1 to 32 MB. */
    static long g1RegionBytes(long maxHeap) {
        long target = Math.max(1, maxHeap / 2048);
        long r = Long.highestOneBit(target);
        if (r < target) r <<= 1;
        return Math.max(1L << 20, Math.min(32L << 20, r));
    }

    void clear() {
        keys = new long[64];
        vals = new int[64][];
        entities = 0;
        labels = 0;
    }

    // ------------------------------------------------------------------
    // Pending marks of one cycle
    // ------------------------------------------------------------------

    /**
     * Marks of the values buffered this cycle, committed only if every push
     * of the cycle was clean (review N5). Primitive arrays: at the example
     * one cycle buffers ~98,000 values.
     */
    static final class Pending {
        private long[] entity = new long[256];
        private int[] width = new int[256];
        private int[] position = new int[256];
        private long[] time = new long[256];
        private int n;

        void add(long e, int w, int p, long ts) {
            if (n == entity.length) {
                int c = n * 2;
                entity = Arrays.copyOf(entity, c);
                width = Arrays.copyOf(width, c);
                position = Arrays.copyOf(position, c);
                time = Arrays.copyOf(time, c);
            }
            entity[n] = e;
            width[n] = w;
            position[n] = p;
            time[n] = ts;
            n++;
        }

        void commitTo(Watermarks marks) {
            for (int i = 0; i < n; i++) marks.record(entity[i], width[i], position[i], time[i]);
        }

        int size() {
            return n;
        }

        void clear() {
            n = 0;
            if (entity.length > 4096) {
                entity = new long[256];
                width = new int[256];
                position = new int[256];
                time = new long[256];
            }
        }
    }
}
