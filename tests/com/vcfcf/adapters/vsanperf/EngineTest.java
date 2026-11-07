package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * T1/T2 at the engine level, against fakes: the lab cycle from the
 * verbatim captures (390 series onto 5 hosts), duplicate-bucket
 * suppression including a host whose bucket arrives one cycle late,
 * identity misses, per-call timeout, cycle deadline, learned types, and
 * the cycle-failing cases. Lagging-host bodies and multi-cluster
 * inventories are constructed and say so.
 */
public final class EngineTest {

    static final String VC = "VC-A";
    static final String LAB = "domain-c6004";
    /** Answer marker: a PerfCall with no parsed result (a defect after the call). */
    static final String NULL_RESULT = "<null-result>";

    /** A scriptable vCenter. */
    static class FakeVc implements CycleEngine.VcenterApi {
        volatile String uuid = VC;
        volatile IOException loginFailure;
        volatile Inventory inventory;
        volatile IOException inventoryFailure;
        volatile RuntimeException inventoryDefect;
        /** (cluster|type) to a body or an exception; missing = empty echo. */
        final Map<String, Object> answers = new ConcurrentHashMap<>();
        final Map<String, Long> delays = new ConcurrentHashMap<>();
        /**
         * Build 21: simulated time. A call in {@link #advances} moves
         * {@link #fakeClock} on by its ms instead of sleeping, so a test that
         * gives the engine {@code fakeClock::get} decides cuts by the fixture.
         */
        final Map<String, Long> advances = new ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicLong fakeClock =
                new java.util.concurrent.atomic.AtomicLong(1_000_000L);
        final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

        @Override public String instanceUuid(long t) throws IOException {
            if (loginFailure != null) throw loginFailure;
            return uuid;
        }

        /** Whether the last inventory read asked for configManager.vsanSystem. */
        volatile boolean lastWithVsanSystem;
        /** vsanSystem moId to its disk uuids, or an exception (disk tier). */
        final Map<String, Object> disks = new ConcurrentHashMap<>();
        final AtomicInteger diskCalls = new AtomicInteger();
        /** Cluster MoRef to its VM read pages (bodies), or an exception (VM tier). */
        final Map<String, Object> vmPages = new ConcurrentHashMap<>();
        final AtomicInteger vmReads = new AtomicInteger();
        volatile boolean lastVmDevices;

        @Override public Inventory readInventory(long t, boolean withVsanSystem) throws IOException {
            lastWithVsanSystem = withVsanSystem;
            if (inventoryFailure != null) throw inventoryFailure;
            if (inventoryDefect != null) throw inventoryDefect;
            return inventory;
        }

        @SuppressWarnings("unchecked")
        @Override public List<String> queryDisksForVsan(String vsanSystem, long t)
                throws IOException {
            diskCalls.incrementAndGet();
            Object a = disks.get(vsanSystem);
            if (a instanceof IOException) throw (IOException) a;
            return a == null ? List.of() : (List<String>) a;
        }

        @Override public VmInventory readVms(String cluster, boolean withDevices, long t)
                throws IOException {
            vmReads.incrementAndGet();
            lastVmDevices = withDevices;
            Object a = vmPages.get(cluster);
            if (a instanceof IOException) throw (IOException) a;
            VmInventory.Builder b = new VmInventory.Builder(withDevices);
            if (a != null) for (String page : (String[]) a) b.addPage(page);
            return b.build();
        }

        @Override public CycleEngine.PerfCall queryPerf(String cluster, EntityType type, long s,
                long e, long timeoutMs) throws IOException, InterruptedException {
            return answer(cluster, type, EntityType.Scope.DASHBOARD);
        }

        /** Build 19: the scope the engine asked under, per call (dashboard goes through the 5-arg form). */
        final Map<String, EntityType.Scope> scopes = new ConcurrentHashMap<>();

        @Override public CycleEngine.PerfCall queryPerf(String cluster, EntityType type,
                EntityType.Scope scope, long s, long e, long timeoutMs)
                throws IOException, InterruptedException {
            scopes.put(cluster + "|" + type.id, scope);
            if (scope == EntityType.Scope.DASHBOARD) return queryPerf(cluster, type, s, e, timeoutMs);
            return answer(cluster, type, scope);
        }

        /** Build 20: every call in the order made ("cluster|type"). */
        final List<String> order = Collections.synchronizedList(new ArrayList<>());

        CycleEngine.PerfCall answer(String cluster, EntityType type, EntityType.Scope scope)
                throws IOException, InterruptedException {
            String k = cluster + "|" + type.id;
            order.add(k);
            Long adv = advances.get(k);
            if (adv != null) fakeClock.addAndGet(adv);
            calls.computeIfAbsent(k, x -> new AtomicInteger()).incrementAndGet();
            Long d = delays.get(k);
            if (d != null) Thread.sleep(d);
            Object a = answers.get(k);
            if (a instanceof IOException) throw (IOException) a;
            if (a instanceof Error) throw (Error) a;
            if (a instanceof RuntimeException) throw (RuntimeException) a;
            if (NULL_RESULT.equals(a)) return new CycleEngine.PerfCall(null, 200, 0, 0);
            String body = a == null ? "[{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\""
                    + type.id + ":*\",\"sampleInfo\":\"\"}]" : (String) a;
            return new CycleEngine.PerfCall(PerfParser.parse(body, type, scope), 200,
                    body.length(), body.length());
        }

        int calls(String cluster, EntityType t) {
            AtomicInteger n = calls.get(cluster + "|" + t.id);
            return n == null ? 0 : n.get();
        }
    }

    /** A recording Suite API: HostSystem and ClusterComputeResource rows, pushes recorded. */
    static final class FakeOps implements CycleEngine.OpsApi {
        final Map<String, String> hosts = new LinkedHashMap<>();
        /** Cluster MoRef to resource id, under {@link #VC}. */
        final Map<String, String> clusters = new LinkedHashMap<>();
        /** Extra ClusterComputeResource rows of another vCenter (same MoRefs). */
        final Map<String, String> otherVcClusters = new LinkedHashMap<>();
        final AtomicInteger gets = new AtomicInteger();
        final AtomicInteger clusterGets = new AtomicInteger();
        final AtomicInteger vmGets = new AtomicInteger();
        /** The VCID the host and cluster rows carry. */
        volatile String vcid = VC;
        /** The VirtualMachine list body served for every page (null: an empty list). */
        volatile String vmList;
        final List<Long> pushTimes = Collections.synchronizedList(new ArrayList<>());
        final Map<String, Map<String, Double>> pushed = new ConcurrentHashMap<>();
        final AtomicInteger values = new AtomicInteger();
        volatile boolean clean = true;
        /** Fail only this pushStats call (0-based), -1 for none (build 15). */
        volatile int failCall = -1;
        /** One entry per pushStats call: its timestamp and what it carried. */
        final List<Map.Entry<Long, Map<String, Map<String, Double>>>> calls =
                Collections.synchronizedList(new ArrayList<>());

        @Override public String get(String path) {
            if (path.contains("resourceKind=VirtualMachine")) {
                vmGets.incrementAndGet();
                T.check(path.contains("pageSize=500"), "VM list paged at 500: " + path);
                return vmList != null ? vmList : OpsHostMapTest.page(0);
            }
            if (path.contains("resourceKind=ClusterComputeResource")) {
                clusterGets.incrementAndGet();
                List<String> rows = new ArrayList<>();
                for (Map.Entry<String, String> c : otherVcClusters.entrySet()) {
                    rows.add(OpsHostMapTest.row(c.getValue(), c.getKey(), "VC-OTHER"));
                }
                for (Map.Entry<String, String> c : clusters.entrySet()) {
                    rows.add(OpsHostMapTest.row(c.getValue(), c.getKey(), vcid));
                }
                return OpsHostMapTest.page(rows.size(), rows.toArray(new String[0]));
            }
            T.check(path.contains("resourceKind=HostSystem"), "host list path: " + path);
            gets.incrementAndGet();
            List<String> rows = new ArrayList<>();
            for (Map.Entry<String, String> h : hosts.entrySet()) {
                rows.add(OpsHostMapTest.row(h.getValue(), h.getKey(), vcid));
            }
            return OpsHostMapTest.page(rows.size(), rows.toArray(new String[0]));
        }

        @Override public CycleEngine.PushOutcome pushStats(Map<String, Map<String, Double>> stats,
                long ts) {
            pushTimes.add(ts);
            Map<String, Map<String, Double>> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Double>> e : stats.entrySet()) {
                copy.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
            calls.add(Map.entry(ts, copy));
            int n = 0;
            for (Map.Entry<String, Map<String, Double>> e : stats.entrySet()) {
                for (Map.Entry<String, Double> v : e.getValue().entrySet()) {
                    pushed.computeIfAbsent(e.getKey(), k -> new ConcurrentHashMap<>())
                            .put(v.getKey() + "@" + ts, v.getValue());
                    n++;
                }
            }
            values.addAndGet(n);
            boolean ok = clean && calls.size() - 1 != failCall;
            return ok ? new CycleEngine.PushOutcome(n, 1, 0, true)
                    : new CycleEngine.PushOutcome(n, 0, 1, false);
        }
    }

    static Inventory labInventory() throws Exception {
        Inventory.Builder b = new Inventory.Builder();
        b.addPage(T.vendor("retrievepropertiesex-cluster-hosts-2026-11-06.json"));
        return b.build();
    }

    static FakeOps labOps(Inventory inv) {
        FakeOps ops = new FakeOps();
        for (Inventory.Cluster c : inv.clusters) {
            for (Inventory.Host h : c.hosts) ops.hosts.put(h.moid, "rid-" + h.moid);
        }
        return ops;
    }

    static FakeVc labVc() throws Exception {
        FakeVc vc = new FakeVc();
        vc.inventory = labInventory();
        vc.answers.put(LAB + "|host-domclient", T.vendor("queryperf-host-domclient-2026-11-06.json"));
        vc.answers.put(LAB + "|host-domowner", T.vendor("queryperf-host-domowner-2026-11-06.json"));
        vc.answers.put(LAB + "|vsan-pnic-net", T.vendor("queryperf-vsan-pnic-net-2026-11-06.json"));
        return vc;
    }

    static CycleEngine engine(FakeVc vc, FakeOps ops, int workers, long callMs, T.RecLog log,
            String include) {
        return new CycleEngine(vc, ops, new CycleEngine.Settings(new ClusterFilter(include, ""),
                workers, callMs, true), log, System::currentTimeMillis);
    }

    static long in(long ms) {
        return System.currentTimeMillis() + ms;
    }

    public static void main(String[] args) throws Exception {
        labCycleAndDedup();
        laggingHost();
        identityMisses();
        failingCycles();
        callTimeoutNextClusterProceeds();
        deadlineCut();
        learnedTypes();
        notAuthenticatedTwice();
        filterAndNonVsan();
        workerErrors();
        callerDefect();
        stopIsNotTimeout();
        uncleanPushResends();
        mixedTimestampRows();
        labelLagsWithinEntity();
        capsWithinTimeGroup();
        failedMiddleBatchOnly();
        noStarvationBehindSlowCluster();
        cutClusterRestartsFirst();
        loginFailsCollectThrows();
        summaryAndPerfLinesEveryCycle();
        System.out.println("EngineTest: all assertions passed");
    }

    static void labCycleAndDedup() throws Exception {
        FakeVc vc = labVc();
        FakeOps ops = labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops, 4, 30_000, log, "");
        CycleEngine.Result r = e.run(in(30_000));
        T.eq(null, r.failure, "lab cycle ok");
        T.eq(1, r.clustersInScope, "in scope");
        T.eq(1, r.clustersOk, "ok");
        T.eq(3, r.perfCalls, "three calls per cluster");
        T.eq(780, r.seriesPushed, "390 series x the window's two buckets (v2 one bucket rule)");
        T.eq(0, r.hostsUnresolved, "all 5 hosts resolved");
        T.eq(5, ops.pushed.size(), "5 HostSystem resources");
        T.eq(0, ops.clusterGets.get(), "no cluster list read with the cluster tier off");
        T.eq(17, r.metrics().size(), "seventeen self-metrics");
        T.eq("none", r.lastErrorProperty(), "no error");
        T.check(r.perfCallsPerSecond > 0, "call rate from the perf phase");
        T.check(r.perfCallMsAvg >= 0, "avg call ms");
        long t0230 = PerfParserTest.T0230;
        Map<String, Double> esx01 = ops.pushed.get("rid-host-6017");
        T.eq(2 * (6 + 38 + 17 * 2), esx01.size(), "esx01: 6 + 38 + 2 NICs x 17 keys, two buckets");
        T.check(esx01.containsKey("vSAN Perf|DOM Owner|latencyAvgWriteUs@" + t0230), "owner key");
        T.check(esx01.containsKey("vSAN Perf|DOM Client|iopsRead@" + t0230), "client key");
        T.check(esx01.containsKey("vSAN Perf|pNIC:vmnic1|rxErrPct@" + t0230), "pNIC key");
        // The per-mille value equals the capture's newest value / 10.
        PerfParser.Result pn = PerfParser.parse(T.vendor("queryperf-vsan-pnic-net-2026-11-06.json"),
                EntityType.VSAN_PNIC_NET);
        for (PerfParser.Entity en : pn.entities) {
            if (!en.ref.uuid.equals("69805090-5bba-3949-88f7-38052534dfcc")
                    || !en.ref.instance.equals("vmnic1")) continue;
            for (PerfParser.Sample s : en.samples) {
                T.near(s.value, esx01.get("vSAN Perf|pNIC:vmnic1|" + s.metric.leaf + "@"
                        + s.timestamp), "pushed " + s.metric.leaf);
            }
        }
        T.check(log.count("PERF call ") == 3, "PERF call lines");
        T.check(log.count("PERF cycle ") == 1 && log.text().contains("calls_per_s="),
                "PERF cycle line");
        T.check(log.text().contains("call_ms_avg="), "avg call ms logged separately");

        // Same buckets again: nothing is pushed twice.
        int before = ops.values.get();
        CycleEngine.Result r2 = e.run(in(30_000));
        T.eq(0, r2.seriesPushed, "second cycle with the same buckets pushes nothing");
        T.eq(780, r2.duplicatesSkipped, "all 780 recognised");
        T.eq(before, ops.values.get(), "no push call carried values");
        T.eq(1, ops.gets.get(), "Ops host map not re-read (no change, not the 12th cycle)");
    }

    /** Constructed: host B's 02:30 bucket arrives one cycle after host A's. */
    static void laggingHost() throws Exception {
        String a = "69805090-5bba-3949-88f7-38052534dfcc"; // host-6017
        String b = "6a8859db-a52e-c523-e78c-3805253a02fc"; // host-31189
        FakeVc vc = new FakeVc();
        vc.inventory = labInventory();
        FakeOps ops = labOps(vc.inventory);
        CycleEngine e = engine(vc, ops, 2, 30_000, new T.RecLog(), "");
        vc.answers.put(LAB + "|host-domclient", "[" + entity(a, "02:25:00,02:30:00", "1,2") + ","
                + entity(b, "02:20:00,02:25:00", "10,20") + "]");
        CycleEngine.Result r1 = e.run(in(30_000));
        T.eq(4, r1.seriesPushed, "cycle 1: A@02:25 and 02:30, B@02:20 and 02:25 (cold start)");
        vc.answers.put(LAB + "|host-domclient", "[" + entity(a, "02:25:00,02:30:00", "1,2") + ","
                + entity(b, "02:25:00,02:30:00", "20,30") + "]");
        CycleEngine.Result r2 = e.run(in(30_000));
        T.eq(1, r2.seriesPushed, "cycle 2: B's late 02:30 bucket is still pushed");
        T.near(30, ops.pushed.get("rid-host-31189").get("vSAN Perf|DOM Client|iopsRead@"
                + java.time.Instant.parse("2026-11-07T02:30:00Z").toEpochMilli()), "B@02:30");
        T.eq(3, r2.duplicatesSkipped, "A's two buckets and B's 02:25 not pushed twice");
    }

    static String entity(String node, String times, String values) {
        String[] t = times.split(",");
        return "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"host-domclient:" + node
                + "\",\"sampleInfo\":\"2026-11-07 " + t[0] + ",2026-11-07 " + t[1] + "\",\"value\":["
                + PerfParserTest.series("iopsRead", values) + "]}";
    }

    static void identityMisses() throws Exception {
        FakeVc vc = labVc();
        FakeOps ops = labOps(vc.inventory);
        ops.hosts.remove("host-6011"); // not collected by the vCenter adapter
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops, 4, 30_000, log, "");
        vc.answers.put(LAB + "|host-domclient", "[" + entity("unknown-node", "02:25:00,02:30:00", "1,2")
                + "]");
        CycleEngine.Result r = e.run(in(30_000));
        T.eq(null, r.failure, "a miss does not fail the cycle");
        T.eq(2, r.hostsUnresolved, "one unknown node + one host without an Ops object");
        T.eq(2 * (38 * 4 + 17 * 8), r.seriesPushed, "the other 4 hosts' rows only");
        T.eq(null, ops.pushed.get("rid-host-6011"), "nothing for the missing host");
        T.eq(1, log.count("match no host of the inventory"), "unknown node WARN");
        T.eq(1, log.count("have no VMWARE HostSystem object"), "Ops miss WARN");
        e.run(in(30_000));
        T.eq(1, log.count("have no VMWARE HostSystem object"), "WARN once per refresh");
        T.eq(1, ops.gets.get(), "the same miss right after a refresh forces no re-read");
        // The in-scope host set changes: the Ops map is re-read.
        Inventory.Cluster c = vc.inventory.clusters.get(0);
        List<Inventory.Host> fewer = new ArrayList<>(c.hosts.subList(1, c.hosts.size()));
        vc.inventory = new Inventory(List.of(new Inventory.Cluster(c.moid, c.name, fewer)));
        e.run(in(30_000));
        T.eq(2, ops.gets.get(), "host set change re-reads the Ops map");
    }

    static void failingCycles() throws Exception {
        FakeVc vc = labVc();
        FakeOps ops = labOps(vc.inventory);
        vc.uuid = "";
        CycleEngine.Result r = engine(vc, ops, 4, 30_000, new T.RecLog(), "").run(in(30_000));
        T.check(r.failure != null && r.failure.contains("instance UUID"), "empty instanceUuid fails");
        T.eq(0, ops.values.get() + ops.gets.get(), "nothing read from or pushed to Ops");
        T.eq(r.failure, r.lastError, "last_error carries it");

        vc = labVc();
        vc.inventoryFailure = new IOException("RetrievePropertiesEx returned HTTP 503");
        r = engine(vc, labOps(vc.inventory), 4, 30_000, new T.RecLog(), "").run(in(30_000));
        T.check(r.failure.startsWith("vSAN cluster inventory read failed"), "inventory fails");
        T.eq(0, vc.calls.size(), "no perf calls without inventory");

        vc = labVc();
        r = new CycleEngine(vc, null, new CycleEngine.Settings(new ClusterFilter("", ""), 4, 30_000,
                false), new T.RecLog(), System::currentTimeMillis).run(in(30_000));
        T.check(r.failure.contains("Suite API"), "no Suite API fails visibly");

        vc = labVc();
        for (EntityType t : EntityType.values()) {
            vc.answers.put(LAB + "|" + t.id, new VsanFault("SystemError", "boom",
                    Collections.emptyList(), 500));
        }
        T.RecLog log = new T.RecLog();
        r = engine(vc, labOps(vc.inventory), 4, 30_000, log, "").run(in(30_000));
        T.eq(1, r.clustersFailed, "cluster failed");
        T.check(r.failure.startsWith("every vSAN cluster in scope failed"), "all failed fails");
        T.eq(1, log.count("WARN cluster " + LAB), "one WARN per failed cluster");
        T.check(log.text().contains("SystemError"), "fault class in the WARN");
    }

    static void callTimeoutNextClusterProceeds() throws Exception {
        FakeVc vc = labVc();
        vc.inventory = new Inventory(List.of(
                new Inventory.Cluster("domain-c1", "slow", List.of(new Inventory.Host("host-x", "x",
                        "node-x", "U", true))),
                labInventory().clusters.get(0)));
        vc.answers.put("domain-c1|host-domowner", new HttpTimeoutException("no answer"));
        FakeOps ops = labOps(vc.inventory);
        CycleEngine.Result r = engine(vc, ops, 1, 30_000, new T.RecLog(), "").run(in(30_000));
        T.eq(1, r.clustersTimedOut, "timed out cluster");
        T.eq(1, r.clustersOk, "the next cluster proceeds");
        T.eq(780, r.seriesPushed, "its data is pushed");
        T.eq(null, r.failure, "a timeout does not fail the cycle");
        T.eq(0, vc.calls("domain-c1", EntityType.HOST_DOMCLIENT), "rest of the slow cluster skipped");
    }

    static void deadlineCut() throws Exception {
        FakeVc vc = labVc();
        vc.inventory = new Inventory(List.of(labInventory().clusters.get(0),
                new Inventory.Cluster("domain-c8", "b", List.of(new Inventory.Host("host-b", "b",
                        "node-b", "U", true))),
                new Inventory.Cluster("domain-c9", "c", List.of(new Inventory.Host("host-c", "c",
                        "node-c", "U", true)))));
        vc.delays.put("domain-c8|host-domowner", 5_000L);
        FakeOps ops = labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        long start = System.currentTimeMillis();
        CycleEngine.Result r = engine(vc, ops, 1, 30_000, log, "").run(in(1_500));
        long took = System.currentTimeMillis() - start;
        T.check(took < 4_000, "the deadline ends the cycle (took " + took + " ms)");
        T.eq(1, r.clustersOk, "completed cluster");
        T.eq(2, r.clustersTimedOut, "running and unstarted clusters timed out");
        T.eq(780, r.seriesPushed, "completed cluster still pushed");
        T.eq(null, r.failure, "a deadline does not fail the cycle");
        T.check(log.text().contains("cycle deadline reached"), "WARN");
    }

    static void learnedTypes() throws Exception {
        FakeVc vc = labVc();
        vc.answers.put(LAB + "|host-domclient", new VsanFault("NotSupported", "no",
                Collections.emptyList(), 500));
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, labOps(vc.inventory), 4, 30_000, log, "");
        CycleEngine.Result r = e.run(in(30_000));
        T.eq(1, r.clustersOk, "a learned type is not a failure");
        e.run(in(30_000));
        T.eq(1, vc.calls(LAB, EntityType.HOST_DOMCLIENT), "not queried again within 24 h");
        T.eq(1, log.count("does not offer host-domclient"), "INFO once");
    }

    static void notAuthenticatedTwice() throws Exception {
        FakeVc vc = labVc();
        vc.answers.put(LAB + "|host-domowner", new VsanFault("NotAuthenticated", "",
                Collections.emptyList(), 500));
        CycleEngine.Result r = engine(vc, labOps(vc.inventory), 4, 30_000, new T.RecLog(), "")
                .run(in(30_000));
        T.check(r.failure != null && r.failure.contains("NotAuthenticated"), "auth fails the cycle");
    }

    /** Review B1: an OOM is rethrown on the caller thread; a defect fails the cluster. */
    static void workerErrors() throws Exception {
        FakeVc vc = labVc();
        vc.answers.put(LAB + "|host-domowner", new OutOfMemoryError("probe"));
        CycleEngine e = engine(vc, labOps(vc.inventory), 4, 30_000, new T.RecLog(), "");
        OutOfMemoryError oom = T.expect(OutOfMemoryError.class, () -> e.run(in(30_000)),
                "a VirtualMachineError in a worker ends the cycle");
        T.eq("probe", oom.getMessage(), "the same error");

        vc = labVc();
        vc.answers.put(LAB + "|host-domowner", NULL_RESULT); // NPE after the call returned
        T.RecLog log = new T.RecLog();
        CycleEngine.Result r = engine(vc, labOps(vc.inventory), 4, 30_000, log, "")
                .run(in(30_000));
        T.eq(0, r.clustersOk, "never counted OK");
        T.eq(1, r.clustersFailed, "a RuntimeException after the call fails the cluster");
        T.check(r.lastError.contains("internal error (NullPointerException)"), r.lastError);
        T.check(r.failure != null, "every cluster failed");

        vc = labVc();
        vc.answers.put(LAB + "|host-domclient", new IllegalStateException("x"));
        r = engine(vc, labOps(vc.inventory), 4, 30_000, new T.RecLog(), "").run(in(30_000));
        T.eq(1, r.clustersFailed, "a RuntimeException from the call fails the cluster");
    }

    /** Review N8: a defect on the caller thread becomes a failed Result, not an escape. */
    static void callerDefect() throws Exception {
        FakeVc vc = labVc();
        vc.inventoryDefect = new IllegalArgumentException("Illegal character in path: session[x]y");
        CycleEngine.Result r = engine(vc, labOps(vc.inventory), 4, 30_000, new T.RecLog(), "")
                .run(in(30_000));
        T.eq("internal error in the collection cycle (IllegalArgumentException)", r.failure,
                "fixed text, no server data");
        T.eq(r.failure, r.lastError, "last_error set for the push");
        T.check(r.failureCause instanceof IllegalArgumentException, "cause kept");
    }

    /** Review N1: Stop Collecting is logged and counted as stopped, not timed out. */
    static void stopIsNotTimeout() throws Exception {
        FakeVc vc = labVc();
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, labOps(vc.inventory), 1, 30_000, log, "");
        e.setStopSignal(() -> true);
        CycleEngine.Result r = e.run(in(30_000));
        T.eq(0, r.clustersTimedOut, "not a timeout");
        T.eq(1, r.clustersStopped + r.clustersOk, "stopped (or finished before the check)");
        if (r.clustersStopped == 1) {
            T.check(log.text().contains("stopped (Stop Collecting)"), "stop WARN");
            T.check(!log.text().contains("cycle deadline reached"), "no deadline WARN");
            T.check(log.text().contains("stopped=1"), "summary says stopped");
        }
    }

    /** Review N5: a bucket in a push that was not clean is sent again next cycle. */
    static void uncleanPushResends() throws Exception {
        FakeVc vc = labVc();
        FakeOps ops = labOps(vc.inventory);
        ops.clean = false;
        CycleEngine e = engine(vc, ops, 4, 30_000, new T.RecLog(), "");
        CycleEngine.Result r1 = e.run(in(30_000));
        T.eq(780, r1.seriesPushed, "first attempt");
        T.eq(0, e.marksSize(), "no mark recorded for an unclean push");
        ops.clean = true;
        CycleEngine.Result r2 = e.run(in(30_000));
        T.eq(780, r2.seriesPushed, "re-sent while still in the window");
        CycleEngine.Result r3 = e.run(in(30_000));
        T.eq(0, r3.seriesPushed, "marked after the clean push");
    }

    static long at(String hhmmss) {
        return java.time.Instant.parse("2026-11-07T" + hhmmss + "Z").toEpochMilli();
    }

    /**
     * Design T1: rows of one cycle with different sample times are pushed
     * each under its own time, one pushStatsBatch call per time (the call
     * stamps every value with its one timestamp). Constructed bodies.
     */
    static void mixedTimestampRows() throws Exception {
        String a = "69805090-5bba-3949-88f7-38052534dfcc"; // host-6017
        String b = "6a8859db-a52e-c523-e78c-3805253a02fc"; // host-31189
        FakeVc vc = new FakeVc();
        vc.inventory = labInventory();
        FakeOps ops = labOps(vc.inventory);
        String entA = "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"host-domclient:" + a
                + "\",\"sampleInfo\":\"2026-11-07 02:25:00,2026-11-07 02:30:00\",\"value\":["
                + PerfParserTest.series("iopsRead", "1,2") + ","
                + PerfParserTest.series("iopsWrite", "5,None") + "]}";
        vc.answers.put(LAB + "|host-domclient", "[" + entA + ","
                + entity(b, "02:20:00,02:25:00", "10,20") + "]");
        CycleEngine.Result r = engine(vc, ops, 2, 30_000, new T.RecLog(), "").run(in(30_000));
        T.eq(5, r.seriesPushed, "five rows: every non-None bucket");
        T.eq(3, ops.calls.size(), "one push call per sample time");
        Map<Long, Map<String, Map<String, Double>>> byTs = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, Map<String, Double>>> c : ops.calls) {
            T.check(byTs.put(c.getKey(), c.getValue()) == null, "one call per time");
        }
        Map<String, Map<String, Double>> t0230 = byTs.get(at("02:30:00"));
        Map<String, Map<String, Double>> t0225 = byTs.get(at("02:25:00"));
        T.near(2, t0230.get("rid-host-6017").get("vSAN Perf|DOM Client|iopsRead"),
                "A iopsRead under 02:30");
        T.eq(1, t0230.size(), "only A has a 02:30 row");
        T.eq(1, t0230.get("rid-host-6017").size(), "A's iopsWrite is not under 02:30");
        T.near(1, t0225.get("rid-host-6017").get("vSAN Perf|DOM Client|iopsRead"),
                "A iopsRead's older bucket under 02:25 too");
        T.near(5, t0225.get("rid-host-6017").get("vSAN Perf|DOM Client|iopsWrite"),
                "A iopsWrite (None at 02:30) under 02:25");
        T.near(20, t0225.get("rid-host-31189").get("vSAN Perf|DOM Client|iopsRead"),
                "lagging B under 02:25");
        T.near(10, byTs.get(at("02:20:00")).get("rid-host-31189")
                .get("vSAN Perf|DOM Client|iopsRead"), "B's 02:20 under its own time");
    }

    /**
     * Design T1: the mark is per (entityRefId, label). Cycle 1: iopsRead has
     * bucket T, iopsWrite is None at T (so its 02:25 value is pushed).
     * Cycle 2: iopsWrite now has T; that value is pushed, iopsRead's T is not
     * pushed again. Constructed bodies.
     */
    static void labelLagsWithinEntity() throws Exception {
        String a = "69805090-5bba-3949-88f7-38052534dfcc"; // host-6017
        FakeVc vc = new FakeVc();
        vc.inventory = labInventory();
        FakeOps ops = labOps(vc.inventory);
        CycleEngine e = engine(vc, ops, 1, 30_000, new T.RecLog(), "");
        String head = "[{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"host-domclient:" + a
                + "\",\"sampleInfo\":\"2026-11-07 02:25:00,2026-11-07 02:30:00\",\"value\":[";
        vc.answers.put(LAB + "|host-domclient", head + PerfParserTest.series("iopsRead", "5,6") + ","
                + PerfParserTest.series("iopsWrite", "7,None") + "]}]");
        CycleEngine.Result r1 = e.run(in(30_000));
        T.eq(3, r1.seriesPushed, "cycle 1: iopsRead@T-300 and T, iopsWrite@T-300");
        vc.answers.put(LAB + "|host-domclient", head + PerfParserTest.series("iopsRead", "5,6") + ","
                + PerfParserTest.series("iopsWrite", "7,8") + "]}]");
        ops.calls.clear();
        CycleEngine.Result r2 = e.run(in(30_000));
        T.eq(1, r2.seriesPushed, "cycle 2: only iopsWrite's T value");
        T.eq(3, r2.duplicatesSkipped, "iopsRead's two buckets and iopsWrite's T-300 not again");
        T.eq(at("02:30:00"), ops.calls.get(0).getKey(), "under T");
        T.near(8, ops.calls.get(0).getValue().get("rid-host-6017")
                .get("vSAN Perf|DOM Client|iopsWrite"), "iopsWrite's T value pushed");
    }

    /**
     * Codex review: one worker, two clusters, the first cluster's call longer
     * than the cycle budget. The scheduling cursor advances on the cut too,
     * so the second cluster is queried in the next cycle (was: never).
     */
    static void noStarvationBehindSlowCluster() throws Exception {
        FakeVc vc = new FakeVc();
        vc.inventory = new Inventory(List.of(
                new Inventory.Cluster("domain-c1", "slow", List.of(new Inventory.Host("host-a",
                        "a", "node-a", "U", true))),
                new Inventory.Cluster("domain-c2", "healthy", List.of(new Inventory.Host("host-b",
                        "b", "node-b", "U", true)))));
        vc.delays.put("domain-c1|host-domowner", 5_000L);
        FakeOps ops = labOps(vc.inventory);
        CycleEngine e = engine(vc, ops, 1, 30_000, new T.RecLog(), "");
        CycleEngine.Result r1 = e.run(in(400));
        T.eq(1, vc.calls("domain-c1", EntityType.HOST_DOMOWNER), "cycle 1 starts with c1");
        T.eq(0, vc.calls("domain-c2", EntityType.HOST_DOMOWNER), "c2 not reached in cycle 1");
        T.eq(2, r1.clustersTimedOut, "both unfinished");
        CycleEngine.Result r2 = e.run(in(400));
        T.eq(1, vc.calls("domain-c2", EntityType.HOST_DOMOWNER), "c2 queried in cycle 2");
        T.eq(1, r2.clustersOk, "c2 completed");
        // Cycle 2 went c2 then c1 (c1 started once c2 was done, then cut).
        T.eq(2, vc.calls("domain-c1", EntityType.HOST_DOMOWNER), "c1 attempted after c2");
        e.run(in(400));
        T.eq(2, vc.calls("domain-c2", EntityType.HOST_DOMOWNER), "c2 queried again in cycle 3");
        T.eq(3, vc.calls("domain-c1", EntityType.HOST_DOMOWNER), "c1 still gets its turn");
    }

    static FakeVc fiveClusters() {
        FakeVc vc = new FakeVc();
        List<Inventory.Cluster> cls = new ArrayList<>();
        for (String n : new String[] {"1", "2", "3", "4", "5"}) {
            cls.add(new Inventory.Cluster("domain-c" + n, "c" + n, List.of(new Inventory.Host(
                    "host-" + n, n, "node-" + n, "U" + n, true))));
        }
        vc.inventory = new Inventory(cls);
        return vc;
    }

    /** The clusters, in order, whose DOM owner call came first in each cycle's calls from {@code from}. */
    static List<String> firstTwo(FakeVc vc, int from) {
        List<String> out = new ArrayList<>();
        synchronized (vc.order) {
            for (String k : vc.order.subList(from, vc.order.size())) {
                if (k.endsWith("|host-domowner")) out.add(k.substring(0, k.indexOf('|')));
                if (out.size() == 2) break;
            }
        }
        return out;
    }

    /**
     * Builds 19 to 21 (design v3 T4 (d), build 19 review W1, PR #11 CI
     * flake): the restart rule decided from injected outcomes, never from
     * wall-clock timing. {@link CycleEngine#settled} and
     * {@link CycleEngine#restartChoice} directly, then one engine run on a
     * simulated clock with one worker (serial, so the call order is the
     * fixture's).
     */
    static void cutClusterRestartsFirst() throws Exception {
        long dl = 10_000;
        T.check(CycleEngine.settled(CycleEngine.Outcome.OK, dl + 5, dl), "OK settles");
        T.check(CycleEngine.settled(CycleEngine.Outcome.FAILED, dl + 5, dl), "FAILED settles");
        T.check(CycleEngine.settled(CycleEngine.Outcome.TIMED_OUT, dl - 1, dl),
                "a per-call timeout before the deadline is not a cut");
        T.check(!CycleEngine.settled(CycleEngine.Outcome.TIMED_OUT, dl, dl),
                "TIMED_OUT at the deadline is a cut");

        List<String> order = List.of("c1", "c2", "c3", "c4", "c5");
        java.util.Set<Integer> started = java.util.Set.of(0, 1, 2, 3, 4);
        java.util.Set<String> cut = new java.util.HashSet<>();
        T.eq("c3", CycleEngine.restartChoice(order, started, java.util.Set.of("c1", "c2", "c4",
                "c5"), java.util.Set.of(), 2, cut), "two workers: the cut c3 goes first");
        T.eq(java.util.Set.of("c3"), cut, "cutNow");
        cut.clear();
        T.eq(null, CycleEngine.restartChoice(order, started, java.util.Set.of("c1", "c2", "c3",
                "c4", "c5"), java.util.Set.of(), 2, cut),
                "c3 timed out on its own call (settled): the start does not move");
        T.check(cut.isEmpty(), "nothing cut");
        T.eq(null, CycleEngine.restartChoice(order, started, java.util.Set.of("c1", "c2", "c4",
                "c5"), java.util.Set.of(), 1, cut), "one worker: rule off");
        cut.clear();
        T.eq(null, CycleEngine.restartChoice(order, started, java.util.Set.of("c3", "c4", "c5"),
                java.util.Set.of(), 2, cut), "c1 and c2 cut, but the workers started with them");
        T.eq(java.util.Set.of("c1", "c2"), cut, "both counted as cut");
        cut.clear();
        T.eq("c4", CycleEngine.restartChoice(order, started, java.util.Set.of("c1", "c2", "c5"),
                java.util.Set.of("c3"), 2, cut), "c3 cut last cycle too: the next cut one");
        cut.clear();
        T.eq("c3", CycleEngine.restartChoice(order, java.util.Set.of(0, 1, 2), java.util.Set.of(
                "c1", "c2"), java.util.Set.of(), 2, cut), "c3 started and cut goes first");
        T.eq(java.util.Set.of("c3"), cut, "c4 and c5 never started before the deadline: not cut");

        // Engine, simulated clock, one worker: c2's DOM owner call takes the
        // clock past the deadline. c3 to c5 are picked up after it and do not
        // count as started; with one worker the rule is off, so cycle 2
        // continues after c2, at c3.
        FakeVc vc = fiveClusters();
        vc.advances.put("domain-c2|host-domowner", 5_000L);
        FakeOps ops = labOps(vc.inventory);
        CycleEngine e = new CycleEngine(vc, ops, new CycleEngine.Settings(new ClusterFilter("", ""),
                1, 30_000, true), new T.RecLog(), vc.fakeClock::get);
        CycleEngine.Result r1 = e.run(vc.fakeClock.get() + 1_000);
        T.eq(1, r1.clustersOk, "c1 done");
        T.eq(1, vc.calls("domain-c2", EntityType.HOST_DOMOWNER), "c2 started, then cut");
        T.eq(0, vc.calls("domain-c3", EntityType.HOST_DOMOWNER), "c3 to c5 never called");
        int mark = vc.order.size();
        e.run(vc.fakeClock.get() + 1_000);
        T.eq("domain-c3", firstTwo(vc, mark).get(0), "one worker: cycle 2 starts at c3");
    }

    static List<String> sorted(List<String> l) {
        List<String> s = new ArrayList<>(l);
        Collections.sort(s);
        return s;
    }

    /**
     * Build 7 (devel 9.0.2 DOWN check): login fails, so the collect path
     * throws; nothing is read from or pushed to VCF Operations; the
     * self-metrics and last_error still reach the sink first; the thrown
     * failure carries the ConnectException, so it maps to DOWN; the message
     * is credential-free.
     */
    static void loginFailsCollectThrows() throws Exception {
        FakeVc vc = labVc();
        vc.loginFailure = new IOException("Login: ConnectException",
                new java.net.ConnectException("Connection refused"));
        FakeOps ops = labOps(vc.inventory);
        Map<String, Object> got = new LinkedHashMap<>();
        CycleReport.Sink sink = new CycleReport.Sink() {
            @Override public void metric(String k, double v) { got.put(k, v); }
            @Override public void property(String k, String v) { got.put(k, v); }
        };
        CycleEngine e = engine(vc, ops, 4, 30_000, new T.RecLog(), "");
        IOException thrown = T.expect(IOException.class, () -> CycleReport.run(e, in(30_000), sink),
                "collect throws");
        T.eq("vCenter login or ServiceInstance read failed: Login: ConnectException",
                thrown.getMessage(), "message");
        T.check(Failures.isUnreachable(thrown), "maps to DOWN (cause chain)");
        T.eq(0, ops.gets.get(), "no Suite API read");
        T.eq(0, ops.calls.size(), "no push");
        T.eq(0, vc.calls.size(), "no perf call");
        T.eq(thrown.getMessage(), got.get(CycleReport.LAST_ERROR), "last_error set");
        T.eq(18, got.size(), "17 self-metrics and last_error");
        T.eq(0.0, got.get("Collection|clusters_ok"), "nothing collected");
        T.check(!thrown.getMessage().contains("svc") && !thrown.getMessage().contains("tok-"),
                "credential-free");

        // A healthy cycle does not throw and reports last_error none.
        FakeVc ok = labVc();
        got.clear();
        CycleEngine.Result r = CycleReport.run(engine(ok, labOps(ok.inventory), 4, 30_000,
                new T.RecLog(), ""), in(30_000), sink);
        T.eq(780, r.seriesPushed, "healthy cycle");
        T.eq("none", got.get(CycleReport.LAST_ERROR), "last_error none");
    }

    /**
     * Build 9: the always-on INFO cycle summary is written every cycle,
     * a failed one included, to the log the adapter passes in (its own
     * logInfo route); PERF lines follow the flag as re-read each cycle.
     */
    static void summaryAndPerfLinesEveryCycle() throws Exception {
        FakeVc vc = labVc();
        T.RecLog log = new T.RecLog();
        CycleEngine e = new CycleEngine(vc, labOps(vc.inventory), new CycleEngine.Settings(
                new ClusterFilter("", ""), 4, 30_000, false), log, System::currentTimeMillis);

        CycleReport.run(e, in(30_000), null);
        String healthy = log.text();
        T.check(healthy.contains("INFO cycle 1 summary: clusters ok=1 failed=0 timed_out=0 of 1; "
                + "perf calls=3; series pushed=780 (core 780; 0 already pushed); unresolved "
                + "hosts=0 clusters=0; unparsed=0; learned skips=0; optional calls failed=0; push batches ok="),
                "summary line: " + healthy);
        T.check(healthy.contains(" s\n"), "summary carries seconds");
        T.eq(0, log.count("PERF "), "flag off: no PERF line");

        // The setting is re-read each cycle: on now, without a new engine.
        e.setPerfLogging(true);
        CycleReport.run(e, in(30_000), null);
        T.eq(1, log.count("INFO PERF cycle cycle=2"), "flag on: the PERF cycle line, at INFO");
        T.check(log.count("INFO PERF call cycle=2") == 3, "PERF call lines");
        T.check(log.text().contains("Performance timing logging on"), "the change is logged");

        // A failed cycle still writes its summary.
        vc.loginFailure = new IOException("Login: ConnectException",
                new java.net.ConnectException("refused"));
        T.expect(IOException.class, () -> CycleReport.run(e, in(30_000), null), "failed cycle");
        T.check(log.text().contains("INFO cycle 3 summary: clusters ok=0")
                && log.text().contains("FAILED: vCenter login or ServiceInstance read failed"),
                "failed cycle summary: " + log.text());
        T.eq(1, log.count("INFO PERF cycle cycle=3"), "PERF cycle line on a failed cycle too");

        e.setPerfLogging(false);
        vc.loginFailure = null;
        int before = log.count("PERF ");
        CycleReport.run(e, in(30_000), null);
        T.eq(before, log.count("PERF "), "flag off again: no new PERF line");
        T.eq(1, log.count("INFO cycle 4 summary"), "summary every cycle");
    }

    /** The 100-host cap applies within a time group: 150 hosts, one time, two calls. */
    static void capsWithinTimeGroup() throws Exception {
        List<Inventory.Host> hosts = new ArrayList<>();
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < 150; i++) {
            hosts.add(new Inventory.Host("host-s" + i, "s" + i, "node-s" + i, "U", true));
            if (i > 0) body.append(',');
            body.append(entity("node-s" + i, "02:25:00,02:30:00", "1," + i));
        }
        FakeVc vc = new FakeVc();
        vc.inventory = new Inventory(List.of(new Inventory.Cluster("domain-c50", "big", hosts)));
        vc.answers.put("domain-c50|host-domclient", body.append(']').toString());
        FakeOps ops = labOps(vc.inventory);
        CycleEngine.Result r = engine(vc, ops, 1, 30_000, new T.RecLog(), "").run(in(30_000));
        T.eq(300, r.seriesPushed, "all rows, two buckets");
        T.eq(4, ops.calls.size(), "split at 100 hosts, per time");
        int t0230calls = 0;
        for (Map.Entry<Long, Map<String, Map<String, Double>>> c : ops.calls) {
            T.check(c.getValue().size() == 100 || c.getValue().size() == 50, "100 then 50");
            if (c.getKey() == at("02:30:00")) t0230calls++;
        }
        T.eq(2, t0230calls, "the 02:30 group split in two calls");
    }

    /**
     * Builds 15 and 16: marks are committed per push call. 150 hosts, two
     * buckets (02:25, 02:30): four calls (02:25 x 100, 02:30 x 100, then
     * 02:25 x 50, 02:30 x 50). Each call in turn fails (review W1: calls 0
     * and 2 carry the older bucket, whose newer bucket is pushed clean). The
     * next cycle re-sends every failed value; the only other values re-sent
     * are the newer bucket of a failed (entity, label), with the same value
     * (it lands on itself); the cycle after that sends nothing. Constructed
     * bodies.
     */
    static void failedMiddleBatchOnly() throws Exception {
        for (int fail = 0; fail < 4; fail++) failedCall(fail);
    }

    static void failedCall(int fail) throws Exception {
        List<Inventory.Host> hosts = new ArrayList<>();
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < 150; i++) {
            hosts.add(new Inventory.Host("host-s" + i, "s" + i, "node-s" + i, "U", true));
            if (i > 0) body.append(',');
            body.append(entity("node-s" + i, "02:25:00,02:30:00", "1," + i));
        }
        FakeVc vc = new FakeVc();
        vc.inventory = new Inventory(List.of(new Inventory.Cluster("domain-c50", "big", hosts)));
        vc.answers.put("domain-c50|host-domclient", body.append(']').toString());
        FakeOps ops = labOps(vc.inventory);
        ops.failCall = fail;
        CycleEngine e = engine(vc, ops, 1, 30_000, new T.RecLog(), "");
        CycleEngine.Result r1 = e.run(in(30_000));
        String w = "failCall=" + fail + ": ";
        T.eq(300, r1.seriesPushed, w + "all values handed to the push");
        T.eq(4, ops.calls.size(), w + "four push calls");
        Map.Entry<Long, Map<String, Map<String, Double>>> failed = ops.calls.get(fail);
        long failedTs = failed.getKey();
        java.util.Set<String> failedValues = new java.util.HashSet<>();
        for (Map.Entry<String, Map<String, Double>> r : failed.getValue().entrySet()) {
            for (Map.Entry<String, Double> k : r.getValue().entrySet()) {
                failedValues.add(r.getKey() + "|" + k.getKey() + "@" + failedTs + "=" + k.getValue());
            }
        }
        java.util.Map<String, Double> landed = new java.util.HashMap<>();
        for (int i = 0; i < ops.calls.size(); i++) {
            if (i == fail) continue;
            Map.Entry<Long, Map<String, Map<String, Double>>> c = ops.calls.get(i);
            for (Map.Entry<String, Map<String, Double>> r : c.getValue().entrySet()) {
                for (Map.Entry<String, Double> k : r.getValue().entrySet()) {
                    landed.put(r.getKey() + "|" + k.getKey() + "@" + c.getKey(), k.getValue());
                }
            }
        }
        ops.failCall = -1;
        ops.calls.clear();
        CycleEngine.Result r2 = e.run(in(30_000));
        java.util.Set<String> resent = new java.util.HashSet<>();
        for (Map.Entry<Long, Map<String, Map<String, Double>>> c : ops.calls) {
            for (Map.Entry<String, Map<String, Double>> r : c.getValue().entrySet()) {
                for (Map.Entry<String, Double> k : r.getValue().entrySet()) {
                    String v = r.getKey() + "|" + k.getKey() + "@" + c.getKey();
                    if (failedValues.contains(v + "=" + k.getValue())) {
                        resent.add(v + "=" + k.getValue());
                        continue;
                    }
                    // Otherwise: the newer bucket of a failed label, re-sent unchanged.
                    T.check(c.getKey() > failedTs, w + "an extra value only for a newer bucket: " + v);
                    T.check(containsLabel(failedValues, r.getKey() + "|" + k.getKey() + "@"
                            + failedTs), w + "of a failed label: " + v);
                    T.eq(landed.get(v), k.getValue(), w + "the same value as landed: " + v);
                }
            }
        }
        T.eq(failedValues.size(), resent.size(), w + "every failed value is sent again");
        T.eq(failedValues.size() + (fail % 2 == 0 ? failedValues.size() : 0), r2.seriesPushed,
                w + (fail % 2 == 0 ? "the failed older buckets and their newer buckets"
                        : "only the failed values"));
        CycleEngine.Result r3 = e.run(in(30_000));
        T.eq(0, r3.seriesPushed, w + "all marked now");
    }

    static boolean containsLabel(java.util.Set<String> values, String prefix) {
        for (String v : values) if (v.startsWith(prefix + "=")) return true;
        return false;
    }

    static void filterAndNonVsan() throws Exception {
        FakeVc vc = labVc();
        Inventory lab = labInventory();
        vc.inventory = new Inventory(List.of(lab.clusters.get(0),
                new Inventory.Cluster("domain-c2", "compute", List.of(new Inventory.Host("host-n",
                        "n", null, null, false)))));
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, labOps(vc.inventory), 4, 30_000, log, "");
        CycleEngine.Result r = e.run(in(30_000));
        T.eq(1, r.clustersInScope, "non-vSAN cluster skipped");
        e.run(in(30_000));
        T.eq(1, log.count("is not vSAN-enabled"), "INFO once");
        CycleEngine.Result ex = engine(vc, labOps(vc.inventory), 4, 30_000, new T.RecLog(),
                "other-cluster").run(in(30_000));
        T.eq(0, ex.clustersInScope, "include filter");
        T.eq(null, ex.failure, "nothing in scope is not a failure");
    }
}
