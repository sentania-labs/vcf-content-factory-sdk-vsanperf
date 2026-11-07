package com.vcfcf.adapters.vsanperf;

import java.util.List;

/**
 * Design T4, the heap and deadline gate (pak issue #6; design v2 §Test plan
 * T4), on every change through {@code ci/run_java_tests.sh}, driving the T3
 * {@link ReplayHarness} in simulated time: 70 clusters x 6 hosts x 2 pNICs,
 * 4 workers, 0.19 s per call.
 *
 * <ul>
 * <li><b>(a) default tiers, per-vCenter 7 calls/s</b>: cycles 1 to 3 take
 *     the deadline path (26 calls per cluster before the empty types are
 *     learned, ~260 s of calls: completed clusters pushed, the rest timed
 *     out); from cycle 4 on every cluster completes with the perf phase
 *     under 240 s; from cycle 5 on exactly the expected 98,350 series per
 *     cycle are pushed (cycle 4 also pushes the previous bucket of the
 *     clusters cycle 3 did not reach); the steady state is 1,610 calls,
 *     ~230 s (design call plan). Peak heap delta under 65 MB.</li>
 * <li><b>(a') the same under the per-cluster ceiling</b>: recorded and
 *     checked against the arithmetic (26 x 0.19 s x 18 rounds = 88.9 s, then
 *     23 x 0.19 x 18 = 78.7 s).</li>
 * <li><b>(b) all tiers, VM N = 3</b> (1 ESA disk per host, 40 VMs per
 *     host, 4.93 disks per VM, 16,800 VMs), per-vCenter model: the deadline
 *     path is taken on some cycle; peak heap delta under 65 MB.</li>
 * <li><b>(b') all tiers, VM N = 3, per-cluster model</b>: every cluster
 *     completes every cycle; peak heap delta under 65 MB.</li>
 * <li><b>(c) all tiers, VM N = 1</b>, per-vCenter: heap only, under 65 MB.</li>
 * </ul>
 *
 * <p><b>Regression ceilings</b> (build 15 review W4), next to the budget, at
 * about twice the build 15 readings, so a regression of the build 14 kind
 * fails on its own: default tiers peak under 15 MB (build 15: ~6 to 7 MB;
 * build 14: 15.2 to 16.8 MB); VM-tier cases peak under 50 MB (build 15:
 * ~26 to 39 MB; build 14: 70.8 to 76.6 MB) and retained under 35 MB (~19 to
 * 29 MB).
 *
 * <p><b>Variance.</b> Simulated time is decided by thread scheduling at a
 * few points (which worker takes which cluster, the order the server sees
 * near-simultaneous requests), so figures move run to run: cycles 1 to 3
 * 1,664 to 1,681 calls with 61 to 64 clusters complete, cycle 4 1,628 to
 * 1,640 calls in 232.9 to 234.6 s (margin to the 240 s assertion ~5 s);
 * cycle 7 is 1,610 calls / 230.3 s in every run seen. The caller's clock is
 * the minimum over this cycle's workers, finished ones included, so a cut
 * cycle often ends with the workers timing out their own clusters
 * ({@code cut=false}, {@code timed_out > 0}) rather than the caller cutting.
 *
 * <p><b>Heap measurement.</b> {@link ReplayHarness#usedAfterGc()} (three
 * {@code System.gc()} calls, then {@code totalMemory - freeMemory}) before
 * the engine exists (the harness's templates already loaded) is the
 * baseline; a sampler thread takes the same reading every 150 ms of wall
 * time during each cycle and keeps the maximum (GC pauses do not move
 * simulated time, so sampling does not change the cycle). Noise: about 1 MB
 * between runs; a short peak between samples can be missed; the harness's
 * own live objects (one generated reply entity, the fake Ops pages) are
 * included and are small (replies are generated as a stream, never held
 * whole); the recording stitcher builds each push body as production does,
 * so that allocation is counted. Blind spot: the 150 ms sampler misses
 * shorter peaks; sampling every 10 ms read ~2 to 5 MB higher on the VM-tier
 * case. Not modelled: the gzip wire buffer (~0.2 MB per VM call) and HTTP
 * client buffers. The cycles run paced (each call also waits its simulated
 * latency / 20, VM cases / 40, of wall time) so the caller drains the result
 * queue as in production; unpaced, the 16-message queue fills.
 *
 * <p><b>VM tier and the budget.</b> Retained engine state after a cycle is
 * ~28 MB (dedup marks for 182,560 VM-tier entities ~21 MB, VM inventories,
 * maps). Until build 14 the cycle's pending dedup marks (24 B per pushed
 * value, ~935,000 values on a VM-heavy cycle, in arrays grown to 1,048,576
 * entries) were held to the end of the cycle and put the peak over 65 MB.
 * Since build 15 they live per push call (12 B each, at most one call's
 * values) and are committed when that call is clean.
 */
public final class ReplayBudgetTest {

    static final long MB = 1024L * 1024;
    /** Design D6. */
    static final long BUDGET = 65 * MB;
    /** Regression ceilings (build 15 review W4). */
    static final long DEFAULT_PEAK_CEILING = 15 * MB;
    static final long VM_PEAK_CEILING = 50 * MB;
    static final long VM_RETAINED_CEILING = 35 * MB;

    public static void main(String[] args) throws Exception {
        long w0 = System.nanoTime();
        defaultTiersPerVcenter();
        defaultTiersPerCluster();
        allTiers("(b) all tiers, VM N=3, per-vCenter", true, 3, 5, true);
        allTiers("(b') all tiers, VM N=3, per-cluster", false, 3, 4, true);
        allTiers("(c) all tiers, VM N=1, per-vCenter", true, 1, 3, false);
        System.out.println("ReplayBudgetTest: wall " + (System.nanoTime() - w0) / 1_000_000_000
                + " s");
        System.out.println("ReplayBudgetTest: all assertions passed");
    }

    static void print(String what, List<ReplayHarness.CycleResult> rs) {
        for (ReplayHarness.CycleResult r : rs) System.out.println("T4 " + what + " " + r);
    }

    /** T4 (a). */
    static void defaultTiersPerVcenter() throws Exception {
        ReplayHarness.Config c = new ReplayHarness.Config();
        c.cycles = 7;
        c.heap = true;
        c.pace = 20;
        ReplayHarness h = new ReplayHarness(c);
        T.eq(98_350L, h.expectedSeriesPerCycle(), "design: 98,350 series per cycle at the example");
        List<ReplayHarness.CycleResult> rs = h.run(null);
        print("(a) per-vCenter", rs);
        long peak = 0;
        for (ReplayHarness.CycleResult r : rs) {
            CycleEngine.Result res = r.res;
            T.eq(null, res.failure, "cycle " + r.cycle + " does not fail");
            peak = Math.max(peak, r.peakHeapDelta);
            if (r.cycle <= 3) {
                T.check(res.clustersTimedOut > 0, "cycle " + r.cycle + " takes the deadline path");
                T.check(res.perfPhaseMs >= 239_000, "cycle " + r.cycle + " runs to the deadline");
                T.check(res.perfCalls < 26 * 70, "cycle " + r.cycle + ": not every cluster's 26 calls");
            } else {
                T.eq(70, res.clustersOk, "cycle " + r.cycle + ": every cluster completes");
                T.check(res.perfPhaseMs < 240_000, "cycle " + r.cycle + " perf phase "
                        + res.perfPhaseMs + " ms < 240 s");
                T.eq(98_350L, r.newestBucketValues, "cycle " + r.cycle + ": every series' newest bucket");
            }
            if (r.cycle >= 5) T.eq(98_350L, r.valuesPushed, "cycle " + r.cycle + ": 98,350 values");
        }
        ReplayHarness.CycleResult last = rs.get(rs.size() - 1);
        T.eq(1_610, last.res.perfCalls, "steady: 23 calls x 70 clusters");
        T.check(Math.abs(last.res.perfPhaseMs - 230_000) < 5_000, "steady ~230 s (1,610 / 7 per s): "
                + last.res.perfPhaseMs);
        T.check(peak < BUDGET, "peak heap " + peak / MB + " MB < 65 MB");
        T.check(peak < DEFAULT_PEAK_CEILING, "regression ceiling: peak " + peak / MB + " MB < 15 MB");
        System.out.println("T4 (a) peak heap delta " + String.format(java.util.Locale.ROOT, "%.1f",
                peak / (double) MB) + " MB (budget 65 MB)");
    }

    /** T4 (a'), recorded: the per-cluster ceiling. */
    static void defaultTiersPerCluster() throws Exception {
        ReplayHarness.Config c = new ReplayHarness.Config();
        c.cycles = 5;
        c.perVcenter = false;
        List<ReplayHarness.CycleResult> rs = new ReplayHarness(c).run(null);
        print("(a') per-cluster", rs);
        for (ReplayHarness.CycleResult r : rs) {
            T.eq(70, r.res.clustersOk, "cycle " + r.cycle + ": completes");
            long want = r.cycle <= 3 ? 88_920 : 78_660; // 26 or 23 calls x 190 ms x 18 rounds
            T.check(Math.abs(r.res.perfPhaseMs - want) < 2_000, "cycle " + r.cycle + " perf phase "
                    + r.res.perfPhaseMs + " ~ " + want);
            T.eq(r.cycle <= 3 ? 1_820 : 1_610, r.res.perfCalls, "cycle " + r.cycle + " calls");
            T.eq(98_350L, r.newestBucketValues, "cycle " + r.cycle + " newest bucket");
        }
    }

    /**
     * T4 (b), (b') and (c): all tiers with the VM tier at N. Per vCenter the
     * deadline path must show on some cycle; per cluster every cluster must
     * complete every cycle ({@code assertCycles}); heap under the budget and
     * the ceilings in every case.
     */
    static void allTiers(String what, boolean perVcenter, int n, int cycles, boolean assertCycles)
            throws Exception {
        ReplayHarness.Config c = new ReplayHarness.Config();
        c.cycles = cycles;
        c.perVcenter = perVcenter;
        c.tierDisk = true;
        c.tierVm = true;
        c.vmEveryN = n;
        c.heap = true;
        c.pace = 40;
        List<ReplayHarness.CycleResult> rs = new ReplayHarness(c).run(null);
        print(what, rs);
        long peak = 0;
        long retained = 0;
        boolean deadline = false;
        for (ReplayHarness.CycleResult r : rs) {
            T.eq(null, r.res.failure, what + " cycle " + r.cycle + " does not fail");
            T.check(r.byKind.getOrDefault("VirtualMachine", 0L) > 0, what + " cycle " + r.cycle
                    + " pushes onto VirtualMachine");
            T.check(r.res.tierSeriesPushed[EntityType.Tier.DISK.ordinal()] > 0, what + " cycle "
                    + r.cycle + " pushes disk-tier keys");
            deadline |= r.res.clustersTimedOut > 0;
            if (assertCycles && !perVcenter) {
                T.eq(70, r.res.clustersOk, what + " cycle " + r.cycle + ": every cluster completes");
            }
            peak = Math.max(peak, r.peakHeapDelta);
            retained = Math.max(retained, r.retainedHeapDelta);
        }
        if (assertCycles && perVcenter) T.check(deadline, what + ": the deadline path is taken");
        System.out.println("T4 " + what + ": peak heap delta " + String.format(java.util.Locale.ROOT,
                "%.1f", peak / (double) MB) + " MB, retained " + String.format(java.util.Locale.ROOT,
                "%.1f", retained / (double) MB) + " MB (budget 65 MB)");
        T.check(peak < BUDGET, what + ": peak " + peak / MB + " MB < 65 MB");
        T.check(peak < VM_PEAK_CEILING, what + ": regression ceiling, peak " + peak / MB + " MB < 50 MB");
        T.check(retained < VM_RETAINED_CEILING, what + ": regression ceiling, retained "
                + retained / MB + " MB < 35 MB");
    }
}
