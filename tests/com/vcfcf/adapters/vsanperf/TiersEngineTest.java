package com.vcfcf.adapters.vsanperf;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * T1 at the engine level for build 10 (design v2): the host and cluster
 * tiers on the lab from the verbatim captures (empty types answer the echo
 * quoted in PROVENANCE-2026-11-06-build2.md), ClusterComputeResource
 * resolution by (MoRef, VCID) with the same MoRef in two vCenters, an Ops
 * miss and its rate-limited refresh, a cluster uuid that is not the queried
 * cluster's, the runtime landing of the unobserved types, learned-empty
 * through the engine, and the PERF lines per tier. Ids marked "synthetic"
 * are constructed: no capture exists for those types.
 */
public final class TiersEngineTest {

    static final String LAB = EngineTest.LAB;
    static final String CL_RID = "rid-cluster-c6004";
    static final String CLUSTER_UUID = PerfParserTest.LAB_CLUSTER_UUID;
    static final String NODE_6017 = "69805090-5bba-3949-88f7-38052534dfcc";
    static final long T0505 = PerfParserTest.T0505;

    public static void main(String[] args) throws Exception {
        labDefaultTiers();
        tierSettingsSelectTypes();
        sameMorefTwoVcenters();
        clusterOpsMissRateLimited();
        clusterUuidNotTheQueriedCluster();
        runtimeLanding();
        System.out.println("TiersEngineTest: all assertions passed");
    }

    /** The lab vCenter with every core, host-tier and cluster-tier capture. */
    static EngineTest.FakeVc labVc() throws Exception {
        EngineTest.FakeVc vc = EngineTest.labVc();
        for (Object[] row : PerfParserTest.BUILD2) {
            vc.answers.put(LAB + "|" + ((EntityType) row[0]).id, T.vendor((String) row[1]));
        }
        return vc; // a missing answer is the empty echo (FakeVc)
    }

    static EngineTest.FakeOps labOps(Inventory inv) {
        EngineTest.FakeOps ops = EngineTest.labOps(inv);
        ops.clusters.put(LAB, CL_RID);
        return ops;
    }

    static List<EntityType> defaultTypes() {
        return new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null).types;
    }

    static CycleEngine engine(EngineTest.FakeVc vc, EngineTest.FakeOps ops, T.RecLog log,
            List<EntityType> types) {
        return new CycleEngine(vc, ops, new CycleEngine.Settings(new ClusterFilter("", ""), 4,
                30_000, true, types), log, T.CLOCK::get);
    }

    static long in(long ms) {
        return T.in(ms);
    }

    static void labDefaultTiers() throws Exception {
        EngineTest.FakeVc vc = labVc();
        EngineTest.FakeOps ops = labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        T.eq(26, defaultTypes().size(), "default tiers: 3 core + 10 cluster + 13 host types");
        CycleEngine e = engine(vc, ops, log, defaultTypes());

        CycleEngine.Result r = e.run(in(60_000));
        T.eq(null, r.failure, "lab cycle ok");
        T.eq(26, r.perfCalls, "26 calls per cluster on the first cycles (design: 26)");
        int core = EntityType.Tier.CORE.ordinal();
        int host = EntityType.Tier.HOST.ordinal();
        int cl = EntityType.Tier.CLUSTER.ordinal();
        T.eq(3, r.tierCalls[core], "core calls");
        T.eq(13, r.tierCalls[host], "host tier calls");
        T.eq(10, r.tierCalls[cl], "cluster tier calls");
        // Cold start: the window's two buckets of 390 + 705 + 91 = 1,186 series.
        T.eq(2 * 1186, r.seriesPushed, "1,186 series x two buckets");
        T.eq(780, r.tierSeriesPushed[core], "core");
        T.eq(1410, r.tierSeriesPushed[host], "host tier: 705 series");
        T.eq(182, r.tierSeriesPushed[cl], "cluster tier: 91 series");
        T.eq(0, r.hostsUnresolved + r.clustersUnresolved + r.entitiesUnparsed, "all resolved");
        T.eq(1, ops.clusterGets.get(), "one ClusterComputeResource list read");

        Map<String, Double> cluster = ops.pushed.get(CL_RID);
        T.eq(182, cluster.size(), "cluster: 91 keys x two buckets");
        T.check(cluster.containsKey("vSAN Perf|Capacity|totalBytes@" + T0505), "capacity key");
        T.check(cluster.containsKey("vSAN Perf|DOM Owner|" + EntityType.CLUSTER_DOMOWNER.metrics()
                .get(0).leaf + "@" + T0505), "cluster DOM Owner key");
        double ratio = cluster.get("vSAN Perf|Capacity|dedupRatio@" + T0505);
        T.check(ratio >= 100, "dedupRatio pushed as served (x 100): " + ratio);
        Map<String, Double> esx01 = ops.pushed.get("rid-host-6017");
        T.eq(2 * (78 + 141), esx01.size(), "esx01: 78 core + 141 host-tier series, two buckets");
        T.check(esx01.containsKey("vSAN Perf|TCP/IP:defaultTcpipStack|tcpErrsPct@" + T0505),
                "TCP/IP instanced key");
        T.check(esx01.containsKey("vSAN Perf|vNIC:vmk2|" + EntityType.VSAN_VNIC_NET.metrics()
                .get(0).leaf + "@" + T0505), "vNIC instanced key");
        T.check(esx01.containsKey("vSAN Perf|vNIC RDT Latency:vmk2|"
                + EntityType.VNIC_RDT_NETWORK_LATENCY.metrics().get(0).leaf + "@" + T0505),
                "vNIC RDT instanced key");
        T.check(esx01.containsKey("vSAN Perf|DOM Comp Mgr|iopsRead@" + T0505),
                "host DOM Comp Mgr key");
        for (String k : esx01.keySet()) {
            T.check(!k.startsWith("vSAN Perf|Capacity|"), "no cluster key on a host: " + k);
        }

        String text = log.text();
        T.eq(26, log.count("PERF call "), "one PERF call line per call");
        T.check(text.contains("type=cluster-domowner tier=cluster parse=stream"), "tier on PERF call");
        T.check(text.contains("type=zdom-vtx tier=host"), "host tier on PERF call");
        T.eq(3, log.count("PERF tier cycle=1 "), "one PERF tier line per tier");
        T.check(text.contains("PERF tier cycle=1 tier=cluster calls=10 "), "cluster tier line");
        T.check(text.contains("series_pushed=182 unresolved=0 learned_skips=0 failed=0"),
                "cluster tier line counts");
        T.check(text.contains("PERF identity cycle=1 read=ops_clusters calls=1 "), "identity");
        T.check(text.contains("trigger=refresh"), "trigger");
        T.check(text.contains("kind=ClusterComputeResource"), "PERF push kind cluster");
        T.check(text.contains("kind=HostSystem"), "PERF push kind host");
        T.check(text.contains("calls_by_tier=core:3,host:13,cluster:10"), "PERF cluster");
        T.check(text.contains("calls_core=3 calls_host=13 calls_cluster=10"), "PERF cycle tiers");
        T.check(text.contains("marks_entities=" + (20 + 60 + 7) + " "),
                "marks: 20 core + 60 host-tier + 7 cluster entities");
        T.check(text.contains("series pushed=2372 (core 780 host 1410 cluster 182; 0 already "
                + "pushed); unresolved hosts=0 clusters=0; unparsed=0; learned skips=0"),
                "summary line: " + text);

        // Cycle 2: same buckets, nothing new; empty types answered empty twice.
        CycleEngine.Result r2 = e.run(in(60_000));
        T.eq(0, r2.seriesPushed, "nothing pushed twice");
        T.eq(26, r2.perfCalls, "still 26");
        // Cycle 3: the third empty answer learns the three learnable empty types.
        CycleEngine.Result r3 = e.run(in(60_000));
        T.eq(26, r3.perfCalls, "26 on the third cycle");
        T.eq(3, log.count("answered empty 3 cycles in a row"), "three types learned");
        // Cycle 4: steady, 23 calls (ioinsight is exempt and still queried).
        CycleEngine.Result r4 = e.run(in(60_000));
        T.eq(23, r4.perfCalls, "steady: 23 calls per cluster on the lab");
        T.eq(3, r4.learnedSkips, "three learned skips");
        T.eq(4, vc.calls(LAB, EntityType.IOINSIGHT), "ioinsight queried every cycle");
        T.eq(3, vc.calls(LAB, EntityType.VSAN_DIRECT_HOST), "vsan-direct-host learned");
        T.eq(4, vc.calls(LAB, EntityType.HOST_DOMOWNER), "core never learned");
        T.check(log.text().contains("PERF tier cycle=4 tier=host calls=12 "), "host 12 steady");
        T.check(log.text().contains("PERF tier cycle=4 tier=cluster calls=8 "),
                "cluster 8 steady (ioinsight exempt)");
    }

    /** Tier switches and exclude_types decide the calls. */
    static void tierSettingsSelectTypes() throws Exception {
        EngineTest.FakeVc vc = labVc();
        List<EntityType> hostOff = new VsanPerfConfig("vc", "u", "p", "", "", null, null, null,
                null, "false", "true", "").types;
        CycleEngine.Result r = engine(vc, labOps(vc.inventory), new T.RecLog(), hostOff)
                .run(in(60_000));
        T.eq(13, r.perfCalls, "host tier off: core + cluster");
        T.eq(0, vc.calls(LAB, EntityType.ZDOM_VTX), "no host-tier call");

        vc = labVc();
        List<EntityType> excl = new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null,
                null, null, "ioinsight, vsan-pnic-net").types;
        EngineTest.FakeOps ops = labOps(vc.inventory);
        r = engine(vc, ops, new T.RecLog(), excl).run(in(60_000));
        T.eq(24, r.perfCalls, "two types excluded (one of them core)");
        T.eq(0, vc.calls(LAB, EntityType.VSAN_PNIC_NET), "pNIC excluded");

        vc = labVc();
        ops = labOps(vc.inventory);
        r = engine(vc, ops, new T.RecLog(), EntityType.ofTiers(EnumSet.of(EntityType.Tier.CORE)))
                .run(in(60_000));
        T.eq(3, r.perfCalls, "both tiers off: Build 1");
        T.eq(0, ops.clusterGets.get(), "no cluster list without the cluster tier");
    }

    /** The resolver: the same cluster MoRef in another vCenter is never matched. */
    static void sameMorefTwoVcenters() throws Exception {
        EngineTest.FakeVc vc = labVc();
        EngineTest.FakeOps ops = labOps(vc.inventory);
        ops.otherVcClusters.put(LAB, "rid-cluster-of-another-vcenter");
        CycleEngine.Result r = engine(vc, ops, new T.RecLog(), defaultTypes()).run(in(60_000));
        T.eq(0, r.clustersUnresolved, "resolved under this vCenter");
        T.eq(182, ops.pushed.get(CL_RID).size(), "onto this vCenter's cluster");
        T.eq(null, ops.pushed.get("rid-cluster-of-another-vcenter"), "never the other one");

        // Directly: the map built for each vCenter picks its own row.
        String page = OpsHostMapTest.page(2,
                OpsHostMapTest.row("rid-a", "domain-c6004", "VC-A"),
                OpsHostMapTest.row("rid-b", "domain-c6004", "VC-B"));
        List<String> paths = new ArrayList<>();
        OpsHostMap a = OpsHostMap.load(p -> {
            paths.add(p);
            return page;
        }, "VC-A", OpsHostMap.CLUSTER);
        T.check(paths.get(0).contains("adapterKind=VMWARE&resourceKind=ClusterComputeResource"),
                "cluster list path");
        T.eq("rid-a", a.resourceId("domain-c6004"), "VC-A");
        T.eq("rid-b", OpsHostMap.load(p -> page, "VC-B", OpsHostMap.CLUSTER)
                .resourceId("domain-c6004"), "VC-B");
        T.eq(null, OpsHostMap.load(p -> OpsHostMapTest.page(1,
                OpsHostMapTest.row("rid-c", "domain-c6004", null)), "VC-A", OpsHostMap.CLUSTER)
                .resourceId("domain-c6004"), "no VCID: never matched");
    }

    /**
     * The cluster is not in Ops: cluster rows are dropped and counted, host
     * data still goes, WARN once per refresh; the miss asks for a map
     * refresh, rate-limited to once per 12 cycles from the last read (review
     * N2: a scheduled read restarts the limit, so reads are never back to
     * back); the MoRef is skipped until then.
     */
    static void clusterOpsMissRateLimited() throws Exception {
        EngineTest.FakeVc vc = labVc();
        EngineTest.FakeOps ops = labOps(vc.inventory);
        ops.clusters.clear();
        T.RecLog log = new T.RecLog();
        List<EntityType> types = EntityType.ofTiers(EnumSet.of(EntityType.Tier.CORE,
                EntityType.Tier.CLUSTER));
        CycleEngine e = engine(vc, ops, log, types);
        CycleEngine.Result r = e.run(in(60_000));
        T.eq(null, r.failure, "a miss does not fail the cycle");
        T.eq(1, r.clustersUnresolved, "one cluster unresolved");
        T.eq(780, r.seriesPushed, "host data still pushed; no cluster row");
        T.eq(1, log.count("have no VMWARE ClusterComputeResource"), "WARN");
        T.eq(1, ops.clusterGets.get(), "first read");
        for (int i = 2; i <= 12; i++) e.run(in(60_000));
        T.eq(1, ops.clusterGets.get(), "review N2: no miss refresh within 12 cycles of a read");
        T.eq(1, log.count("have no VMWARE ClusterComputeResource"), "WARN once per refresh");
        ops.clusters.put(LAB, CL_RID);
        CycleEngine.Result r13 = e.run(in(60_000));
        T.eq(2, ops.clusterGets.get(), "one refresh at the 12-cycle limit, never back to back");
        T.eq(0, r13.clustersUnresolved, "resolved after it");
        T.check(ops.pushed.get(CL_RID) != null, "cluster data now pushed");
    }

    /** Constructed: a cluster-type entity whose uuid is not the queried cluster's. */
    static void clusterUuidNotTheQueriedCluster() throws Exception {
        EngineTest.FakeVc vc = labVc();
        vc.answers.put(LAB + "|cluster-domowner", T.vendor(
                "queryperf-cluster-domowner-2026-11-06.json").replace(CLUSTER_UUID,
                "00000000-synthetic-other-cluster"));
        EngineTest.FakeOps ops = labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine.Result r = engine(vc, ops, log, defaultTypes()).run(in(60_000));
        T.eq(1, r.clustersUnresolved, "counted");
        T.eq(182 - 46, ops.pushed.get(CL_RID).size(), "the other cluster types still land");
        T.eq(1, log.count("are not the queried cluster's"), "WARN");
    }

    /**
     * The three cluster-tier types with no observed id form (synthetic ids):
     * the cluster's uuid lands on the cluster, one node uuid of that cluster
     * on that host (same group), anything else is skipped and counted, WARN
     * once per type per day with the part count, never the id.
     */
    static void runtimeLanding() throws Exception {
        EngineTest.FakeVc vc = labVc();
        String head = "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"ioinsight:";
        String tail = "\",\"sampleInfo\":\"2026-11-07 05:05:00\",\"value\":["
                + PerfParserTest.series("writeRatio", "42") + "]}";
        vc.answers.put(LAB + "|ioinsight", "[" + head + CLUSTER_UUID + tail + "," + head + NODE_6017
                + tail + "," + head + "synthetic-unknown-uuid" + tail + "," + head
                + "synthetic|two-parts" + tail + "]");
        EngineTest.FakeOps ops = labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops, log, defaultTypes());
        CycleEngine.Result r = e.run(in(60_000));
        T.near(42, ops.pushed.get(CL_RID).get("vSAN Perf|IOInsight|writeRatio@" + T0505),
                "cluster uuid: on the cluster");
        T.near(42, ops.pushed.get("rid-host-6017").get("vSAN Perf|IOInsight|writeRatio@" + T0505),
                "node uuid: on that host, same group");
        T.eq(2, r.entitiesUnparsed, "unknown uuid and two-part id counted");
        T.eq(0, r.hostsUnresolved + r.clustersUnresolved, "not an identity miss");
        T.eq(1, log.count("ioinsight: an entity id ("), "WARN once");
        T.check(!log.text().contains("synthetic-unknown-uuid") && !log.text().contains("two-parts"),
                "the id is never logged");
        e.run(in(60_000));
        T.eq(1, log.count("ioinsight: an entity id ("), "not again the same day");
    }
}
