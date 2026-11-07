package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Engine-level T1 for build 11: the disk and VM tiers on the lab from the
 * verbatim captures (perf types, QueryDisksForVsan, VM pages, the Ops
 * VirtualMachine list of devel), the per-cycle call counts with all tiers,
 * the disk map's 12-cycle miss limit, the VM cadence, one VM call in
 * flight, and review W1 (an optional-type failure does not fail the
 * cluster or the cycle).
 *
 * <p>Constructed, and labelled so: the hosts' {@code configManager.vsanSystem}
 * moIds ({@code vsanSystem-<host number>}, the form P2 recorded; the R2
 * capture predates the path), and the QueryDisksForVsan answers of four of
 * the five hosts (only host-15151's body was saved; the uuid-to-host table
 * is the api map's P2 table). The VM read serves the two saved device pages
 * then light pages 3 to 5 (same chain order, P2), so VMs 21 to 42 carry no
 * device list and their vdisks stay unresolved here.
 */
public final class DiskVmEngineTest {

    static final String LAB = EngineTest.LAB;
    static final String VCID = "4ff53df1-d47a-4fb9-b6f8-b96c6ce8ae8e";
    /** api map P2 "ESA disk id = QueryDisksForVsan vsanUuid". */
    static final String[][] DISKS = {
            {"52d171f6-2e48-758a-777f-d5098533d861", "host-15151"},
            {"52c7ed3e-9dcf-b82e-8177-f704806c2e4a", "host-31189"},
            {"52bde68f-b0e5-6952-075b-bcb49bd54fa6", "host-6015"},
            {"52585910-fedf-604c-04d3-868955bf7f7d", "host-6017"},
            {"52850722-bc18-61ea-8400-190ec367ac70", "host-6011"}};
    static final long T0505 = PerfParserTest.T0505;

    public static void main(String[] args) throws Exception {
        allTiersOnTheLab();
        diskMissRateLimited();
        optionalFailureDoesNotFailTheCycle();
        oneVmCallInFlight();
        vmReadEvery12thRun();
        vmReadsSerialize();
        diskTotalFailureRateLimited();
        failedRefreshSuppressesVirtualDisk();
        System.out.println("DiskVmEngineTest: all assertions passed");
    }

    /** The lab inventory with each host's vsanSystem moId (constructed, see the class doc). */
    static Inventory labInventoryWithVsanSystem() throws Exception {
        Inventory.Cluster c = EngineTest.labInventory().clusters.get(0);
        List<Inventory.Host> hosts = new ArrayList<>();
        for (Inventory.Host h : c.hosts) {
            hosts.add(new Inventory.Host(h.moid, h.name, h.nodeUuid, h.vsanClusterUuid,
                    h.vsanEnabled, "vsanSystem-" + h.moid.substring("host-".length())));
        }
        return new Inventory(List.of(new Inventory.Cluster(c.moid, c.name, hosts)));
    }

    static EngineTest.FakeVc vc() throws Exception {
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        vc.uuid = VCID;
        vc.inventory = labInventoryWithVsanSystem();
        vc.answers.put(LAB + "|vsan-esa-disk-layer", T.vendor("queryperf-vsan-esa-disk-layer-2026-11-06.json"));
        vc.answers.put(LAB + "|vsan-esa-disk-scsifw", T.vendor("queryperf-vsan-esa-disk-scsifw-2026-11-06.json"));
        vc.answers.put(LAB + "|virtual-machine", T.vendor("queryperf-virtual-machine-2026-11-06.json"));
        vc.answers.put(LAB + "|virtual-disk", T.vendor("queryperf-virtual-disk-2026-11-06.json"));
        vc.answers.put(LAB + "|vscsi", T.vendor("queryperf-vscsi-2026-11-06.json"));
        for (String[] d : DISKS) {
            String vs = "vsanSystem-" + d[1].substring("host-".length());
            vc.disks.put(vs, d[1].equals("host-15151")
                    ? Inventory.vsanDiskUuids(T.vendor("querydisksforvsan-host-15151-2026-11-06.json"))
                    : List.of(d[0]));
        }
        vc.vmPages.put(LAB, new String[] {
                T.vendor("retrievepropertiesex-vm-cluster-devices-max10-page1-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-devices-max10-page2-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page3-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page4-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page5-2026-11-06.json")});
        return vc;
    }

    static EngineTest.FakeOps ops(Inventory inv) throws Exception {
        EngineTest.FakeOps ops = TiersEngineTest.labOps(inv);
        ops.vcid = VCID;
        ops.vmList = T.vendor("ops-devel-resources-virtualmachine-2026-11-06.json");
        return ops;
    }

    static CycleEngine engine(EngineTest.FakeVc vc, EngineTest.FakeOps ops, T.RecLog log,
            List<EntityType> types, int n, int workers) {
        return new CycleEngine(vc, ops, new CycleEngine.Settings(new ClusterFilter("", ""),
                workers, 30_000, true, types, n), log, T.CLOCK::get);
    }

    static List<EntityType> allTypes() {
        return new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null, null, null,
                "true", "true", null, "").types;
    }

    static long in(long ms) {
        return T.in(ms);
    }

    static void allTiersOnTheLab() throws Exception {
        EngineTest.FakeVc vc = vc();
        EngineTest.FakeOps ops = ops(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops, log, allTypes(), 3, 4);
        int vmIdx = EntityType.Tier.VM.ordinal();
        int diskIdx = EntityType.Tier.DISK.ordinal();
        List<Integer> calls = new ArrayList<>();
        List<Boolean> runs = new ArrayList<>();
        CycleEngine.Result first = null;
        CycleEngine.Result firstVm = null;
        for (int cycle = 1; cycle <= 9; cycle++) {
            boolean run = e.vmRun(LAB, cycle);
            CycleEngine.Result r = e.run(in(60_000));
            T.eq(null, r.failure, "cycle " + cycle + " ok");
            T.check(vc.lastWithVsanSystem, "R2 asks for configManager.vsanSystem with the disk tier");
            calls.add(r.perfCalls);
            runs.add(run);
            T.eq(run ? 3 : 0, r.tierCalls[vmIdx], "VM calls only on the cluster's VM cycles");
            if (cycle == 1) first = r;
            if (run && firstVm == null) {
                firstVm = r;
                // virtual-machine 354 + vscsi 2,360 + the 57 resolvable vdisks' 339 values.
                T.eq(354 + 2360 + 339, r.tierSeriesPushed[vmIdx], "VM tier values, first VM run");
                T.eq(0, r.vmsUnresolved, "every perf VM placed (MoRef, VCID) on devel's list");
                T.eq(148 - 57, r.vdisksUnresolved, "vdisks of VMs with no saved device page");
                T.eq(1, vc.vmReads.get(), "one VM read");
                T.check(vc.lastVmDevices, "with the device list (virtual-disk in scope)");
                T.eq(1, ops.vmGets.get(), "one Ops VM list page (109 VMs)");
                Map<String, Double> adfs1 = ops.pushed.get("0b2a707d-b2b7-414c-85a3-2d607a1bd9e9");
                T.check(adfs1 != null, "adfs1 (vm-39030) got VM keys");
                T.check(adfs1.containsKey("vSAN Perf|Virtual Disk:scsi0:0|NIOPS@" + T0505)
                        || adfs1.containsKey("vSAN Perf|Virtual Disk:scsi0:0|NIOPS@"
                        + (T0505 - 300_000)), "vdisk instanced by bus position");
                boolean vscsiKey = false;
                for (String k : adfs1.keySet()) vscsiKey |= k.startsWith("vSAN Perf|vSCSI:scsi0:0|");
                T.check(vscsiKey, "vSCSI:scsi0:0 on the same VM");
                for (String k : adfs1.keySet()) {
                    T.check(!k.contains("|NIOPSRead|") && !k.contains("|NIOPSWrite"),
                            "no NIOPSRead/Write values (all None)");
                }
            }
        }
        T.eq(Boolean.TRUE, runs.contains(true), "the lab cluster ran its VM types");
        // Disk tier: 5 of 5 ESA disks on the right host, two types, two buckets each.
        T.eq(5, vc.diskCalls.get(), "one QueryDisksForVsan per host, once (no miss, < 12 cycles)");
        T.eq(0, first.disksUnresolved, "5 of 5 disk uuids on a host");
        T.eq(100, first.tierSeriesPushed[diskIdx], "disk tier: 50 series x two buckets");
        for (String[] d : DISKS) {
            Map<String, Double> host = ops.pushed.get("rid-" + d[1]);
            T.check(host.containsKey("vSAN Perf|ESA Disk Layer:" + d[0] + "|iopsReadCapacity@"
                    + T0505), d[0] + " on " + d[1]);
            T.check(host.containsKey("vSAN Perf|ESA Disk SCSI:" + d[0] + "|latencyDevDAvgUs@"
                    + T0505), d[0] + " SCSI on " + d[1]);
        }
        // Calls per cycle on the lab with all tiers (design: 33 first, 28 steady on a VM cycle).
        for (int i = 0; i < calls.size(); i++) {
            int want = runs.get(i) ? (i < 3 ? 33 : 28) : (i < 3 ? 30 : 25);
            T.eq(want, calls.get(i), "cycle " + (i + 1) + " calls (VM run: " + runs.get(i) + ")");
        }
        String text = log.text();
        T.check(text.contains("read=disk_map cluster=" + LAB + " calls=5 "), "PERF identity disk_map");
        T.check(text.contains("read=vm_read cluster=" + LAB + " pages=5 "), "PERF identity vm_read");
        T.check(text.contains("read=ops_vms pages=1 "), "PERF identity ops_vms");
        T.check(text.contains("vm_run=yes") && text.contains("vm_run=no"), "PERF cluster vm_run");
        T.check(text.contains("vm_permit_wait_ms_max="), "PERF pool permit wait");
        T.check(text.contains("kind=VirtualMachine"), "PERF push kind VirtualMachine");
        T.check(text.contains("tier=vm parse=stream"), "PERF call tier vm");
        T.check(text.contains("calls_disk=") && text.contains("calls_vm="), "PERF cycle tiers");
        System.out.println("DiskVmEngineTest: lab calls per cycle, all tiers, N=3: " + calls
                + " (VM run cycles " + runs + "); marks " + e.marksSize() + " label marks");
    }

    /** A disk uuid not in the map: counted, re-read at most once per 12 cycles per cluster. */
    static void diskMissRateLimited() throws Exception {
        EngineTest.FakeVc vc = vc();
        vc.disks.put("vsanSystem-6017", List.of()); // host-6017 reports no vSAN disk at first
        EngineTest.FakeOps ops = ops(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops, log, EntityType.ofTiers(EnumSet.of(EntityType.Tier.CORE,
                EntityType.Tier.DISK)), 3, 1);
        CycleEngine.Result r1 = e.run(in(60_000));
        T.eq(1, r1.disksUnresolved, "the missing disk counted");
        T.eq(null, r1.failure, "a miss does not fail the cycle");
        T.eq(1, log.count("vSAN disk uuid(s) in the performance data are on no host"), "WARN");
        T.eq(5, vc.diskCalls.get(), "first read");
        for (int i = 2; i <= 12; i++) e.run(in(60_000));
        T.eq(5, vc.diskCalls.get(), "no re-read within 12 cycles of the last read");
        vc.disks.put("vsanSystem-6017", List.of(DISKS[3][0]));
        CycleEngine.Result r13 = e.run(in(60_000));
        T.eq(10, vc.diskCalls.get(), "re-read at the limit");
        T.eq(0, r13.disksUnresolved, "found now");

        // A host whose QueryDisksForVsan faults: its disks unresolved, the rest map.
        EngineTest.FakeVc vc2 = vc();
        vc2.disks.put("vsanSystem-6011", new IOException("QueryDisksForVsan returned HTTP 503"));
        T.RecLog log2 = new T.RecLog();
        CycleEngine.Result r = engine(vc2, ops(vc2.inventory), log2, EntityType.ofTiers(
                EnumSet.of(EntityType.Tier.CORE, EntityType.Tier.DISK)), 3, 1).run(in(60_000));
        T.eq(null, r.failure, "succeeds");
        T.eq(1, r.disksUnresolved, "host-6011's disk only");
        T.check(log2.text().contains("QueryDisksForVsan failed on 1 host(s)"), "WARN");
        T.check(r.lastError.contains("QueryDisksForVsan"), "last_error");
    }

    /** Review W1: one optional type faults on a one-cluster scope; the cycle succeeds. */
    static void optionalFailureDoesNotFailTheCycle() throws Exception {
        EngineTest.FakeVc vc = TiersEngineTest.labVc();
        vc.answers.put(LAB + "|vsan-file-service", new IOException("vsan-file-service: HTTP 502"));
        EngineTest.FakeOps ops = TiersEngineTest.labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine e = TiersEngineTest.engine(vc, ops, log, TiersEngineTest.defaultTypes());
        CycleEngine.Result r = e.run(in(60_000));
        T.eq(null, r.failure, "the cycle succeeds (was: every vSAN cluster in scope failed)");
        T.eq(1, r.clustersOk, "the cluster is OK");
        T.eq(0, r.clustersFailed, "not failed");
        T.eq(1, r.optionalCallsFailed, "counted");
        T.eq(1, r.tierFailed[EntityType.Tier.CLUSTER.ordinal()], "per tier");
        T.eq(2372, r.seriesPushed, "all other data pushed");
        T.check(r.lastError.contains("vsan-file-service"), "last_error: " + r.lastError);
        T.eq(1, log.count("an optional cluster-tier type"), "one WARN");
        T.eq(Double.valueOf(1), r.metrics().get("Collection|optional_calls_failed"), "self-metric");
        T.check(log.text().contains("tier=cluster calls=10 ") && log.text().contains(" failed=1"),
                "PERF tier failed=");
        e.run(in(60_000));
        T.eq(1, log.count("an optional cluster-tier type"), "not repeated before the next refresh");

        // A core type still fails the cluster (and a one-cluster cycle).
        EngineTest.FakeVc core = TiersEngineTest.labVc();
        core.answers.put(LAB + "|host-domowner", new IOException("host-domowner: HTTP 502"));
        CycleEngine.Result rc = TiersEngineTest.engine(core, TiersEngineTest.labOps(core.inventory),
                new T.RecLog(), TiersEngineTest.defaultTypes()).run(in(60_000));
        T.eq(1, rc.clustersFailed, "core failure fails the cluster");
        T.check(rc.failure != null, "and the one-cluster cycle");

        // Every call failing fails the cluster too.
        EngineTest.FakeVc all = TiersEngineTest.labVc();
        for (EntityType t : TiersEngineTest.defaultTypes()) {
            if (t.tier != EntityType.Tier.CORE) all.answers.put(LAB + "|" + t.id, new IOException("x"));
        }
        all.answers.remove(LAB + "|host-domowner");
        CycleEngine.Result ra = TiersEngineTest.engine(all, TiersEngineTest.labOps(all.inventory),
                new T.RecLog(), TiersEngineTest.defaultTypes()).run(in(60_000));
        T.eq(0, ra.clustersFailed, "one call answered: not every call failed");
        T.eq(23, ra.optionalCallsFailed, "23 optional failures counted");
    }

    /**
     * Review B1 (build 11): with no miss at all, the VM read is still re-run
     * every 12th VM run of the cluster (v2 device-list caching (b)). N = 1,
     * virtual-machine and vscsi from the captures, all of them placed by the
     * light read (no miss); virtual-disk excluded.
     */
    static void vmReadEvery12thRun() throws Exception {
        EngineTest.FakeVc vc = vc();
        vc.vmPages.put(LAB, new String[] {
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page1-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page2-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page3-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page4-2026-11-06.json"),
                T.vendor("retrievepropertiesex-vm-cluster-light-max10-page5-2026-11-06.json")});
        List<EntityType> types = new ArrayList<>(EntityType.ofTiers(EnumSet.of(EntityType.Tier.CORE)));
        types.add(EntityType.VIRTUAL_MACHINE);
        types.add(EntityType.VSCSI);
        CycleEngine e = engine(vc, ops(vc.inventory), new T.RecLog(), types, 1, 1);
        for (int cycle = 1; cycle <= 12; cycle++) {
            CycleEngine.Result r = e.run(in(60_000));
            T.eq(0, r.vmsUnresolved, "cycle " + cycle + ": no miss");
            T.eq(1, vc.vmReads.get(), "cycle " + cycle + ": one VM read so far");
            T.check(!vc.lastVmDevices, "light read (virtual-disk excluded)");
        }
        e.run(in(60_000));
        T.eq(2, vc.vmReads.get(), "cycle 13: the 12th VM run since the read re-reads, no miss needed");
        for (int cycle = 14; cycle <= 24; cycle++) e.run(in(60_000));
        T.eq(2, vc.vmReads.get(), "not again before the next 12 runs");
        e.run(in(60_000));
        T.eq(3, vc.vmReads.get(), "cycle 25: again");
    }

    /**
     * Build 13 (Codex): a VM refresh that fails after an earlier successful
     * device read must not let virtual-disk run on the stale device map. N = 1,
     * VM tier only: cycle 1 reads; the 12th-run refresh at cycle 13 fails, so
     * virtual-disk is not called that run while virtual-machine and vscsi are,
     * still placed by the previous read; cycle 14 retries, succeeds, and
     * virtual-disk resumes.
     */
    static void failedRefreshSuppressesVirtualDisk() throws Exception {
        EngineTest.FakeVc vc = vc();
        Object pages = vc.vmPages.get(LAB);
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops(vc.inventory), log, EntityType.ofTiers(EnumSet.of(
                EntityType.Tier.CORE, EntityType.Tier.VM)), 1, 1);
        for (int cycle = 1; cycle <= 12; cycle++) e.run(in(60_000));
        int reads = vc.vmReads.get();
        int vdisk = vc.calls(LAB, EntityType.VIRTUAL_DISK);
        int vm = vc.calls(LAB, EntityType.VIRTUAL_MACHINE);
        int vscsi = vc.calls(LAB, EntityType.VSCSI);
        T.eq(12, vdisk, "virtual-disk called on every run while the device map is current");

        vc.vmPages.put(LAB, new IOException("RetrievePropertiesEx (VMs) returned HTTP 503"));
        CycleEngine.Result r13 = e.run(in(60_000));
        T.eq(null, r13.failure, "a failed VM read does not fail the cycle");
        T.eq(reads + 1, vc.vmReads.get(), "cycle 13: the refresh was attempted");
        T.eq(vdisk, vc.calls(LAB, EntityType.VIRTUAL_DISK), "no virtual-disk call on the stale map");
        T.eq(vm + 1, vc.calls(LAB, EntityType.VIRTUAL_MACHINE), "virtual-machine still collected");
        T.eq(vscsi + 1, vc.calls(LAB, EntityType.VSCSI), "vscsi still collected");
        T.eq(0, r13.vmsUnresolved, "placed by the previous read");
        T.check(r13.duplicatesSkipped > 0, "their rows reached the push stage (same buckets: dedup)");
        T.check(r13.lastError.contains("VM read failed"), "last_error: " + r13.lastError);
        T.eq(1, log.count("virtual-disk is not collected for it until a VM read succeeds"),
                "WARN matches the behaviour");

        vc.vmPages.put(LAB, pages);
        CycleEngine.Result r14 = e.run(in(60_000));
        T.eq(reads + 2, vc.vmReads.get(), "cycle 14: retried on the next VM run");
        T.eq(vdisk + 1, vc.calls(LAB, EntityType.VIRTUAL_DISK), "virtual-disk resumes");
        T.eq(null, r14.failure, "ok");
        e.run(in(60_000));
        T.eq(reads + 2, vc.vmReads.get(), "no further read once it succeeded");
    }

    /** Review W1 (build 11): two clusters' VM reads never overlap (one page in memory). */
    static void vmReadsSerialize() throws Exception {
        AtomicInteger inRead = new AtomicInteger();
        AtomicInteger maxInRead = new AtomicInteger();
        EngineTest.FakeVc vc = new EngineTest.FakeVc() {
            @Override public VmInventory readVms(String cluster, boolean withDevices, long t)
                    throws IOException {
                maxInRead.accumulateAndGet(inRead.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(60);
                    return super.readVms(cluster, withDevices, t);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                } finally {
                    inRead.decrementAndGet();
                }
            }
        };
        List<Inventory.Cluster> clusters = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            clusters.add(new Inventory.Cluster("domain-c" + i, "c" + i, List.of(new Inventory.Host(
                    "host-" + i, "h" + i, "node-" + i, "U" + i, true))));
        }
        vc.inventory = new Inventory(clusters);
        CycleEngine.Result r = engine(vc, EngineTest.labOps(vc.inventory), new T.RecLog(),
                EntityType.ofTiers(EnumSet.of(EntityType.Tier.CORE, EntityType.Tier.VM)), 1, 2)
                .run(in(60_000));
        T.eq(null, r.failure, "ok");
        T.eq(2, vc.vmReads.get(), "both clusters read");
        T.eq(1, maxInRead.get(), "the two VM reads ran one after the other");
        T.check(r.vmPermitWaitMaxMs > 0, "the second waited for the permit");
    }

    /**
     * Review W2 (build 11): every host's QueryDisksForVsan faulting does not
     * drive a read every cycle: 14 cycles make 5 + 5 calls (cycle 1 and the
     * 12-cycle refresh at cycle 13), not 70. The WARN names the fault class.
     */
    static void diskTotalFailureRateLimited() throws Exception {
        EngineTest.FakeVc vc = vc();
        for (String[] d : DISKS) {
            vc.disks.put("vsanSystem-" + d[1].substring("host-".length()),
                    new VsanFault("NoPermission", "Permission to perform this operation was denied.",
                            java.util.Collections.emptyList(), 500));
        }
        T.RecLog log = new T.RecLog();
        CycleEngine e = engine(vc, ops(vc.inventory), log, EntityType.ofTiers(EnumSet.of(
                EntityType.Tier.CORE, EntityType.Tier.DISK)), 3, 1);
        for (int cycle = 1; cycle <= 14; cycle++) {
            CycleEngine.Result r = e.run(in(60_000));
            T.eq(null, r.failure, "cycle " + cycle + " succeeds");
        }
        T.eq(10, vc.diskCalls.get(), "5 + 5 calls in 14 cycles");
        T.check(log.text().contains("QueryDisksForVsan failed on 5 host(s) (first: NoPermission)"),
                "WARN with the fault class: " + log.text());
        T.check(!log.text().contains("denied"), "never the server's text");
    }

    /** v2 Sizing requirement 4: under 4 workers, VM-type calls never overlap. */
    static void oneVmCallInFlight() throws Exception {
        List<Inventory.Cluster> clusters = new ArrayList<>();
        AtomicInteger maxInVm = new AtomicInteger();
        EngineTest.FakeVc vc = new EngineTest.FakeVc() {
            final AtomicInteger inVm = new AtomicInteger();

            @Override public CycleEngine.PerfCall queryPerf(String cluster, EntityType type,
                    long s, long e, long t) throws IOException, InterruptedException {
                if (type.tier != EntityType.Tier.VM) return super.queryPerf(cluster, type, s, e, t);
                int now = inVm.incrementAndGet();
                try {
                    maxInVm.accumulateAndGet(now, Math::max);
                    Thread.sleep(40);
                    return super.queryPerf(cluster, type, s, e, t);
                } finally {
                    inVm.decrementAndGet();
                }
            }
        };
        for (int i = 0; i < 4; i++) {
            clusters.add(new Inventory.Cluster("domain-c" + i, "c" + i, List.of(new Inventory.Host(
                    "host-" + i, "h" + i, "node-" + i, "U" + i, true))));
        }
        vc.inventory = new Inventory(clusters);
        EngineTest.FakeOps ops = EngineTest.labOps(vc.inventory);
        T.RecLog log = new T.RecLog();
        CycleEngine.Result r = engine(vc, ops, log, EntityType.ofTiers(EnumSet.of(
                EntityType.Tier.CORE, EntityType.Tier.VM)), 1, 4).run(in(60_000));
        T.eq(null, r.failure, "ok");
        T.eq(4 * 3, r.tierCalls[EntityType.Tier.VM.ordinal()], "every cluster's VM calls ran");
        T.eq(1, maxInVm.get(), "never two VM-type calls in flight");
        T.check(r.vmPermitWaitMaxMs > 0, "a worker waited for the permit: " + r.vmPermitWaitMaxMs);
    }
}
