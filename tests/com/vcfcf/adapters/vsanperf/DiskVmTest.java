package com.vcfcf.adapters.vsanperf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * T1 for the disk and VM tiers (design v2 and its P2 addendum), from the
 * verbatim captures (PROVENANCE-2026-11-06-build2.md and
 * PROVENANCE-2026-11-06-p2.md): the disk and VM perf types, the
 * QueryDisksForVsan answer, the cluster-scoped VM pages, the bus position
 * (including non-SCSI controllers, synthesized in this test only and
 * labelled so), the streaming parser against the whole-document parse on
 * the largest capture, the NIOPS* None series, and the VM cadence.
 */
public final class DiskVmTest {

    static final long T0505 = PerfParserTest.T0505;

    public static void main(String[] args) throws Exception {
        diskAndVmCaptures();
        queryDisksForVsan();
        vmPages();
        busPosition();
        vdiskJoinFromDeviceRead();
        streamingEqualsTree();
        truncatedStreamFails();
        cadenceAndStagger();
        System.out.println("DiskVmTest: all assertions passed");
    }

    /** PROV2 counts for the disk and VM types; the 592 NIOPS* series push nothing. */
    static void diskAndVmCaptures() throws Exception {
        PerfParserTest.check("queryperf-vsan-esa-disk-layer-2026-11-06.json",
                EntityType.VSAN_ESA_DISK_LAYER, 5, 25, T0505, 5);
        PerfParserTest.check("queryperf-vsan-esa-disk-scsifw-2026-11-06.json",
                EntityType.VSAN_ESA_DISK_SCSIFW, 5, 25, T0505, 5);
        for (PerfParser.Entity e : PerfParser.parse(T.vendor(
                "queryperf-vsan-esa-disk-layer-2026-11-06.json"), EntityType.VSAN_ESA_DISK_LAYER)
                .entities) {
            T.eq(e.ref.uuid, e.ref.instance, "disk uuid is the instance");
        }
        PerfParser.Result vm = PerfParser.parse(T.vendor("queryperf-virtual-machine-2026-11-06.json"),
                EntityType.VIRTUAL_MACHINE);
        T.eq(30, vm.entities.size(), "30 VMs");
        T.eq(180, vm.seriesSeen, "180 series");
        T.eq(354, vm.samples, "354 samples (one VM with one bucket)");
        PerfParser.Result vs = PerfParser.parse(T.vendor("queryperf-vscsi-2026-11-06.json"),
                EntityType.VSCSI);
        T.eq(148, vs.entities.size(), "148 vscsi devices");
        T.eq(1184, vs.seriesSeen, "1,184 series");
        Set<String> controllers = new HashSet<>();
        for (PerfParser.Entity e : vs.entities) {
            controllers.add(e.ref.instance.substring(0, e.ref.instance.indexOf(':')));
        }
        T.eq(Set.of("scsi0", "scsi1"), controllers, "controllers seen on the lab");
        PerfParser.Result vd = PerfParser.parse(T.vendor("queryperf-virtual-disk-2026-11-06.json"),
                EntityType.VIRTUAL_DISK);
        T.eq(148, vd.entities.size(), "148 vdisks");
        T.eq(1036, vd.seriesSeen, "1,036 series");
        T.eq(1036 - 592, vd.seriesKept, "the 592 NIOPS* series (148 x 4) are all None");
        T.eq(885, vd.samples, "885 values pushed at most");
        Set<String> paths = new HashSet<>();
        int slash = 0;
        int nested = 0;
        for (PerfParser.Entity e : vd.entities) {
            T.check(!e.ref.uuid.startsWith("/"), "leading slash stripped");
            if (e.ref.raw.startsWith("virtual-disk:/")) slash++;
            if (e.ref.uuid.split("/").length == 4) nested++; // <ns>/_003e/_00xx/<file>
            T.check(paths.add(e.ref.uuid), "no two refs collide after stripping");
            for (PerfParser.Sample s : e.samples) {
                T.check(!s.metric.label.startsWith("NIOPSRead")
                        && !s.metric.label.startsWith("NIOPSWrite"), "no NIOPSRead/Write value");
            }
        }
        T.eq(3, slash, "3 leading-slash refs");
        T.eq(22, nested, "22 two-level nested refs (api map: <ns>/_003e/_000e/<32-hex>.vmdk)");
        PerfParser.Result p2 = PerfParser.parse(T.vendor(
                "queryperf-vsan-esa-disk-layer-p2-2026-11-06.json"), EntityType.VSAN_ESA_DISK_LAYER);
        T.eq(5, p2.entities.size(), "P2 session: the same 5 disks");
    }

    /** P2: the bare array of VsanHostDiskResult; key on non-empty vsanUuid. */
    static void queryDisksForVsan() throws Exception {
        List<String> u = Inventory.vsanDiskUuids(T.vendor("querydisksforvsan-host-15151-2026-11-06.json"));
        T.eq(List.of("52d171f6-2e48-758a-777f-d5098533d861"), u,
                "one vSAN disk of three (all three read 'ineligible')");
        T.expect(java.io.IOException.class, () -> Inventory.vsanDiskUuids("{\"a\":1}"), "not an array");
    }

    /** P2: the cluster-scoped VM pages, light and with devices. */
    static void vmPages() throws Exception {
        VmInventory.Builder b = new VmInventory.Builder(false);
        String tok = null;
        for (int i = 1; i <= 5; i++) {
            tok = b.addPage(T.vendor("retrievepropertiesex-vm-cluster-light-max10-page" + i
                    + "-2026-11-06.json"));
            T.eq(i < 5 ? String.valueOf(i + 3) : null, tok, "page " + i + " token");
        }
        VmInventory light = b.build();
        T.eq(42, light.vms(), "42 VMs in the cluster view");
        T.eq(0, light.disks(), "no device list on the light read");
        T.eq(null, light.disk("x/y.vmdk"), "no disk lookup without devices");
        VmInventory one = page(false, T.vendor("retrievepropertiesex-vm-cluster-light-2026-11-06.json"));
        T.eq(42, one.vms(), "the unpaged light read: the same 42");
        PerfParser.Result vm = PerfParser.parse(T.vendor("queryperf-virtual-machine-2026-11-06.json"),
                EntityType.VIRTUAL_MACHINE);
        int found = 0;
        for (PerfParser.Entity e : vm.entities) if (light.vmMoid(e.ref.uuid) != null) found++;
        T.eq(30, found, "30 of 30 perf VMs by config.instanceUuid");
        PerfParser.Result vs = PerfParser.parse(T.vendor("queryperf-vscsi-2026-11-06.json"),
                EntityType.VSCSI);
        found = 0;
        for (PerfParser.Entity e : vs.entities) if (light.vmMoid(e.ref.uuid) != null) found++;
        T.eq(148, found, "148 of 148 vscsi refs onto a VM");
        T.eq("vm-39030", page(true, T.vendor(
                "retrievepropertiesex-vm-cluster-devices-max10-page1-2026-11-06.json"))
                .vmMoid("5003f958-74a6-1965-00ad-25b80c81c3c4"), "adfs1");
        T.check(VmInventory.retrieveBody("v", true).contains("\"maxObjects\":50"), "pages of 50");
        T.check(VmInventory.retrieveBody("v", true).contains("config.hardware.device")
                && !VmInventory.retrieveBody("v", false).contains("config.hardware.device"),
                "devices only when asked");
    }

    /**
     * Bus position: controllerKey to the controller's busNumber, prefix by
     * the controller type. The non-SCSI and unknown controllers below are
     * SYNTHESIZED in this test (not vendor captures; the lab has PVSCSI and
     * LSI Logic disks only).
     */
    static void busPosition() throws Exception {
        T.eq("scsi", VmInventory.busPrefix("ParaVirtualSCSIController"), "PVSCSI");
        T.eq("scsi", VmInventory.busPrefix("VirtualLsiLogicController"), "LSI");
        T.eq("scsi", VmInventory.busPrefix("VirtualLsiLogicSASController"), "LSI SAS");
        T.eq("scsi", VmInventory.busPrefix("VirtualBusLogicController"), "BusLogic");
        T.eq("nvme", VmInventory.busPrefix("VirtualNVMEController"), "NVMe");
        T.eq("sata", VmInventory.busPrefix("VirtualAHCIController"), "SATA");
        T.eq("ide", VmInventory.busPrefix("VirtualIDEController"), "IDE");
        T.eq(null, VmInventory.busPrefix("VirtualUSBController"), "not a disk bus");
        T.eq("a/b.vmdk", VmInventory.normalizeBackingPath("[vcf-lab ds] a/b.vmdk"), "datastore cut");
        T.eq("a/b.vmdk", VmInventory.normalizeBackingPath("[ds] /a/b.vmdk"), "slash cut");
        // SYNTHESIZED device array (not a vendor fixture): one VM, disks on
        // NVMe bus 1, SATA bus 0, IDE bus 1, an unknown controller type, a
        // missing controller, and two disks sharing one path.
        String page = "{\"_typeName\":\"RetrieveResult\",\"objects\":[{\"_typeName\":\"ObjectContent\","
                + "\"obj\":{\"_typeName\":\"ManagedObjectReference\",\"value\":\"vm-synthetic\","
                + "\"type\":\"VirtualMachine\"},\"propSet\":["
                + "{\"_typeName\":\"DynamicProperty\",\"name\":\"config.instanceUuid\",\"val\":{"
                + "\"_typeName\":\"string\",\"_value\":\"iu-synthetic\"}},"
                + "{\"_typeName\":\"DynamicProperty\",\"name\":\"config.hardware.device\",\"val\":{"
                + "\"_typeName\":\"ArrayOfVirtualDevice\",\"_value\":["
                + ctl("VirtualNVMEController", 31000, 1) + "," + ctl("VirtualAHCIController", 15000, 0)
                + "," + ctl("VirtualIDEController", 201, 1) + "," + ctl("VirtualFutureController", 9000, 0)
                + "," + disk(31000, 0, "nvme.vmdk") + "," + disk(15000, 3, "sata.vmdk") + ","
                + disk(201, 0, "ide.vmdk") + "," + disk(9000, 0, "future.vmdk") + ","
                + disk(4242, 0, "orphan.vmdk") + "," + disk(31000, 1, "shared.vmdk") + ","
                + disk(15000, 4, "shared.vmdk") + "]}}]}]}";
        VmInventory inv = page(true, page);
        T.eq("nvme1:0", inv.disk("ns/nvme.vmdk").busPosition, "NVMe bus 1 unit 0");
        T.eq("sata0:3", inv.disk("ns/sata.vmdk").busPosition, "SATA");
        T.eq("ide1:0", inv.disk("ns/ide.vmdk").busPosition, "IDE");
        T.eq("vm-synthetic", inv.disk("ns/ide.vmdk").vmMoid, "its VM");
        T.check(inv.disk("ns/future.vmdk") == VmInventory.UNPLACEABLE, "unknown controller: never guessed");
        T.check(inv.disk("ns/orphan.vmdk") == VmInventory.UNPLACEABLE, "missing controller");
        T.check(inv.disk("ns/shared.vmdk") == VmInventory.UNPLACEABLE, "shared path: neither");
        T.eq(null, inv.disk("ns/absent.vmdk"), "unknown path: a re-read trigger");
        T.eq(3, inv.unresolvableDisks, "three unplaceable");
    }

    /** One page read into an inventory. */
    static VmInventory page(boolean devices, String body) throws java.io.IOException {
        VmInventory.Builder b = new VmInventory.Builder(devices);
        b.addPage(body);
        return b.build();
    }

    static String ctl(String type, int key, int bus) {
        return "{\"_typeName\":\"" + type + "\",\"key\":" + key + ",\"busNumber\":" + bus + "}";
    }

    static String disk(int controllerKey, int unit, String file) {
        return "{\"_typeName\":\"VirtualDisk\",\"key\":" + (2000 + unit) + ",\"controllerKey\":"
                + controllerKey + ",\"unitNumber\":" + unit + ",\"backing\":{\"_typeName\":"
                + "\"VirtualDiskFlatVer2BackingInfo\",\"fileName\":\"[ds] ns/" + file + "\"}}";
    }

    /**
     * P2 join from the verbatim device pages (20 VMs): every vdisk path of
     * those VMs in the perf capture resolves, and its bus position is also a
     * vscsi id of the same VM (Q5: the two instances agree).
     */
    static void vdiskJoinFromDeviceRead() throws Exception {
        VmInventory.Builder b = new VmInventory.Builder(true);
        b.addPage(T.vendor("retrievepropertiesex-vm-cluster-devices-max10-page1-2026-11-06.json"));
        b.addPage(T.vendor("retrievepropertiesex-vm-cluster-devices-max10-page2-2026-11-06.json"));
        VmInventory inv = b.build();
        T.eq(20, inv.vms(), "20 VMs on the two saved device pages");
        T.eq(64, inv.disks(), "64 virtual disks on them");
        T.eq(0, inv.unresolvableDisks, "all on PVSCSI or LSI Logic");
        Map<String, String> iuByMoid = new HashMap<>();
        for (PerfParser.Entity e : PerfParser.parse(T.vendor(
                "queryperf-virtual-machine-2026-11-06.json"), EntityType.VIRTUAL_MACHINE).entities) {
            String m = inv.vmMoid(e.ref.uuid);
            if (m != null) iuByMoid.put(m, e.ref.uuid);
        }
        Set<String> vscsi = new HashSet<>();
        for (PerfParser.Entity e : PerfParser.parse(T.vendor("queryperf-vscsi-2026-11-06.json"),
                EntityType.VSCSI).entities) {
            vscsi.add(e.ref.uuid + "|" + e.ref.instance);
        }
        int resolved = 0;
        for (PerfParser.Entity e : PerfParser.parse(T.vendor("queryperf-virtual-disk-2026-11-06.json"),
                EntityType.VIRTUAL_DISK).entities) {
            VmInventory.Disk d = inv.disk(e.ref.uuid);
            if (d == null) continue;
            resolved++;
            T.check(d.busPosition.startsWith("scsi"), "scsi bus");
            String iu = iuByMoid.get(d.vmMoid);
            if (iu != null) {
                T.check(vscsi.contains(iu + "|" + d.busPosition), "vdisk bus position is a vscsi id "
                        + "of the same VM: " + d.busPosition);
            }
        }
        T.eq(57, resolved, "57 perf vdisks are on these 20 VMs, all resolved");
    }

    /** Design T1: the streaming parse and the whole-document parse give identical rows. */
    static void streamingEqualsTree() throws Exception {
        String[][] files = {{"queryperf-vscsi-2026-11-06.json", "vscsi"},
                {"queryperf-virtual-disk-2026-11-06.json", "virtual-disk"},
                {"queryperf-host-domowner-2026-11-06.json", "host-domowner"},
                {"queryperf-vsan-tcpip-stats-2026-11-06.json", "vsan-tcpip-stats"}};
        for (String[] f : files) {
            EntityType t = EntityType.byId(f[1]);
            String body = T.vendor(f[0]);
            PerfParser.Result tree = PerfParser.parse(body, t);
            PerfParser.Result stream = PerfParser.parse(new java.util.zip.GZIPInputStream(
                    new ByteArrayInputStream(gzip(body))), t);
            T.eq(rows(tree), rows(stream), f[0] + ": streaming == whole-document rows");
            T.eq(tree.seriesSeen, stream.seriesSeen, f[0] + " series");
            T.eq(tree.samples, stream.samples, f[0] + " samples");
        }
        // Echo, malformed and empty-array answers agree too.
        for (EntityType t : PerfParserTest.EMPTY_ON_LAB) {
            PerfParser.Result s = PerfParser.parse(new ByteArrayInputStream(
                    PerfParserTest.echo(t).getBytes(StandardCharsets.UTF_8)), t);
            T.check(s.isEmptyEcho(), t.id + " echo via the stream");
        }
        System.out.println("DiskVmTest: largest capture (vscsi) " + T.vendor(
                "queryperf-vscsi-2026-11-06.json").length() + " B decoded, "
                + gzip(T.vendor("queryperf-vscsi-2026-11-06.json")).length + " B gzip");
    }

    /** A gzip body cut mid-entity fails the call; nothing of it is returned. */
    static void truncatedStreamFails() throws Exception {
        byte[] gz = gzip(T.vendor("queryperf-vscsi-2026-11-06.json"));
        byte[] cut = java.util.Arrays.copyOf(gz, gz.length / 2);
        T.expect(Unreadable.class, () -> PerfParser.parse(new java.util.zip.GZIPInputStream(
                new ByteArrayInputStream(cut)), EntityType.VSCSI), "cut gzip");
        String body = T.vendor("queryperf-vscsi-2026-11-06.json");
        String half = body.substring(0, body.length() / 2);
        T.expect(Unreadable.class, () -> PerfParser.parse(new ByteArrayInputStream(
                half.getBytes(StandardCharsets.UTF_8)), EntityType.VSCSI), "cut JSON");
        T.expect(Unreadable.class, () -> PerfParser.parse(new ByteArrayInputStream(
                "{\"a\":1}".getBytes(StandardCharsets.UTF_8)), EntityType.VSCSI), "not an array");
    }

    /** v2 Settings: cluster c runs its VM types where (cycle + stableHash(c)) mod N == 0. */
    static void cadenceAndStagger() {
        for (int n : new int[] {1, 2, 3, 12}) {
            CycleEngine e = new CycleEngine(null, null, new CycleEngine.Settings(
                    new ClusterFilter("", ""), 4, 30_000, false,
                    EntityType.ofTiers(java.util.EnumSet.allOf(EntityType.Tier.class)), n),
                    Log.NONE, T.CLOCK::get);
            int[] perCycle = new int[n];
            for (int c = 0; c < 70; c++) {
                String moid = "domain-c" + (6000 + c);
                int runs = 0;
                for (long cycle = 1; cycle <= n; cycle++) {
                    if (e.vmRun(moid, cycle)) {
                        runs++;
                        perCycle[(int) (cycle - 1)]++;
                    }
                }
                T.eq(1, runs, "N=" + n + ": " + moid + " runs exactly once in N cycles");
                T.check(e.vmRun(moid, 7) == e.vmRun(moid, 7 + n), "periodic");
            }
            if (n == 3) {
                for (int k : perCycle) T.check(k >= 10 && k <= 40, "staggered: " + k + " of 70");
            }
        }
    }

    static String rows(PerfParser.Result r) {
        StringBuilder b = new StringBuilder();
        for (PerfParser.Entity e : r.entities) {
            b.append(e.ref.raw).append('|').append(e.ref.uuid).append('|').append(e.ref.instance);
            for (PerfParser.Sample s : e.samples) {
                b.append(';').append(s.metric.label).append('@').append(s.timestamp).append('=')
                        .append(s.value);
            }
            b.append('\n');
        }
        return b.toString();
    }

    static byte[] gzip(String s) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(bo)) {
            g.write(s.getBytes(StandardCharsets.UTF_8));
        }
        return bo.toByteArray();
    }
}
