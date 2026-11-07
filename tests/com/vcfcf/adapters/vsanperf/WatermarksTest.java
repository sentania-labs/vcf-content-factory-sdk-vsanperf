package com.vcfcf.adapters.vsanperf;

import java.util.HashMap;
import java.util.Map;

/**
 * T1: the compact duplicate-bucket marks (design v2 "Heap", requirement 1).
 * Per (entity, label) semantics as Build 1 (the engine-level lagging-label
 * and lagging-host cases are in EngineTest); retention; times that cannot
 * be stored are never skipped; and memory at the 70-cluster example with
 * the default tiers (7,210 entities, 98,350 label marks): the estimate
 * stays near the design's ~0.9 MB, and the measured heap is a fraction of
 * Build 1's String-keyed scheme for the same marks.
 */
public final class WatermarksTest {

    static final long T5 = java.time.Instant.parse("2026-11-07T05:05:00Z").toEpochMilli();

    public static void main(String[] args) {
        semantics();
        memory();
        System.out.println("WatermarksTest: all assertions passed");
    }

    static void semantics() {
        Watermarks w = new Watermarks();
        long a = Watermarks.hash("host-domclient:node-a");
        long b = Watermarks.hash("host-domclient:node-b");
        T.check(a != b, "distinct hashes");
        T.eq(a, Watermarks.hash("host-domclient:node-a"), "stable hash");
        T.eq(Long.MIN_VALUE, w.last(a, 0), "no mark");
        w.record(a, 6, 0, T5);
        T.eq(T5, w.last(a, 0), "mark");
        T.eq(Long.MIN_VALUE, w.last(a, 1), "another label of the entity has none");
        T.eq(Long.MIN_VALUE, w.last(b, 0), "another entity has none");
        w.record(a, 6, 0, T5 - 300_000);
        T.eq(T5, w.last(a, 0), "an older time never moves the mark back");
        w.record(a, 6, 0, T5 + 300_000);
        T.eq(T5 + 300_000, w.last(a, 0), "newer moves it");
        T.eq(1, w.size(), "one label mark");
        T.eq(1, w.entities(), "one entity");
        w.record(a, 6, 7, T5);
        T.eq(1, w.size(), "a position outside the catalog is ignored");
        w.record(b, 6, 2, T5 + 500);
        T.eq(Long.MIN_VALUE, w.last(b, 2), "a time with milliseconds is not stored: never skipped");
        w.record(b, 6, 2, java.time.Instant.parse("2025-06-01T00:00:00Z").toEpochMilli());
        T.eq(Long.MIN_VALUE, w.last(b, 2), "before the base: not stored");
        // Retention: a label older than 24 h is dropped, the entity with it.
        w.record(b, 6, 3, T5);
        w.prune(T5 + 300_000 + Watermarks.RETAIN_MS + 1000);
        T.eq(0, w.entities(), "all marks older than 24 h dropped");
        w.record(a, 6, 0, T5);
        w.record(a, 6, 1, T5 + 24 * 3_600_000L);
        w.prune(T5 + 24 * 3_600_000L + 1000);
        T.eq(Long.MIN_VALUE, w.last(a, 0), "old label dropped");
        T.eq(T5 + 24 * 3_600_000L, w.last(a, 1), "recent label kept");
        // Build 15: pending marks per push group (one sample time), committed
        // per group; a group that is not committed leaves only its marks unset.
        Watermarks.Pending g1 = new Watermarks.Pending(T5, 4);
        Watermarks.Pending g2 = new Watermarks.Pending(T5 + 300_000, 4);
        Watermarks.Pending g3 = new Watermarks.Pending(T5 + 600_000, 4);
        for (int i = 0; i < 1000; i++) {
            long e = Watermarks.hash("e" + i);
            g1.add(e, 4, i % 4);
            g2.add(e, 4, (i + 1) % 4);
            g3.add(Watermarks.hash("f" + i), 82, 81); // another type's entities (width 82)
        }
        T.eq(1000, g1.size(), "grown past its preallocation");
        g1.commitTo(w);
        // g2 "failed": not committed.
        g3.commitTo(w);
        T.eq(T5, w.last(Watermarks.hash("e999"), 3), "group 1 committed");
        T.eq(Long.MIN_VALUE, w.last(Watermarks.hash("e998"), 3), "e998's position 3 came only from "
                + "group 2 (not committed): unset");
        T.eq(T5 + 600_000, w.last(Watermarks.hash("f7"), 81), "group 3 committed, width 82 position 81");
        T.eq(2000, w.entities() - 1, "2000 more entities");
        // Build 16 (review W1): an uncommitted older group lowers the newer mark.
        long x = Watermarks.hash("x");
        Watermarks.Pending older = new Watermarks.Pending(T5, 4);
        Watermarks.Pending newer = new Watermarks.Pending(T5 + 300_000, 4);
        older.add(x, 4, 2);
        newer.add(x, 4, 2);
        newer.commitTo(w);
        T.eq(T5 + 300_000, w.last(x, 2), "newer bucket marked");
        older.lowerIn(w);
        T.eq(T5 - 1000, w.last(x, 2), "lowered below the failed older bucket");
        T.check(T5 > w.last(x, 2), "so the older bucket is newer than the mark: sent again");
        w.lower(x, 4, 2, T5 + 600_000);
        T.eq(T5 - 1000, w.last(x, 2), "lower never raises");
        w.lower(Watermarks.hash("absent"), 4, 0, T5);
        T.eq(Long.MIN_VALUE, w.last(Watermarks.hash("absent"), 0), "lower never creates");
        w.clear();
        T.eq(0, w.size(), "clear");
    }

    /** The 70-cluster example at default tiers: 7,210 entities, 98,350 label marks. */
    static void memory() {
        int entities = 7210;
        int labels = 98_350;
        int[] width = new int[entities];
        for (int i = 0; i < entities; i++) width[i] = labels / entities;
        for (int i = 0; i < labels % entities; i++) width[i]++;

        Runtime rt = Runtime.getRuntime();
        long before = used(rt);
        Watermarks w = new Watermarks();
        for (int e = 0; e < entities; e++) {
            long h = Watermarks.hash("synthetic-type:" + e + "-node");
            for (int p = 0; p < width[e]; p++) w.record(h, width[e], p, T5);
        }
        long compact = used(rt) - before;
        T.eq(labels, w.size(), "every label mark held");
        T.eq(entities, w.entities(), "every entity held");
        long est = w.bytesEstimate();
        System.out.println("WatermarksTest: compact marks at the example: estimate " + est
                + " B, measured " + compact + " B");
        T.check(est < 1_200_000, "estimate near the design's ~0.9 MB: " + est);

        // Build 1's scheme for the same marks, for comparison only.
        long before1 = used(rt);
        Map<String, Long> build1 = new HashMap<>();
        for (int e = 0; e < entities; e++) {
            for (int p = 0; p < width[e]; p++) {
                build1.put("synthetic-type:" + e + "-node\u0000label" + p, T5);
            }
        }
        long old = used(rt) - before1;
        System.out.println("WatermarksTest: Build 1 String-keyed marks: measured " + old + " B");
        T.check(build1.size() == labels, "same marks");
        T.check(compact < old / 4, "compact is a fraction of Build 1 (" + compact + " vs " + old
                + ")");
        T.check(w.size() > 0, "keep alive");
        vmTierMemory(rt);
    }

    /**
     * The VM tier's marks at the example (v2 heap table: 182,560 VM, vdisk
     * and vSCSI entities, 1,344,000 labels, ~17 MB estimated): 16,800 VMs x
     * 6 labels, 82,880 vdisks x 7, 82,880 vSCSI devices x 8.
     */
    static void vmTierMemory(Runtime rt) {
        long before = used(rt);
        Watermarks w = new Watermarks();
        int[][] kinds = {{16_800, 6}, {82_880, 7}, {82_880, 8}};
        int e = 0;
        for (int[] k : kinds) {
            for (int i = 0; i < k[0]; i++, e++) {
                long h = Watermarks.hash("synthetic-vm-tier:" + e);
                for (int p = 0; p < k[1]; p++) w.record(h, k[1], p, T5);
            }
        }
        long measured = used(rt) - before;
        T.eq(182_560, w.entities(), "VM-tier entities");
        T.eq(16_800 * 6 + 82_880 * 7 + 82_880 * 8, w.size(), "VM-tier label marks");
        System.out.println("WatermarksTest: VM-tier marks at the example (182,560 entities, "
                + w.size() + " labels): estimate " + w.bytesEstimate() + " B, measured "
                + measured + " B");
        long est = w.bytesEstimate();
        // Build 11 review N4: the estimate (PERF cycle marks_bytes_est) tracks the
        // measured heap within 10% (G1 humongous slot arrays included).
        T.check(Math.abs(est - measured) <= measured / 10, "estimate " + est + " within 10% of "
                + "measured " + measured);
    }

    static long used(Runtime rt) {
        for (int i = 0; i < 3; i++) System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }
}
