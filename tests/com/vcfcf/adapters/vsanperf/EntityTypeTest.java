package com.vcfcf.adapters.vsanperf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T1: the catalog equals the design tables row for row: the 61 core keys
 * (verbatim copy of v1 in tests/fixtures/design-build1-metrics.md) and the
 * 338 build 10 keys of the cluster and host tiers (verbatim copy of v2 in
 * tests/fixtures/design-build2-metrics.md): label, stat key, per-mille
 * transform, labels array order per type; and the design's own leaf rule
 * (v1 Q1, extended by v2 Q6) reproduces every leaf from the label and unit
 * columns, so the tables are self-consistent.
 */
public final class EntityTypeTest {

    static final class Row {
        String type, label, key, unit, transform;
    }

    public static void main(String[] args) throws Exception {
        List<Row> rows = designRows("design-build1-metrics.md", "^### `([a-z-]+)`");
        T.eq(61, rows.size(), "design rows");
        int n = 0;
        for (EntityType t : EntityType.values()) {
            if (t.tier == EntityType.Tier.CORE) n += t.metrics().size();
        }
        T.eq(61, n, "core catalog rows");
        T.eq(38, EntityType.HOST_DOMOWNER.metrics().size(), "DOM owner");
        T.eq(6, EntityType.HOST_DOMCLIENT.metrics().size(), "DOM client");
        T.eq(17, EntityType.VSAN_PNIC_NET.metrics().size(), "pNIC");

        for (Row r : rows) {
            EntityType t = EntityType.byId(r.type);
            T.check(t != null, "type " + r.type);
            EntityType.Metric m = t.metric(r.label);
            T.check(m != null, r.type + " has label " + r.label);
            String key = t.statKey("<vmnic>", m);
            T.eq(r.key, key, r.type + " " + r.label + " key");
            T.eq(!"none".equals(r.transform), m.perMille, r.label + " per-mille");
            if (m.perMille) T.eq("per-mille / 10", r.transform, r.label + " transform text");
            T.eq(ruleLeaf(r.label, r.unit, m.perMille), m.leaf, r.label + " follows the leaf rule");
        }
        // Order: the catalog lists labels in design table order.
        List<String> designDomOwner = new ArrayList<>();
        for (Row r : rows) if (r.type.equals("host-domowner")) designDomOwner.add(r.label);
        T.eq(designDomOwner, EntityType.HOST_DOMOWNER.labels(), "labels array order");

        // Collection order and keys.
        T.eq(EntityType.HOST_DOMOWNER, EntityType.values()[0], "DOM owner first");
        T.eq(EntityType.VSAN_PNIC_NET, EntityType.values()[2], "pNIC last");
        T.eq("vSAN Perf|pNIC:vmnic0|rxErrPct",
                EntityType.VSAN_PNIC_NET.statKey("vmnic0", EntityType.VSAN_PNIC_NET.metric("rxErr")),
                "instanced key");
        T.eq("vSAN Perf|DOM Owner|latencyAvgWriteUs",
                EntityType.HOST_DOMOWNER.statKey(null,
                        EntityType.HOST_DOMOWNER.metric("latencyAvgWrite")), "host key");
        T.near(12.5, EntityType.VSAN_PNIC_NET.metric("rxErr").convert(125), "per mille to percent");
        T.near(125, EntityType.VSAN_PNIC_NET.metric("rxCrcErrRaw").convert(125), "raw unchanged");
        build2();
        System.out.println("EntityTypeTest: all assertions passed");
    }

    /**
     * The 390 Build 2 rows: cluster tier 10 types / 189 keys, host tier 13 /
     * 149, disk tier 4 / 31, VM tier 3 / 21.
     */
    static void build2() throws Exception {
        List<Row> rows = designRows("design-build2-metrics.md", "^#### `([a-z0-9-]+)`");
        T.eq(390, rows.size(), "v2 design rows");
        java.util.Map<EntityType.Tier, Integer> keys = new java.util.EnumMap<>(EntityType.Tier.class);
        java.util.Map<EntityType.Tier, Integer> types = new java.util.EnumMap<>(EntityType.Tier.class);
        for (EntityType t : EntityType.values()) {
            keys.merge(t.tier, t.size(), Integer::sum);
            types.merge(t.tier, 1, Integer::sum);
        }
        T.eq(189, keys.get(EntityType.Tier.CLUSTER), "cluster tier keys");
        T.eq(10, types.get(EntityType.Tier.CLUSTER), "cluster tier types");
        T.eq(149, keys.get(EntityType.Tier.HOST), "host tier keys");
        T.eq(13, types.get(EntityType.Tier.HOST), "host tier types");
        T.eq(31, keys.get(EntityType.Tier.DISK), "disk tier keys");
        T.eq(4, types.get(EntityType.Tier.DISK), "disk tier types");
        T.eq(21, keys.get(EntityType.Tier.VM), "VM tier keys");
        T.eq(3, types.get(EntityType.Tier.VM), "VM tier types");
        T.eq(33, EntityType.values().length, "33 types");
        int all = 0;
        for (EntityType t : EntityType.values()) all += t.size();
        T.eq(451, all, "451 keys (the Python pack's default scope)");

        java.util.Map<String, List<String>> designLabels = new java.util.LinkedHashMap<>();
        for (Row r : rows) {
            EntityType t = EntityType.byId(r.type);
            T.check(t != null, "type " + r.type);
            T.check(t.tier != EntityType.Tier.CORE, r.type + " is a build 10 type");
            EntityType.Metric m = t.metric(r.label);
            T.check(m != null, r.type + " has label " + r.label);
            String inst = r.key.contains(":<") ? r.key.substring(r.key.indexOf(":<") + 1,
                    r.key.indexOf('>', r.key.indexOf(":<")) + 1) : null;
            T.eq(r.key, t.statKey(inst, m), r.type + " " + r.label + " key");
            T.eq(inst != null, t.instanced, r.type + " instanced");
            boolean pm = r.transform.startsWith("per-mille / 10");
            T.eq(pm, m.perMille, r.type + " " + r.label + " per-mille");
            T.check(pm || r.transform.startsWith("none"), r.label + " transform text");
            String want = r.type.equals("capacity-disk") && r.label.equals("deleteCongestion")
                    ? r.label // R3b: a congestion score, left unsuffixed despite "bytes"
                    : ruleLeaf(r.label, r.unit, m.perMille);
            T.eq(want, m.leaf, r.type + " " + r.label + " follows the leaf rule");
            designLabels.computeIfAbsent(r.type, k -> new ArrayList<>()).add(r.label);
        }
        for (java.util.Map.Entry<String, List<String>> e : designLabels.entrySet()) {
            T.eq(e.getValue(), EntityType.byId(e.getKey()).labels(), e.getKey() + " labels array");
        }
        // Collection order (v2 "Steps"): core, cluster, host, disk, VM.
        List<EntityType.Tier> order = List.of(EntityType.Tier.CORE, EntityType.Tier.CLUSTER,
                EntityType.Tier.HOST, EntityType.Tier.DISK, EntityType.Tier.VM);
        EntityType.Tier last = EntityType.Tier.CORE;
        int stage = 0;
        for (EntityType t : EntityType.values()) {
            int s = order.indexOf(t.tier);
            T.check(s >= stage, t.id + " out of collection order after " + last);
            stage = s;
            last = t.tier;
        }
        // Targets and id forms.
        for (EntityType t : EntityType.values()) {
            if (t.tier == EntityType.Tier.CLUSTER) {
                boolean landing = t == EntityType.VSAN_DIRECT_CLUSTER
                        || t == EntityType.VSAN_FILE_SERVICE || t == EntityType.IOINSIGHT;
                T.eq(landing ? EntityType.Target.CLUSTER_OR_HOST : EntityType.Target.CLUSTER,
                        t.target, t.id + " target");
            } else if (t.tier == EntityType.Tier.DISK) {
                T.eq(EntityType.Target.DISK, t.target, t.id + " target");
                T.eq(EntityType.IdForm.SELF, t.form, t.id + " instanced by its disk uuid");
            } else if (t.tier == EntityType.Tier.VM) {
                T.eq(EntityType.Target.VM, t.target, t.id + " target");
                T.eq(16L * 1024 * 1024, t.wireCap(), t.id + " 16 MB wire cap");
            } else {
                T.eq(EntityType.Target.HOST, t.target, t.id + " target");
                T.eq(4L * 1024 * 1024, t.wireCap(), t.id + " 4 MB wire cap");
            }
        }
        T.eq(EntityType.IdForm.ONE, EntityType.VIRTUAL_MACHINE.form, "VM: instanceUuid");
        T.eq(EntityType.IdForm.TWO, EntityType.VSCSI.form, "vscsi: instanceUuid|scsiN:M");
        T.eq(EntityType.IdForm.PATH, EntityType.VIRTUAL_DISK.form, "vdisk: path");
        T.check(EntityType.VIRTUAL_DISK.instanced && !EntityType.VIRTUAL_MACHINE.instanced,
                "instanced flags");
        T.eq("vSAN Perf|Virtual Disk:scsi0:0|NIOPS", EntityType.VIRTUAL_DISK.statKey("scsi0:0",
                EntityType.VIRTUAL_DISK.metric("NIOPS")), "vdisk key by bus position");
        T.eq("vSAN Perf|ESA Disk Layer:52d171f6|iopsReadCapacity", EntityType.VSAN_ESA_DISK_LAYER
                .statKey("52d171f6", EntityType.VSAN_ESA_DISK_LAYER.metric("iopsReadCapacity")),
                "disk key by uuid");
        T.eq(EntityType.IdForm.TWO, EntityType.VSAN_TCPIP_STATS.form, "tcpip: node|stack");
        T.eq(EntityType.IdForm.THREE_LAST, EntityType.VSAN_VNIC_NET.form, "vnic: node|stack|vmk");
        T.eq(EntityType.IdForm.TWO, EntityType.VNIC_RDT_NETWORK_LATENCY.form, "vnic rdt: node|vmk");
        T.eq(EntityType.IdForm.TWO, EntityType.VSAN_PNIC_NET.form, "pnic unchanged");
        T.eq("vSAN Perf|TCP/IP:defaultTcpipStack|tcpErrsPct", EntityType.VSAN_TCPIP_STATS
                .statKey("defaultTcpipStack", EntityType.VSAN_TCPIP_STATS.metric("tcpErrs")),
                "TCP/IP instanced key shape");
        T.near(1.5, EntityType.VSAN_TCPIP_STATS.metric("tcpErrs").convert(15), "per mille");
        T.check(!EntityType.HOST_DOMOWNER.learnsEmpty(), "core never learned empty");
        T.check(EntityType.ZDOM_VTX.learnsEmpty(), "host tier learns empty");
        // Group names are unique per target object (v2 "Group names").
        java.util.Map<String, String> byGroup = new java.util.HashMap<>();
        for (EntityType t : EntityType.values()) {
            String target = t.target == EntityType.Target.HOST || t.target == EntityType.Target.DISK
                    ? "host" : t.target == EntityType.Target.VM ? "vm" : "cluster";
            String prev = byGroup.put(target + "|" + t.group, t.id);
            T.check(prev == null, "group " + t.group + " used twice on " + target + ": " + prev
                    + ", " + t.id);
        }
        for (EntityType t : EntityType.values()) {
            if (t.target != EntityType.Target.CLUSTER_OR_HOST) continue;
            T.check(!byGroup.containsKey("host|" + t.group), t.group + " landing on a host collides");
        }
    }

    /** The design's leaf rule: v1 Q1, extended by v2 Q6 (Bytes, Ms, Pct). */
    static String ruleLeaf(String label, String unit, boolean perMille) {
        if (perMille) return label + "Pct";
        boolean suffixed = label.endsWith("Us") || label.endsWith("Sec") || label.endsWith("Bps");
        if (!suffixed && unit.equals("microseconds")) return label + "Us";
        if (!suffixed && unit.equals("bytes/s")) return label + "BytesPerSec";
        if (unit.equals("bytes") && !label.endsWith("Bytes")) return label + "Bytes";
        if (unit.equals("milliseconds") && !label.endsWith("Ms")) return label + "Ms";
        if (unit.equals("percent") && !label.endsWith("Pct")) return label + "Pct";
        return label;
    }

    static List<Row> designRows(String file, String heading) throws Exception {
        Pattern head = Pattern.compile(heading);
        List<Row> out = new ArrayList<>();
        String type = null;
        for (String line : T.read(file).split("\n")) {
            Matcher h = head.matcher(line);
            if (h.find()) {
                type = h.group(1);
                continue;
            }
            if (!line.startsWith("| `")) continue;
            String raw = line.trim();
            raw = raw.substring(1, raw.length() - 1);
            String[] p = raw.split("(?<!\\\\)\\|");
            Row r = new Row();
            r.type = type;
            r.label = p[0].trim().replace("`", "");
            r.key = p[1].trim().replace("`", "").replace("\\|", "|");
            r.unit = p[3].trim();
            r.transform = p[4].trim();
            out.add(r);
        }
        return out;
    }
}
