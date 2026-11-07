package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * T1 for build 19 (design v3, {@code metric_scope}), from the P3 captures
 * ({@code tests/fixtures/vsan-perfsvc-json/all-<type>-2026-11-07.json}, all 69
 * types, no {@code labels} filter, verbatim; faults and empty echoes
 * included): the request with and without the filter; the parser keeping
 * the scope's catalog labels and counting the rest; the new id grammars
 * ({@code host-cpu} one and two parts, {@code dom-world-cpu} three); the
 * engine under {@code all} on the lab (calls per cycle with the Q5 fault
 * strikes, values, labels dropped, the read-back keys of T5 step 5); the
 * scope switch restarting learned state and marks; the remote (HCI Mesh)
 * rule. Ids marked "synthetic" are constructed: no capture exists for them.
 */
public final class ScopeTest {

    static final String LAB = EngineTest.LAB;
    static final String CL_RID = TiersEngineTest.CL_RID;
    static final String CLUSTER_UUID = PerfParserTest.LAB_CLUSTER_UUID;
    static final String NODE_6017 = TiersEngineTest.NODE_6017;
    static final long T1345 = java.time.Instant.parse("2026-11-07T13:45:00Z").toEpochMilli();
    static final long T1350 = java.time.Instant.parse("2026-11-07T13:50:00Z").toEpochMilli();
    /** all-vsan-dp-historical-stats: totalSnapshotCapacity at 13:50. */
    static final double DP_1350 = 64257117696.0;

    public static void main(String[] args) throws Exception {
        requestBody();
        parserOverTheCaptures();
        grammars();
        allScopeOnTheLab();
        scopeSwitchRestarts();
        remoteRule();
        remoteRuleVmTier();
        mixedCaseExclusion();
        System.out.println("ScopeTest: all assertions passed");
    }

    static String capture(EntityType t) throws Exception {
        return T.vendor("all-" + t.id + "-2026-11-07.json");
    }

    /** A capture is a fault body (an object), not an array of entities. */
    static boolean isFault(String body) {
        return body.trim().startsWith("{");
    }

    /** v3 C3: the labels filter under dashboard, none under all (the P3 body shape). */
    static void requestBody() {
        long start = java.time.Instant.parse("2026-11-07T13:47:50Z").toEpochMilli();
        for (EntityType t : EntityType.values()) {
            SimpleJson all = SimpleJson.parse(VsanJsonClient.perfBody(t, EntityType.Scope.ALL,
                    LAB, start, start + CycleEngine.WINDOW_MS)).get("querySpecs").get(0);
            T.check(all.get("labels").isNull(), t.id + ": no labels key under all");
            T.eq(t.id + ":*", all.get("entityRefId").asString(), "wildcard");
            T.eq("2026-11-07T13:54:50Z", all.get("endTime").asString(), "window");
            if (!t.inScope(EntityType.Scope.DASHBOARD)) continue;
            SimpleJson dash = SimpleJson.parse(VsanJsonClient.perfBody(t, EntityType.Scope.DASHBOARD,
                    LAB, start, start + CycleEngine.WINDOW_MS)).get("querySpecs").get(0);
            T.eq(t.labels().size(), dash.get("labels").size(), t.id + ": dashboard labels filter");
        }
    }

    /**
     * Every data capture parses under both scopes. Under all: every catalog
     * label is returned (0 missing, as P3 records) and kept; what is dropped
     * is exactly the 41 Actual twins (pNIC 24, TCP/IP 10, vNIC 7). The ESA
     * dedup types keep all of what they return (v3 addendum Q3: 42 returned,
     * 42 kept, was 24). Under dashboard the same body keeps only the
     * dashboard labels.
     */
    static void parserOverTheCaptures() throws Exception {
        int data = 0;
        int echo = 0;
        int fault = 0;
        Set<String> twins = new HashSet<>();
        for (EntityType t : EntityType.values()) {
            String body = capture(t);
            if (isFault(body)) {
                fault++;
                VsanFault f = VsanFault.parse(body, 500);
                T.check(f != null && f.isTypeUnavailable(), t.id + ": a type-unavailable fault");
                T.eq(t.faultStrikes, f.is(VsanFault.INVALID_ARGUMENT), t.id
                        + ": InvalidArgument exactly on the nine Q5 types");
                continue;
            }
            PerfParser.Result all = PerfParser.parse(body, t, EntityType.Scope.ALL);
            PerfParser.Result streamed = PerfParser.parse(new java.io.ByteArrayInputStream(
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8)), t, EntityType.Scope.ALL);
            T.eq(all.seriesKept, streamed.seriesKept, t.id + ": both parses agree");
            T.eq(all.labelsDropped, streamed.labelsDropped, t.id + ": both parses agree (dropped)");
            if (all.isEmptyEcho()) {
                echo++;
                continue;
            }
            data++;
            T.eq(0, all.malformed, t.id + ": every id in the type's form");
            Set<String> returned = new HashSet<>();
            for (SimpleJson e : SimpleJson.parse(body).asList()) {
                for (SimpleJson s : e.get("value").asList()) {
                    returned.add(s.get("metricId").get("label").asString());
                }
            }
            for (EntityType.Metric m : t.metrics(EntityType.Scope.ALL)) {
                T.check(returned.contains(m.label), t.id + " returns catalog label " + m.label);
            }
            for (String l : all.droppedNames) {
                T.check(l.endsWith("Actual") && t.metric(l.substring(0, l.length() - 6),
                        EntityType.Scope.ALL) != null, t.id + ": only Actual twins dropped: " + l);
                twins.add(t.id + " " + l);
            }
            T.eq(all.entities.size() * t.size(EntityType.Scope.ALL),
                    all.seriesSeen - all.labelsDropped, t.id + ": every entity, every catalog label");
            PerfParser.Result dash = PerfParser.parse(body, t, EntityType.Scope.DASHBOARD);
            for (PerfParser.Entity e : dash.entities) {
                for (PerfParser.Sample s : e.samples) {
                    T.check(s.metric.dashboard, t.id + ": dashboard parse keeps dashboard rows only");
                }
            }
        }
        T.eq(41, data, "P3: 41 types with data");
        T.eq(17, echo, "P3: 17 empty echoes (6 shipped + 11 new)");
        T.eq(11, fault, "P3: 11 faults (9 InvalidArgument, 2 NotSupported)");
        T.eq(41, twins.size(), "the 41 Actual twins are the only labels dropped");
        PerfParser.Result dedup = PerfParser.parse(capture(EntityType.CLUSTER_ESA_DEDUP_DOMCOMPMGR_IO),
                EntityType.CLUSTER_ESA_DEDUP_DOMCOMPMGR_IO, EntityType.Scope.ALL);
        T.eq(42, dedup.seriesSeen, "dedup: 42 returned");
        T.eq(0, dedup.labelsDropped, "dedup: 42 kept (24 model + 18 P3)");
        PerfParser.Result owner = PerfParser.parse(capture(EntityType.HOST_DOMOWNER),
                EntityType.HOST_DOMOWNER, EntityType.Scope.DASHBOARD);
        T.eq(1440 - 5 * 38, owner.labelsDropped, "dashboard parse of an unfiltered body drops the rest");
    }

    /** The new grammars over the captures, and the part-count rule (synthetic ids). */
    static void grammars() throws Exception {
        PerfParser.Result cpu = PerfParser.parse(capture(EntityType.HOST_CPU), EntityType.HOST_CPU,
                EntityType.Scope.ALL);
        T.eq(165, cpu.entities.size(), "host-cpu: 160 CPUs + 5 aggregates");
        int aggregates = 0;
        for (PerfParser.Entity e : cpu.entities) {
            if (e.ref.instance == null) aggregates++;
            else T.check(e.ref.instance.matches("cpu-\\d+"), "cpu instance " + e.ref.instance);
        }
        T.eq(5, aggregates, "one 1-part aggregate per host");
        PerfParser.Result world = PerfParser.parse(capture(EntityType.DOM_WORLD_CPU),
                EntityType.DOM_WORLD_CPU, EntityType.Scope.ALL);
        T.eq(330, world.entities.size(), "dom-world-cpu: 66 worlds x 5 hosts");
        Set<String> perHost = new HashSet<>();
        for (PerfParser.Entity e : world.entities) {
            T.check(e.ref.instance.matches("VSAN_0x[0-9a-f]+_(CompServer|Owner|OwnerHlpr0|Client)-\\d+"),
                    "world instance " + e.ref.instance);
            T.check(perHost.add(e.ref.uuid + "|" + e.ref.instance), "no repeated world per host");
        }
        T.eq("vSAN Perf|DOM World:VSAN_0x432abcf22c40_CompServer-2099225|usedPct",
                EntityType.DOM_WORLD_CPU.statKey(EntityRef.parse("dom-world-cpu:" + NODE_6017
                        + "|VSAN_0x432abcf22c40_CompServer|2099225", EntityType.DOM_WORLD_CPU).instance,
                        EntityType.DOM_WORLD_CPU.metric("usedPct", EntityType.Scope.ALL)), "world key");
        // Synthetic: wrong part counts are unparsed, never guessed.
        T.eq(null, EntityRef.parse("dom-world-cpu:n|w", EntityType.DOM_WORLD_CPU), "2 parts");
        T.eq(null, EntityRef.parse("host-cpu:n|cpu-1|x", EntityType.HOST_CPU), "host-cpu 3 parts");
        T.eq(null, EntityRef.parse("host-cpu:n|", EntityType.HOST_CPU), "empty part");
        T.eq("iqn.1998-01.com.vmware:tgt/0", EntityRef.parse("vsan-iscsi-lun:n|iqn.1998-01.com.vmware:tgt|0",
                EntityType.VSAN_ISCSI_LUN).instance, "synthetic LUN instance");
        T.eq("n|x", EntityRef.parse("remote-virtual-disk:n|x", EntityType.REMOTE_VIRTUAL_DISK).uuid,
                "a non-path remote vdisk id is kept whole (vdisks_unresolved, not unparsed)");
        T.eq(null, EntityRef.parse("virtual-disk:n|x", EntityType.VIRTUAL_DISK), "virtual-disk unchanged");
        T.eq(null, EntityRef.parse("system-mem:n|x", EntityType.SYSTEM_MEM), "one-part type, two parts");
    }

    /** The lab vCenter answering every type with its P3 capture (faults thrown as served). */
    static EngineTest.FakeVc p3Vc() throws Exception {
        EngineTest.FakeVc vc = EngineTest.labVc();
        for (EntityType t : EntityType.values()) {
            String body = capture(t);
            vc.answers.put(LAB + "|" + t.id, isFault(body) ? VsanFault.parse(body, 500) : body);
        }
        return vc;
    }

    static List<EntityType> types(String scope) {
        return new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null, null, null, null,
                null, null, "", scope).types;
    }

    static CycleEngine engine(EngineTest.FakeVc vc, EngineTest.FakeOps ops, T.RecLog log,
            EntityType.Scope scope) {
        return new CycleEngine(vc, ops, new CycleEngine.Settings(new ClusterFilter("", ""), 4,
                30_000, true, types(scope.label), 3, scope), log, System::currentTimeMillis);
    }

    static long in(long ms) {
        return System.currentTimeMillis() + ms;
    }

    /**
     * Default tiers under all on the lab (v3 call plan and T5 steps 2 to 5,
     * with the addendum's Q5): 56 calls on cycle 1 (the two PMem types
     * fault-learned at once), 54 on cycles 2 and 3 while the four host-tier
     * Q5 types fault as optional failures, 37 from cycle 4 (13 empty types,
     * the 4 strike types and the 2 PMem types learned).
     */
    static void allScopeOnTheLab() throws Exception {
        EngineTest.FakeVc vc = p3Vc();
        EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        T.eq(56, types("all").size(), "default tiers under all: 56 types");
        CycleEngine e = engine(vc, ops, log, EntityType.Scope.ALL);
        int core = EntityType.Tier.CORE.ordinal();
        int host = EntityType.Tier.HOST.ordinal();
        int cl = EntityType.Tier.CLUSTER.ordinal();

        CycleEngine.Result r = e.run(in(60_000));
        T.eq(null, r.failure, "cycle ok");
        T.eq(56, r.perfCalls, "cycle 1: 56 calls");
        T.eq(3, r.tierCalls[core], "core");
        T.eq(34, r.tierCalls[host], "host tier");
        T.eq(19, r.tierCalls[cl], "cluster tier");
        for (EntityType t : types("all")) {
            T.eq(EntityType.Scope.ALL, vc.scopes.get(LAB + "|" + t.id), t.id + " asked under all");
        }
        // Both buckets of every catalog series with a value (computed from the captures).
        T.eq(4120, r.tierSeriesPushed[core], "core: 2,060 newest values, two buckets");
        T.eq(8700, r.tierSeriesPushed[host], "host tier");
        T.eq(696, r.tierSeriesPushed[cl], "cluster tier");
        T.eq(4120 + 8700 + 696, r.seriesPushed, "cycle 1 values");
        T.eq(240, r.tierLabelsDropped[core], "pNIC Actual twins: 24 x 10 entities");
        T.eq(85, r.tierLabelsDropped[host], "TCP/IP 10 x 5 + vNIC 7 x 5");
        T.eq(4, r.optionalCallsFailed, "Q5: clom-host, cmmds-workload, heap-memory, slab-memory");
        T.eq(4, r.tierFailed[host], "counted on the host tier");
        T.eq(0, r.hostsUnresolved + r.clustersUnresolved + r.entitiesUnparsed, "all resolved");
        T.check(r.lastError != null && r.lastError.contains("InvalidArgument"), "last_error names it");
        String text = log.text();
        T.check(text.contains("PERF tier cycle=1 tier=core calls=3 ") && text.contains(
                "failed=0 scope=all labels_dropped=240"), "PERF tier scope and labels_dropped");
        T.check(text.contains("; scope=all; "), "summary scope");
        T.eq(1, log.count("vsan-pnic-net: labels not in this pack's all-scope catalog"),
                "dropped-label INFO for pNIC");
        T.check(text.contains("ioChainDropsActual"), "names the dropped labels");
        T.eq(2, log.count("does not offer"), "the two PMem types learned at once");
        T.eq(4, log.count("an optional host-tier type"), "Q5: one WARN per type");

        Map<String, Double> esx01 = ops.pushed.get("rid-host-6017");
        T.near(144228, esx01.get("vSAN Perf|System Memory|totalMbMemUsed@" + T1345), "MiB, as served");
        T.near(56, esx01.get("vSAN Perf|System Memory|pctMemUsed@" + T1345),
                "Q4: integer percent, no transform");
        T.near(55, esx01.get("vSAN Perf|System Memory|pctMemUsed@" + T1350), "truncated, as served");
        T.check(esx01.containsKey("vSAN Perf|pCPU|pcpuUsedPct@" + T1350), "pCPU aggregate");
        T.check(esx01.containsKey("vSAN Perf|pCPU:cpu-0|pcpuUsedPct@" + T1350), "pCPU per CPU");
        int pcpu = 0;
        int worlds = 0;
        for (String k : esx01.keySet()) {
            if (k.startsWith("vSAN Perf|pCPU")) pcpu++;
            if (k.startsWith("vSAN Perf|DOM World:VSAN_0x")) worlds++;
            T.check(!k.contains("Actual@"), "no twin pushed: " + k);
        }
        T.eq(33 * 3 * 2, pcpu, "pCPU: 32 CPUs + aggregate, 3 keys, two buckets");
        T.eq(66 * 3 * 2, worlds, "66 DOM worlds, 3 keys, two buckets");
        Map<String, Double> cluster = ops.pushed.get(CL_RID);
        T.near(DP_1350, cluster.get("vSAN Perf|Data Protection|totalSnapshotCapacity@" + T1350),
                "Data Protection on the cluster, as served");
        T.check(cluster.containsKey("vSAN Perf|ESA Dedup DOM Owner|dedupChunkReadCongestion@" + T1350),
                "a P3 label (addendum Q3) on the cluster");
        T.check(esx01.containsKey("vSAN Perf|ESA Dedup DOM Client|iopsDedupHashObjUnmap@" + T1350),
                "a P3 label on a shared dashboard type, under all");

        CycleEngine.Result r2 = e.run(in(60_000));
        T.eq(54, r2.perfCalls, "cycle 2: the PMem types learned");
        T.eq(0, r2.seriesPushed, "same buckets: nothing pushed twice");
        T.eq(4, r2.optionalCallsFailed, "still faulting");
        T.eq(1, log.count("vsan-pnic-net: labels not in"), "dropped-label INFO once a day");
        CycleEngine.Result r3 = e.run(in(60_000));
        T.eq(54, r3.perfCalls, "cycle 3");
        T.eq(4, log.count("faulted (InvalidArgument) 3 cycles in a row"), "strike INFO");
        T.eq(13, log.count("answered empty 3 cycles in a row"), "13 empty types learned");
        T.eq(4, log.count("an optional host-tier type"), "WARN once per type per refresh");
        CycleEngine.Result r4 = e.run(in(60_000));
        T.eq(37, r4.perfCalls, "steady under all (design: 37)");
        T.eq(19, r4.learnedSkips, "13 empty + 4 strikes + 2 PMem");
        T.eq(0, r4.optionalCallsFailed, "no optional failure once learned");
        T.eq(4, vc.calls(LAB, EntityType.IOINSIGHT), "ioinsight exempt");
        T.eq(3, vc.calls(LAB, EntityType.CLOM_HOST), "clom-host: three faults, then learned");
        T.eq(1, vc.calls(LAB, EntityType.HOST_PMEM), "host-pmem: learned at the first fault");
        T.eq(4, vc.calls(LAB, EntityType.HOST_CPU), "host-cpu every cycle");
    }

    /**
     * v3 "Setting" step 5: a scope change is a reconfigure, which builds a new
     * engine: the learned types and the dedup marks start over (T5 step 7).
     * The same unfiltered bodies serve both scopes here, so the dashboard
     * engine's pushes also prove the dashboard parse keeps exactly the
     * dashboard keys (2,372 values, the build 10 figure).
     */
    static void scopeSwitchRestarts() throws Exception {
        EngineTest.FakeVc vc = p3Vc();
        EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
        CycleEngine dash = engine(vc, ops, new T.RecLog(), EntityType.Scope.DASHBOARD);
        CycleEngine.Result d1 = dash.run(in(60_000));
        T.eq(26, d1.perfCalls, "dashboard: 26 calls");
        T.eq(2372, d1.seriesPushed, "dashboard keys only, two buckets");
        for (int i = 0; i < 3; i++) dash.run(in(60_000));
        T.eq(23, dash.run(in(60_000)).perfCalls, "dashboard steady 23");
        for (EntityType t : EntityType.values()) {
            if (t.allOnly) T.eq(0, vc.calls(LAB, t), t.id + " never queried under dashboard");
        }
        CycleEngine all = engine(vc, ops, new T.RecLog(), EntityType.Scope.ALL);
        CycleEngine.Result a1 = all.run(in(60_000));
        T.eq(56, a1.perfCalls, "switch to all: every type probed (learned state reset)");
        T.eq(0, a1.learnedSkips, "nothing learned yet");
        T.eq(13516, a1.seriesPushed, "both buckets again (marks reset)");
        CycleEngine back = engine(vc, ops, new T.RecLog(), EntityType.Scope.DASHBOARD);
        CycleEngine.Result b1 = back.run(in(60_000));
        T.eq(26, b1.perfCalls, "switch back: 26, the dashboard empties re-probed");
        T.eq(2372, b1.seriesPushed, "both buckets, dashboard keys only");
    }

    /**
     * v3 Q6 (addendum): remote types land only on an object of the queried
     * cluster; anything else is skipped and counted in clusters_unresolved,
     * WARN once per refresh, never landed on another in-scope cluster (a
     * second, synthetic cluster here, whose host is known).
     */
    static void remoteRule() throws Exception {
        EngineTest.FakeVc vc = p3Vc();
        List<Inventory.Cluster> cls = new ArrayList<>(vc.inventory.clusters);
        String otherNode = "00000000-synthetic-node-of-another-cluster";
        cls.add(new Inventory.Cluster("domain-c9999", "synthetic-other", List.of(new Inventory.Host(
                "host-9999", "esx-other", otherNode, "00000000-synthetic-other-cluster", true, null))));
        vc.inventory = new Inventory(cls);
        EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
        String head = "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"";
        String tail = "\",\"sampleInfo\":\"2026-11-07 13:50:00\",\"value\":["
                + PerfParserTest.series("iopsRead", "7") + "]}";
        vc.answers.put(LAB + "|host-remotedomclient", "[" + head + "host-remotedomclient:" + NODE_6017
                + tail + "," + head + "host-remotedomclient:" + otherNode + tail + "," + head
                + "host-remotedomclient:00000000-synthetic-cross-vcenter" + tail + "]");
        vc.answers.put(LAB + "|cluster-remotedomclient", "[" + head + "cluster-remotedomclient:"
                + CLUSTER_UUID + tail + "," + head + "cluster-remotedomclient:"
                + "00000000-synthetic-other-cluster" + tail + "]");
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops, log, EntityType.Scope.ALL);
        CycleEngine.Result r = e.run(in(60_000));
        T.near(7, ops.pushed.get("rid-host-6017").get("vSAN Perf|Remote DOM Client|iopsRead@" + T1350),
                "a node of the queried cluster: lands");
        T.near(7, ops.pushed.get(CL_RID).get("vSAN Perf|Remote DOM Client|iopsRead@" + T1350),
                "the queried cluster's uuid: lands");
        Map<String, Double> other = ops.pushed.get("rid-host-9999");
        T.check(other == null || !other.containsKey("vSAN Perf|Remote DOM Client|iopsRead@" + T1350),
                "never on another in-scope cluster's host");
        T.eq(3, r.clustersUnresolved, "the other cluster's node, a cross-vCenter node, the other "
                + "cluster's uuid");
        T.eq(0, r.hostsUnresolved, "not host misses");
        T.eq(1, log.count("remote (HCI Mesh) entity id(s)"), "WARN");
        T.check(!log.text().contains("cross-vcenter"), "the id is never logged");
        e.run(in(60_000));
        T.eq(1, log.count("remote (HCI Mesh) entity id(s)"), "WARN once per refresh");
    }

    /**
     * Build 20 (review N3): the remote rule on the VM tier. A
     * {@code remote-virtual-machine} or {@code remote-vscsi} VM uuid in the
     * queried cluster's VM read, and a {@code remote-virtual-disk} path in
     * its device map, land on that VM (the vdisk at its bus position); any
     * other VM uuid, path or a non-path id is dropped and counted in
     * clusters_unresolved, never in vms/vdisks_unresolved, and does not ask
     * for a VM re-read. Synthetic remote answers; the VM read and the Ops VM
     * list are the lab captures.
     */
    static void remoteRuleVmTier() throws Exception {
        EngineTest.FakeVc vc = DiskVmEngineTest.vc();
        VmInventory.Builder vb = new VmInventory.Builder(true);
        for (String page : (String[]) vc.vmPages.get(LAB)) vb.addPage(page);
        VmInventory vms = vb.build();
        String vmUuid = null;
        for (SimpleJson e : SimpleJson.parse(T.vendor("queryperf-virtual-machine-2026-11-06.json"))
                .asList()) {
            String u = e.get("entityRefId").asString().substring("virtual-machine:".length());
            if (vms.vmMoid(u) != null) {
                vmUuid = u;
                break;
            }
        }
        String path = null;
        for (SimpleJson e : SimpleJson.parse(T.vendor("queryperf-virtual-disk-2026-11-06.json"))
                .asList()) {
            String p = e.get("entityRefId").asString().substring("virtual-disk:".length());
            p = p.startsWith("/") ? p.substring(1) : p;
            VmInventory.Disk d = vms.disk(p);
            if (d != null && d != VmInventory.UNPLACEABLE) {
                path = p;
                break;
            }
        }
        T.check(vmUuid != null && path != null, "a placeable VM and vdisk in the lab read");
        String head = "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"";
        String tail = "\",\"sampleInfo\":\"2026-11-07 13:50:00\",\"value\":["
                + PerfParserTest.series("iopsRead", "7") + "," + PerfParserTest.series("NIOPS", "9")
                + "]}";
        String foreign = "00000000-synthetic-vm-computing-elsewhere";
        vc.answers.put(LAB + "|remote-virtual-machine", "[" + head + "remote-virtual-machine:" + vmUuid
                + tail + "," + head + "remote-virtual-machine:" + foreign + tail + "]");
        vc.answers.put(LAB + "|remote-vscsi", "[" + head + "remote-vscsi:" + vmUuid + "|scsi0:0" + tail
                + "," + head + "remote-vscsi:" + foreign + "|scsi0:0" + tail + "]");
        vc.answers.put(LAB + "|remote-virtual-disk", "[" + head + "remote-virtual-disk:" + path + tail
                + "," + head + "remote-virtual-disk:synthetic-ns/elsewhere.vmdk" + tail + "," + head
                + "remote-virtual-disk:synthetic|not-a-path" + tail + "]");
        EngineTest.FakeOps ops = DiskVmEngineTest.ops(vc.inventory);
        List<EntityType> types = new ArrayList<>();
        Collections.addAll(types, EntityType.HOST_DOMOWNER, EntityType.REMOTE_VIRTUAL_MACHINE,
                EntityType.REMOTE_VSCSI, EntityType.REMOTE_VIRTUAL_DISK);
        T.RecLog log = new T.RecLog();
        CycleEngine e = new CycleEngine(vc, ops, new CycleEngine.Settings(new ClusterFilter("", ""),
                4, 30_000, true, types, 1, EntityType.Scope.ALL), log, System::currentTimeMillis);
        CycleEngine.Result r = e.run(in(60_000));
        T.eq(null, r.failure, "cycle ok");
        int vmKeys = 0;
        String vmRid = null;
        for (Map.Entry<String, Map<String, Double>> res : ops.pushed.entrySet()) {
            for (String k : res.getValue().keySet()) {
                if (k.startsWith("vSAN Perf|Remote VM|") || k.startsWith("vSAN Perf|Remote vSCSI:")
                        || k.startsWith("vSAN Perf|Remote Virtual Disk:")) {
                    vmKeys++;
                    T.check(vmRid == null || vmRid.equals(res.getKey()) || k.startsWith(
                            "vSAN Perf|Remote Virtual Disk:"), "the uuid's VM only: " + k);
                    if (!k.startsWith("vSAN Perf|Remote Virtual Disk:")) vmRid = res.getKey();
                }
            }
        }
        T.check(vmRid != null, "remote VM keys landed on a VM of the read");
        T.near(7, ops.pushed.get(vmRid).get("vSAN Perf|Remote VM|iopsRead@" + T1350), "Remote VM");
        T.near(7, ops.pushed.get(vmRid).get("vSAN Perf|Remote vSCSI:scsi0:0|iopsRead@" + T1350),
                "Remote vSCSI");
        VmInventory.Disk d = vms.disk(path);
        boolean vdiskLanded = false;
        for (Map<String, Double> m : ops.pushed.values()) {
            Double v = m.get("vSAN Perf|Remote Virtual Disk:" + d.busPosition + "|NIOPS@" + T1350);
            if (v != null) vdiskLanded = true;
        }
        T.check(vdiskLanded, "remote vdisk at its bus position " + d.busPosition);
        T.eq(3, vmKeys, "one key each (the other label is not in that type's catalog), nothing else");
        T.eq(3, r.clustersUnresolved, "the foreign VM uuid (its vSCSI device carries the same "
                + "uuid and counts once), the foreign path, the non-path id");
        T.eq(0, r.vmsUnresolved + r.vdisksUnresolved, "not VM or vdisk misses");
        T.eq(0, r.entitiesUnparsed, "a non-path remote vdisk id is not unparsed");
        T.eq(1, log.count("remote (HCI Mesh) entity id(s)"), "one WARN");
        T.check(!log.text().contains("synthetic-vm-computing-elsewhere"), "no id logged");
    }

    /**
     * Build 21 (Codex on PR #11): excluding the two mixed-case types, as an
     * operator types them or in lower case, keeps them out of the cycle:
     * never queried, not reported as unknown, the rest unchanged (54 calls).
     */
    static void mixedCaseExclusion() throws Exception {
        VsanPerfConfig cfg = new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null,
                null, null, null, null, null,
                "computecluster-remotedomclient,computeHost-remotedomclient", "all");
        T.check(cfg.unknownExcludes.isEmpty(), "not unknown");
        EngineTest.FakeVc vc = p3Vc();
        CycleEngine e = new CycleEngine(vc, TiersEngineTest.labOps(vc.inventory),
                new CycleEngine.Settings(new ClusterFilter("", ""), 4, 30_000, false, cfg.types, 3,
                        cfg.scope), new T.RecLog(), System::currentTimeMillis);
        CycleEngine.Result r = e.run(in(60_000));
        T.eq(54, r.perfCalls, "56 default-tier types under all, two excluded");
        T.eq(0, vc.calls(LAB, EntityType.COMPUTECLUSTER_REMOTEDOMCLIENT), "computeCluster not queried");
        T.eq(0, vc.calls(LAB, EntityType.COMPUTEHOST_REMOTEDOMCLIENT), "computeHost not queried");
    }
}
