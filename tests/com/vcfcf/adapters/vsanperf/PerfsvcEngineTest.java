package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 1.2.1 (factory design vsanperf-mp-v3.md, "1.2.1: Performance Service
 * disabled", D1 to D8), at the engine level on the simulated clock
 * ({@link T#CLOCK}).
 *
 * <p>Config bodies: {@code vsanclustergetconfig-enabled.json} (ESA lab,
 * verbatim), {@code -absent.json} (OSA probe, verbatim, no
 * {@code perfsvcConfig}), {@code -false.json} (SYNTHETIC: the OSA body with
 * {@code perfsvcConfig.enabled=false} added; the shape after
 * enable-then-disable is unverified). Fault bodies:
 * {@code queryperf-fault-systemerror-{pfccountraw,kareset}.json}, class and
 * faultstring verbatim from the devel 9.0.2 adapter log in a constructed
 * HTTP 500 envelope (tests/fixtures/README.md);
 * {@code queryperf-fault-masternotfound-osa-2026-11-07.json}, the verbatim
 * OSA probe capture {@code 05-osa-queryperf-disk-group.json}. The second cluster
 * ({@code domain-c9}, one host) is constructed; it answers the wildcard
 * echo for every type.
 */
public final class PerfsvcEngineTest {

    static final String LAB = EngineTest.LAB;
    static final String CL_RID = TiersEngineTest.CL_RID;
    /** Constructed second cluster; sorts after domain-c6004. */
    static final String PROBE = "domain-c9";
    static final String PROBE_RID = "rid-cluster-c9";

    public static void main(String[] args) throws Exception {
        disabledByAbsentAndFalse();
        disabledSkipsDiskAndVmReads();
        enabled();
        configReadFails();
        masterNotFoundStillFails();
        disabledThenEnabled();
        tierClusterOff();
        coreFaultIsolatedAndLearned();
        strikesResetOnAnAnswer();
        transportOutageRecovers();
        serverFaultBesideTransportError();
        allTypesLearnedSkippedWarns();
        System.out.println("PerfsvcEngineTest: all assertions passed");
    }

    static String config(String which) throws IOException {
        return T.vendor("vsanclustergetconfig-" + which + ".json");
    }

    static VsanFault fault(String which) throws IOException {
        return VsanFault.parse(T.vendor("queryperf-fault-systemerror-" + which + ".json"), 500);
    }

    /** The lab cluster plus the constructed probe cluster. */
    static EngineTest.FakeVc twoClusters() throws Exception {
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        List<Inventory.Cluster> cl = new ArrayList<>(vc.inventory.clusters);
        cl.add(new Inventory.Cluster(PROBE, "probe", List.of(new Inventory.Host("host-p1", "p1",
                "node-p1", "probe-uuid", true))));
        vc.inventory = new Inventory(cl);
        return vc;
    }

    static EngineTest.FakeOps ops(Inventory inv) {
        EngineTest.FakeOps ops = TiersEngineTest.labOps(inv);
        ops.clusters.put(PROBE, PROBE_RID);
        return ops;
    }

    static CycleEngine engine(EngineTest.FakeVc vc, EngineTest.FakeOps ops, T.RecLog log) {
        return TiersEngineTest.engine(vc, ops, log, TiersEngineTest.defaultTypes());
    }

    /** The Performance Service values pushed on {@code rid}, in push order. */
    static List<Double> states(EngineTest.FakeOps ops, String rid) {
        List<Double> out = new ArrayList<>();
        synchronized (ops.calls) {
            for (Map.Entry<Long, Map<String, Map<String, Double>>> c : ops.calls) {
                Map<String, Double> r = c.getValue().get(rid);
                if (r != null && r.containsKey(CycleEngine.PERFSVC_KEY)) {
                    out.add(r.get(CycleEngine.PERFSVC_KEY));
                }
            }
        }
        return out;
    }

    /** D1, D2, D4: absent key and enabled=false both read as disabled. */
    static void disabledByAbsentAndFalse() throws Exception {
        for (String which : new String[] {"absent", "false"}) {
            EngineTest.FakeVc vc = twoClusters();
            vc.configs.put(LAB, config("enabled"));
            vc.configs.put(PROBE, config(which));
            EngineTest.FakeOps ops = ops(vc.inventory);
            T.RecLog log = new T.RecLog();
            CycleEngine e = engine(vc, ops, log);
            CycleEngine.Result r = e.run(T.in(60_000));
            String w = which + ": ";
            T.eq(null, r.failure, w + "cycle ok");
            T.eq(0, vc.clusterCalls(PROBE), w + "zero perf calls on the disabled cluster");
            T.eq(26, r.perfCalls, w + "the lab's 26 only; the config reads are not perf calls");
            T.eq(1, vc.configCalls(PROBE), w + "one config read");
            T.eq(2, r.clustersInScope, w + "in scope counts it");
            T.eq(1, r.clustersOk, w + "the lab ok");
            T.eq(1, r.clustersPerfsvcDisabled, w + "clusters_perfsvc_disabled");
            T.eq(0, r.clustersFailed + r.clustersTimedOut, w + "not failed, not timed out");
            T.eq(Double.valueOf(1), r.metrics().get("Collection|clusters_perfsvc_disabled"),
                    w + "self-metric");
            T.eq("none", r.lastErrorProperty(), w + "no last_error");
            T.eq(List.of(0.0), states(ops, PROBE_RID), w + "0 on the disabled cluster");
            T.eq(List.of(1.0), states(ops, CL_RID), w + "1 on the lab");
            T.check(log.text().contains("summary: clusters ok=1 failed=0 timed_out=0 "
                    + "perfsvc_disabled=1 of 2;"), w + "summary: " + log.text());
            T.eq(1, log.count("INFO Performance Service disabled on " + PROBE + " (probe); perf "
                    + "collection skipped"), w + "INFO on the transition");
            // Rotation (D2): the disabled cluster is never lastAttempted or restartAt.
            T.eq(LAB, e.lastAttempted(), w + "rotation: the lab, not the disabled cluster");
            T.eq(null, e.restartAt(), w + "no restart cluster");
            T.check(!e.cutLastCycle().contains(PROBE), w + "never cut");

            CycleEngine.Result r2 = e.run(T.in(60_000));
            T.eq(1, r2.clustersPerfsvcDisabled, w + "cycle 2 still disabled");
            T.eq(0, vc.clusterCalls(PROBE), w + "still no perf call");
            T.eq(List.of(0.0, 0.0), states(ops, PROBE_RID), w + "pushed every cycle");
            T.eq(1, log.count("Performance Service disabled on"), w + "INFO not repeated");
            T.eq(LAB, e.lastAttempted(), w + "rotation unchanged");
        }

        // Only the disabled cluster: the rotation does not move at all.
        EngineTest.FakeVc vc = twoClusters();
        vc.configs.put(PROBE, config("absent"));
        CycleEngine e = new CycleEngine(vc, ops(vc.inventory), new CycleEngine.Settings(
                new ClusterFilter(PROBE, ""), 4, 30_000, true, TiersEngineTest.defaultTypes()),
                new T.RecLog(), T.CLOCK::get);
        CycleEngine.Result r = e.run(T.in(60_000));
        T.eq(null, r.failure, "a scope of one disabled cluster is not a failed cycle");
        T.eq(null, e.lastAttempted(), "rotation not moved by a disabled cluster");

        // The lab failing (a core batching fault, a pack defect) beside a
        // disabled probe: every collectible cluster failed, so the cycle fails.
        EngineTest.FakeVc vc3 = twoClusters();
        vc3.configs.put(PROBE, config("absent"));
        vc3.answers.put(LAB + "|host-domowner", VsanFault.parse(
                T.vendor("queryperf-fault-wildcardlimit-2026-11-06.json"), 500));
        CycleEngine.Result r3 = engine(vc3, ops(vc3.inventory), new T.RecLog()).run(T.in(60_000));
        T.eq(1, r3.clustersFailed, "the lab failed");
        T.eq(1, r3.clustersPerfsvcDisabled, "the probe disabled");
        T.check(r3.failure != null && r3.failure.startsWith("every vSAN cluster in scope failed"),
                "every collectible cluster failed: the cycle fails: " + r3.failure);
    }

    /** D2: the perf-only identity reads (QueryDisksForVsan, the VM read) are skipped too. */
    static void disabledSkipsDiskAndVmReads() throws Exception {
        EngineTest.FakeVc vc = DiskVmEngineTest.vc();
        vc.configs.put(LAB, config("absent"));
        CycleEngine.Result r = DiskVmEngineTest.engine(vc, DiskVmEngineTest.ops(vc.inventory),
                new T.RecLog(), DiskVmEngineTest.allTypes(), 1, 4).run(T.in(60_000));
        T.eq(0, r.perfCalls, "no perf call");
        T.eq(0, vc.diskCalls.get(), "no QueryDisksForVsan");
        T.eq(0, vc.vmReads.get(), "no VM read");
        T.eq(1, r.clustersPerfsvcDisabled, "disabled");
    }

    /** D1, D4: enabled collects as 1.2.0 and pushes 1. */
    static void enabled() throws Exception {
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        vc.configs.put(LAB, config("enabled"));
        EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine.Result r = engine(vc, ops, log).run(T.in(60_000));
        T.eq(26, r.perfCalls, "perf calls as 1.2.0");
        T.eq(2372 + 1, r.seriesPushed, "1.2.0's 2,372 values and the state value");
        T.eq(0, r.clustersPerfsvcDisabled, "not disabled");
        T.eq(List.of(1.0), states(ops, CL_RID), "1 pushed");
        T.eq(0, log.count("Performance Service"), "no transition INFO for a first enabled read");
        T.check(log.text().contains("series pushed=2373 (core 780 host 1410 cluster 182 state 1; "),
                "N3: the summary breakdown adds up to series_pushed: " + log.text());
        T.check(log.text().contains("PERF perfsvc cycle=1 cluster=" + LAB + " ms=1 "
                + "state=enabled fault=-"), "PERF perfsvc line");
    }

    /** D1: a failed or unreadable config read is unknown: collected as 1.2.0, no value. */
    static void configReadFails() throws Exception {
        Object[] answers = {new IOException("VsanClusterGetConfig returned HTTP 503"),
                new HttpTimeoutException("VsanClusterGetConfig: no answer"),
                new VsanFault("NotAuthenticated", "", Collections.emptyList(), 500),
                "<html>maintenance</html>", "[]"};
        for (Object a : answers) {
            EngineTest.FakeVc vc = TiersEngineTest.labVc();
            vc.configs.put(LAB, a);
            EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
            T.RecLog log = new T.RecLog();
            CycleEngine e = engine(vc, ops, log);
            CycleEngine.Result r = e.run(T.in(60_000));
            String w = a + ": ";
            T.eq(null, r.failure, w + "cycle ok");
            T.eq(26, r.perfCalls, w + "perf calls as 1.2.0");
            T.eq(1, r.clustersOk, w + "the cluster ok");
            T.eq(0, r.clustersPerfsvcDisabled, w + "not counted disabled");
            T.eq(List.of(), states(ops, CL_RID), w + "no state value pushed");
            T.eq("none", r.lastErrorProperty(), w + "a config failure alone is no error");
            T.check(log.text().contains("state=unknown"), w + "PERF perfsvc unknown");
            e.run(T.in(60_000));
            T.eq(1, log.count("VsanClusterGetConfig failed"), w + "WARN once per refresh");
        }
    }

    /**
     * D3: masternotfound on a cluster read as enabled or unknown still fails
     * it, on the verbatim capture (review W1); build 24 (N2): the cluster's
     * remaining calls are abandoned after it (one call a cycle, never learned).
     */
    static void masterNotFoundStillFails() throws Exception {
        String body = T.vendor("queryperf-fault-masternotfound-osa-2026-11-07.json");
        VsanFault parsed = VsanFault.parse(body, 500);
        T.eq("NotFound", parsed.faultClass, "capture: fault class");
        T.check(parsed.isMasterNotFound(), "capture: masternotfound read from faultMessage[].key");
        T.check(!parsed.isTypeUnavailable() && !parsed.isBatchingFault(), "nothing else");
        for (boolean known : new boolean[] {true, false}) {
            EngineTest.FakeVc vc = TiersEngineTest.labVc();
            if (known) vc.configs.put(LAB, config("enabled"));
            for (EntityType t : TiersEngineTest.defaultTypes()) {
                vc.answers.put(LAB + "|" + t.id, VsanFault.parse(body, 500));
            }
            EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
            CycleEngine e = engine(vc, ops, new T.RecLog());
            String w = (known ? "enabled" : "unknown") + ": ";
            for (int i = 1; i <= 4; i++) {
                CycleEngine.Result r = e.run(T.in(60_000));
                T.eq(1, r.perfCalls, w + "cycle " + i + ": the first call, then abandoned (N2)");
                T.eq(1, r.clustersFailed, w + "cycle " + i + ": failed");
                T.check(r.lastError.contains("masternotfound"), w + "last_error: " + r.lastError);
                T.eq(0, r.requiredCallsFailed + r.optionalCallsFailed, w + "not a type fault");
                T.eq(0, r.learnedSkips, w + "never learned");
            }
            T.eq(known ? List.of(1.0, 1.0, 1.0, 1.0) : List.of(), states(ops, CL_RID),
                    w + "the config read, not the fault, decides the state");
        }
        // N1: on a non-core type too (cluster-wide), after the core types answered.
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        vc.answers.put(LAB + "|cluster-domowner", VsanFault.parse(body, 500));
        CycleEngine.Result r = engine(vc, TiersEngineTest.labOps(vc.inventory), new T.RecLog())
                .run(T.in(60_000));
        T.eq(1, r.clustersFailed, "masternotfound on a cluster-tier type fails the cluster");
        T.check(r.seriesPushed > 0, "what was read before it is still pushed");
    }

    /** D2: disabled then enabled clears that cluster's learned state only; INFO per transition. */
    static void disabledThenEnabled() throws Exception {
        EngineTest.FakeVc vc = twoClusters();
        vc.configs.put(LAB, config("enabled"));
        vc.configs.put(PROBE, config("enabled"));
        EngineTest.FakeOps ops = ops(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops, log);
        int[] probe = new int[7];
        int[] lab = new int[7];
        for (int i = 1; i <= 6; i++) {
            if (i == 4) vc.configs.put(PROBE, config("absent"));
            if (i == 6) vc.configs.put(PROBE, config("enabled"));
            int p0 = vc.clusterCalls(PROBE);
            int l0 = vc.clusterCalls(LAB);
            e.run(T.in(60_000));
            probe[i] = vc.clusterCalls(PROBE) - p0;
            lab[i] = vc.clusterCalls(LAB) - l0;
            if (i == 4 || i == 5) {
                T.eq(1, log.count("INFO Performance Service disabled on " + PROBE),
                        "cycle " + i + ": INFO once");
            }
        }
        T.eq(26, probe[1], "probe cycle 1");
        T.eq(26, probe[3], "probe cycle 3: its 23 empty types learn now");
        T.eq(0, probe[4], "cycle 4 disabled");
        T.eq(0, probe[5], "cycle 5 disabled");
        T.eq(26, probe[6], "cycle 6 enabled again: learned state cleared, every type re-probed");
        T.eq(23, lab[4], "the lab's three empty types learned at cycle 3");
        T.eq(23, lab[6], "the lab's learned state is untouched by the probe's transition");
        T.eq(1, log.count("INFO Performance Service enabled on " + PROBE + " (probe); collecting"),
                "INFO once on enable");
        T.eq(List.of(1.0, 1.0, 1.0, 0.0, 0.0, 1.0), states(ops, PROBE_RID), "values per cycle");
    }

    /** D7: with tier_cluster off the cluster map is still read and the value lands. */
    static void tierClusterOff() throws Exception {
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        vc.configs.put(LAB, config("enabled"));
        EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
        CycleEngine.Result r = EngineTest.engine(vc, ops, 4, 30_000, new T.RecLog(), "")
                .run(T.in(30_000));
        T.eq(3, r.perfCalls, "core types only");
        T.eq(1, ops.clusterGets.get(), "cluster list read with tier_cluster off");
        T.eq(List.of(1.0), states(ops, CL_RID), "value pushed");
        T.eq(0, r.clustersUnresolved, "resolved");

        // No ClusterComputeResource object: counted unresolved, nothing pushed for it.
        EngineTest.FakeVc vc2 = TiersEngineTest.labVc();
        vc2.configs.put(LAB, config("absent"));
        EngineTest.FakeOps ops2 = EngineTest.labOps(vc2.inventory); // hosts only
        T.RecLog log = new T.RecLog();
        CycleEngine.Result r2 = EngineTest.engine(vc2, ops2, 4, 30_000, log, "").run(T.in(30_000));
        T.eq(1, r2.clustersUnresolved, "clusters_unresolved, as the cluster tier counts it");
        T.eq(0, ops2.calls.size(), "nothing pushed");
        T.check(log.text().contains("have no VMWARE ClusterComputeResource object"), "WARN");
    }

    /**
     * D8: a core-type server fault (vsan-pnic-net, pfcCountRaw) and the RDT
     * trio (kaReset) stay with their types: counted, named in last_error,
     * the later types still called, the cluster OK; learned at the third
     * fault; from cycle 4 four calls fewer, last_error none; re-probed after
     * 24 h.
     */
    static void coreFaultIsolatedAndLearned() throws Exception {
        long saved = T.CLOCK.get();
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        vc.configs.put(LAB, config("enabled"));
        vc.answers.put(LAB + "|vsan-pnic-net", fault("pfccountraw"));
        for (String t : new String[] {"cluster-rdt-network-latency", "rdt-network-latency",
                "vnic-rdt-network-latency"}) {
            vc.answers.put(LAB + "|" + t, fault("kareset"));
        }
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, TiersEngineTest.labOps(vc.inventory), log);
        for (int i = 1; i <= 3; i++) {
            CycleEngine.Result r = e.run(T.in(60_000));
            String w = "cycle " + i + ": ";
            T.eq(26, r.perfCalls, w + "26 calls");
            T.eq(1, r.clustersOk, w + "the cluster is OK");
            T.eq(0, r.clustersFailed, w + "not failed");
            T.eq(1, r.requiredCallsFailed, w + "required_calls_failed (vsan-pnic-net)");
            T.eq(3, r.optionalCallsFailed, w + "optional_calls_failed (the RDT trio)");
            T.eq(Double.valueOf(1), r.metrics().get("Collection|required_calls_failed"),
                    w + "self-metric");
            T.check(r.lastError.startsWith("cluster " + LAB + ": vsan-pnic-net: vSphere fault "
                    + "SystemError: A general system error occurred: RuntimeError('no such column: "
                    + "pfcCountRaw"), w + "last_error: " + r.lastError);
            T.eq(i, vc.calls(LAB, EntityType.CLUSTER_DOMOWNER), w + "later types still called");
            T.eq(i, vc.calls(LAB, EntityType.VSAN_VNIC_NET), w + "host tier still called");
        }
        T.eq(1, log.count("WARN cluster " + LAB + " (vcf-lab-mgmt-cl01): vsan-pnic-net: vSphere "
                + "fault SystemError"), "WARN at the first fault only");
        T.check(log.text().contains("(a core type; the cluster's other data is still pushed"),
                "WARN names it a core type");
        T.eq(3, log.count("(an optional "), "the trio's WARNs, once each");
        T.eq(4, log.count("faulted (SystemError) 3 cycles in a row; not queried there for about "
                + "24 h"), "INFO at learn, four types");
        for (int i = 4; i <= 5; i++) {
            CycleEngine.Result r = e.run(T.in(60_000));
            T.eq(26 - 3 - 4, r.perfCalls, "cycle " + i + ": 3 empties and the 4 faulting types "
                    + "skipped");
            T.eq(7, r.learnedSkips, "cycle " + i + ": learned skips");
            T.eq(0, r.requiredCallsFailed + r.optionalCallsFailed, "cycle " + i + ": counters 0");
            T.eq("none", r.lastErrorProperty(), "cycle " + i + ": last_error none once learned");
        }
        T.eq(3, vc.calls(LAB, EntityType.VSAN_PNIC_NET), "pNIC called three times");

        // 24 h (plus the up-to-59-minute spread) later: re-probed; a fault re-learns at once.
        T.CLOCK.addAndGet(25L * 60 * 60 * 1000);
        CycleEngine.Result r = e.run(T.in(60_000));
        T.eq(4, vc.calls(LAB, EntityType.VSAN_PNIC_NET), "re-probed after 24 h");
        T.eq(1, r.requiredCallsFailed, "still faulting");
        T.eq(0, r.clustersFailed, "still not a cluster failure");
        T.eq(8, log.count("faulted (SystemError) 3 cycles in a row"), "re-learned at once");
        r = e.run(T.in(60_000));
        T.eq(4, vc.calls(LAB, EntityType.VSAN_PNIC_NET), "skipped again");
        T.CLOCK.set(saved);
    }

    /**
     * D8: an answer between faults starts the strike count over; ioinsight
     * is never learned; a type that faults while every other call faults
     * too still fails the cluster (W1).
     */
    static void strikesResetOnAnAnswer() throws Exception {
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        String pnic = T.vendor("queryperf-vsan-pnic-net-2026-11-06.json");
        vc.answers.put(LAB + "|ioinsight", fault("kareset"));
        CycleEngine e = engine(vc, TiersEngineTest.labOps(vc.inventory), new T.RecLog());
        // fault, fault, answer, fault, fault, fault (learned), skipped
        boolean[] faults = {true, true, false, true, true, true, false};
        int[] required = new int[faults.length];
        for (int i = 0; i < faults.length; i++) {
            vc.answers.put(LAB + "|vsan-pnic-net", faults[i] ? fault("pfccountraw") : pnic);
            required[i] = e.run(T.in(60_000)).requiredCallsFailed;
        }
        T.eq(6, vc.calls(LAB, EntityType.VSAN_PNIC_NET), "not learned at the 4th fault, learned "
                + "at the 3rd in a row (cycle 6), skipped on cycle 7");
        T.eq("[1, 1, 0, 1, 1, 1, 0]", java.util.Arrays.toString(required), "required per cycle");
        T.eq(7, vc.calls(LAB, EntityType.IOINSIGHT), "ioinsight faults but is never learned");

        EngineTest.FakeVc all = TiersEngineTest.labVc();
        for (EntityType t : TiersEngineTest.defaultTypes()) {
            all.answers.put(LAB + "|" + t.id, fault("pfccountraw"));
        }
        CycleEngine.Result r = engine(all, TiersEngineTest.labOps(all.inventory), new T.RecLog())
                .run(T.in(60_000));
        T.eq(1, r.clustersFailed, "every perf call failed: the cluster fails (W1, kept)");
        T.eq(0, r.requiredCallsFailed + r.optionalCallsFailed, "counted as the cluster failure");
        T.check(r.lastError.contains("every call failed"), r.lastError);
        // Build 24 (B1): no strike on a failed cluster, so nothing is ever learned.
        CycleEngine ea = engine(all, TiersEngineTest.labOps(all.inventory), new T.RecLog());
        for (int i = 1; i <= 5; i++) {
            CycleEngine.Result ri = ea.run(T.in(60_000));
            T.eq(26, ri.perfCalls, "all faulting, cycle " + i + ": every type still called");
            T.eq(0, ri.learnedSkips, "all faulting, cycle " + i + ": nothing learned");
        }
    }

    /**
     * Review B1 (the reviewer's scratch probe): every call answers HTTP 503
     * with no fault body for three cycles, then the server recovers. Cycle 4
     * makes every call and pushes data; nothing was learned.
     */
    static void transportOutageRecovers() throws Exception {
        for (boolean coreOnly : new boolean[] {true, false}) {
            List<EntityType> types = coreOnly
                    ? EntityType.ofTiers(java.util.EnumSet.of(EntityType.Tier.CORE))
                    : TiersEngineTest.defaultTypes();
            EngineTest.FakeVc vc = TiersEngineTest.labVc();
            vc.configs.put(LAB, config("enabled"));
            Map<String, Object> good = new java.util.HashMap<>(vc.answers);
            for (EntityType t : types) vc.answers.put(LAB + "|" + t.id, new IOException("HTTP 503"));
            CycleEngine e = TiersEngineTest.engine(vc, TiersEngineTest.labOps(vc.inventory),
                    new T.RecLog(), types);
            String w = (coreOnly ? "core" : "default") + ": ";
            for (int i = 1; i <= 3; i++) {
                CycleEngine.Result r = e.run(T.in(60_000));
                T.eq(types.size(), r.perfCalls, w + "cycle " + i + ": every type called");
                T.eq(1, r.clustersFailed, w + "cycle " + i + ": the outage fails the cluster");
                T.check(r.failure != null, w + "cycle " + i + ": and the cycle");
                T.eq(0, r.requiredCallsFailed, w + "a 503 without a fault body is no type fault");
            }
            vc.answers.clear();
            vc.answers.putAll(good);
            CycleEngine.Result r4 = e.run(T.in(60_000));
            T.eq(types.size(), r4.perfCalls, w + "cycle 4: full calls after recovery");
            T.eq(0, r4.learnedSkips, w + "cycle 4: nothing learned from the outage");
            T.eq(1, r4.clustersOk, w + "cycle 4: ok");
            T.check(r4.seriesPushed > 1, w + "cycle 4: data pushed (" + r4.seriesPushed + ")");
            T.eq("none", r4.lastErrorProperty(), w + "cycle 4: clean");
        }
    }

    /**
     * Build 24 (B1): one parsed server fault (pNIC, pfcCountRaw) beside a
     * transport error on another type. Transport error on an optional type:
     * the cluster is OK, the server fault strikes and is learned at cycle 3.
     * Transport error on a core type: the cluster fails, the server fault is
     * never struck.
     */
    static void serverFaultBesideTransportError() throws Exception {
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        vc.answers.put(LAB + "|vsan-pnic-net", fault("pfccountraw"));
        vc.answers.put(LAB + "|cluster-domowner", new IOException("HTTP 502"));
        CycleEngine e = engine(vc, TiersEngineTest.labOps(vc.inventory), new T.RecLog());
        for (int i = 1; i <= 3; i++) {
            CycleEngine.Result r = e.run(T.in(60_000));
            T.eq(1, r.clustersOk, "optional transport, cycle " + i + ": ok");
            T.eq(1, r.requiredCallsFailed, "optional transport, cycle " + i + ": pNIC counted");
            T.eq(1, r.optionalCallsFailed, "optional transport, cycle " + i + ": 502 counted");
        }
        CycleEngine.Result r4 = e.run(T.in(60_000));
        T.eq(3, vc.calls(LAB, EntityType.VSAN_PNIC_NET), "pNIC learned at its third fault");
        T.eq(4, vc.calls(LAB, EntityType.CLUSTER_DOMOWNER), "the 502 type is never learned");
        T.eq(1, r4.optionalCallsFailed, "the 502 is still counted");

        EngineTest.FakeVc vc2 = TiersEngineTest.labVc();
        vc2.answers.put(LAB + "|vsan-pnic-net", fault("pfccountraw"));
        vc2.answers.put(LAB + "|host-domclient", new IOException("HTTP 502"));
        CycleEngine e2 = engine(vc2, TiersEngineTest.labOps(vc2.inventory), new T.RecLog());
        for (int i = 1; i <= 4; i++) {
            CycleEngine.Result r = e2.run(T.in(60_000));
            T.eq(1, r.clustersFailed, "core transport, cycle " + i + ": the cluster fails");
        }
        T.eq(4, vc2.calls(LAB, EntityType.VSAN_PNIC_NET), "no strike on a failed cluster");
    }

    /**
     * Build 24 (B1 part 3): every type learned (the core types on server
     * faults while a host-tier type answered empty), so a cycle makes no perf
     * call: still OK, but WARN once per refresh and last_error names it.
     */
    static void allTypesLearnedSkippedWarns() throws Exception {
        List<EntityType> types = new ArrayList<>(
                EntityType.ofTiers(java.util.EnumSet.of(EntityType.Tier.CORE)));
        types.add(EntityType.VSAN_CPU);
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        for (EntityType t : types) {
            if (t.tier == EntityType.Tier.CORE) vc.answers.put(LAB + "|" + t.id, fault("pfccountraw"));
        }
        vc.answers.remove(LAB + "|vsan-cpu"); // the wildcard echo
        T.RecLog log = new T.RecLog();
        CycleEngine e = TiersEngineTest.engine(vc, TiersEngineTest.labOps(vc.inventory), log, types);
        for (int i = 1; i <= 3; i++) {
            CycleEngine.Result r = e.run(T.in(60_000));
            T.eq(1, r.clustersOk, "cycle " + i + ": ok, one call answered");
            T.eq(3, r.requiredCallsFailed, "cycle " + i + ": the three core faults");
        }
        for (int i = 4; i <= 5; i++) {
            CycleEngine.Result r = e.run(T.in(60_000));
            T.eq(0, r.perfCalls, "cycle " + i + ": everything learned");
            T.eq(4, r.learnedSkips, "cycle " + i + ": four skips");
            T.eq(1, r.clustersOk, "cycle " + i + ": still ok");
            T.check(r.lastError != null && r.lastError.equals("cluster " + LAB
                    + ": all types learned-skipped; no perf call this cycle"),
                    "cycle " + i + ": last_error names it: " + r.lastError);
        }
        T.eq(1, log.count("every type in scope is learned-skipped"), "WARN once per refresh");
    }
}
