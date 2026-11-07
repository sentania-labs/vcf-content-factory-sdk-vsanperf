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
        build3();
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
            if (t.inScope(EntityType.Scope.DASHBOARD)) types.merge(t.tier, 1, Integer::sum);
        }
        T.eq(189, keys.get(EntityType.Tier.CLUSTER), "cluster tier keys");
        T.eq(10, types.get(EntityType.Tier.CLUSTER), "cluster tier types");
        T.eq(149, keys.get(EntityType.Tier.HOST), "host tier keys");
        T.eq(13, types.get(EntityType.Tier.HOST), "host tier types");
        T.eq(31, keys.get(EntityType.Tier.DISK), "disk tier keys");
        T.eq(4, types.get(EntityType.Tier.DISK), "disk tier types");
        T.eq(21, keys.get(EntityType.Tier.VM), "VM tier keys");
        T.eq(3, types.get(EntityType.Tier.VM), "VM tier types");
        int dashTypes = 0;
        for (EntityType t : EntityType.values()) if (t.inScope(EntityType.Scope.DASHBOARD)) dashTypes++;
        T.eq(33, dashTypes, "33 dashboard-scope types");
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
            if (t.allOnly) continue; // build 3 types: build3()
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
            // Build 3: cluster-pmem shares PMem with host-pmem; it never lands on a host.
            T.check(!byGroup.containsKey("host|" + t.group) || !t.landsOnHost(),
                    t.group + " landing on a host collides");
            T.eq(!byGroup.containsKey("host|" + t.group), t.landsOnHost(), t.id + " lands on host");
        }
        T.check(!EntityType.CLUSTER_PMEM.landsOnHost(), "cluster-pmem: cluster only");
        T.check(EntityType.CLUSTER_RESYNC.landsOnHost(), "cluster-resync: runtime landing");
    }

    /** v3 Units rule 1: disputed units, label verbatim. */
    static final java.util.Set<String> DISPUTED = java.util.Set.of("cluster-pmem latencyRead",
            "cluster-pmem latencyWrite", "host-pmem latencyRead", "host-pmem latencyWrite",
            "ddh-disk avgReadLatency", "ddh-disk avgWriteLatency",
            "disk-group outstandingBytesSched", "system-mem pctMemUsed");

    static final String[] FAMILY_18 = {"cluster-esa-dedup-domclient-io",
            "host-esa-dedup-domclient-io", "cluster-esa-dedup-domcompmgr-io",
            "host-esa-dedup-domcompmgr-io"};

    /**
     * Build 19 (design v3): the 1,144 Appendix A rows plus the 210 P3 labels
     * (v3 addendum Q3), both copied verbatim into
     * tests/fixtures/design-build3-metrics.md: each is an all-scope row of its
     * type with the listed leaf, the stat key, the per-mille transform on
     * exactly the 15 listed keys; the catalog holds nothing else; v3 rules
     * 1 to 7 reproduce every Appendix A leaf from the Python pack's units
     * (tests/fixtures/design-build3-units.tsv); one leaf per label across
     * types; counts, forms, targets, flags and order of the 36 new types.
     */
    static void build3() throws Exception {
        String doc = T.read("design-build3-metrics.md");
        Pattern row = Pattern.compile("^- \\*\\*`([A-Za-z0-9-]+)`\\*\\* \\(`([^`]+)`, (\\w+), "
                + "(\\w+), \\+(\\d+)[^)]*\\): (.*)$");
        java.util.Map<String, java.util.Map<String, String>> appendix = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> setOf = new java.util.HashMap<>();
        int rows = 0;
        String extrasDoc = doc.substring(doc.indexOf("### Labels beyond the Python model"));
        for (String line : doc.substring(0, doc.indexOf("### Labels beyond")).split("\n")) {
            Matcher m = row.matcher(line);
            if (!m.find()) continue;
            java.util.Map<String, String> leaves = new java.util.LinkedHashMap<>();
            for (String it : m.group(6).split(", ")) {
                String[] lf = it.split("\u2192");
                leaves.put(lf[0], lf.length == 2 ? lf[1] : lf[0]);
            }
            T.eq(Integer.parseInt(m.group(5)), leaves.size(), m.group(1) + " row count");
            appendix.put(m.group(1), leaves);
            setOf.put(m.group(1), m.group(4));
            rows += leaves.size();
        }
        T.eq(1144, rows, "Appendix A: 1,144 keys");
        T.eq(57, appendix.size(), "21 shared + 36 new types");

        // The P3 labels (210): the four 18-label types, the two domowner types
        // (the same 18 plus 40), chunk service 6, hash service 5.
        java.util.Map<String, List<String>> extras = new java.util.LinkedHashMap<>();
        for (String line : extrasDoc.split("\n")) {
            if (!line.startsWith("- **")) continue;
            int sep = line.indexOf(":**");
            List<String> types = ticks(line.substring(0, sep));
            List<String> names = ticks(line.substring(sep));
            for (String t : types) extras.computeIfAbsent(t, k -> new ArrayList<>()).addAll(names);
        }
        List<String> eighteen = extras.get(FAMILY_18[0]);
        T.eq(18, eighteen.size(), "the 18-label family");
        for (String t : new String[] {"cluster-esa-dedup-domowner-io", "host-esa-dedup-domowner-io"}) {
            T.eq(40, extras.get(t).size(), t + " 40 own");
            extras.get(t).addAll(0, eighteen);
        }
        int extraCount = 0;
        for (List<String> v : extras.values()) extraCount += v.size();
        T.eq(210, extraCount, "P3: 210 labels beyond the Python model");

        java.util.Set<String> perMille = new java.util.HashSet<>(java.util.Arrays.asList(
                "vsan-pnic-net rxPacketsLossRate", "vsan-pnic-net txPacketsLossRate",
                "vsan-tcpip-stats ip6Errs", "vsan-tcpip-stats ipErrs", "vsan-vnic-net ip6Errs",
                "vsan-vnic-net ipErrs", "vsan-vnic-net tcpErrs", "vsan-vnic-net tcpHalfopenDropRate",
                "vsan-vnic-net tcpRcvdupackRate", "vsan-vnic-net tcpRcvduppackRate",
                "vsan-vnic-net tcpRcvoopackRate", "vsan-vnic-net tcpSackRcvBlocksRate",
                "vsan-vnic-net tcpSackRexmitsRate", "vsan-vnic-net tcpSackSendBlocksRate",
                "vsan-vnic-net tcpTimeoutDropRate"));
        java.util.Map<String, String[]> units = new java.util.HashMap<>();
        for (String line : T.read("design-build3-units.tsv").split("\n")) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] p = line.split("\t");
            units.put(p[0], p);
        }
        // The released (dashboard) leaf of every label, for rule 2.
        java.util.Map<String, EntityType.Metric> released = new java.util.HashMap<>();
        for (EntityType t : EntityType.values()) {
            for (EntityType.Metric m : t.metrics()) {
                EntityType.Metric prev = released.put(m.label, m);
                T.check(prev == null || prev.leaf.equals(m.leaf), "released leaf conflict " + m.label);
            }
        }
        int pm = 0;
        java.util.Map<String, String> leafOfLabel = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, java.util.Map<String, String>> e : appendix.entrySet()) {
            EntityType t = EntityType.byId(e.getKey());
            T.check(t != null, "type " + e.getKey());
            T.eq("new".equals(setOf.get(e.getKey())), t.allOnly, t.id + " all-only flag");
            for (java.util.Map.Entry<String, String> r : e.getValue().entrySet()) {
                EntityType.Metric m = t.metric(r.getKey(), EntityType.Scope.ALL);
                T.check(m != null, t.id + " has " + r.getKey());
                T.check(!m.dashboard, t.id + " " + r.getKey() + " is all-scope");
                T.eq(null, t.metric(r.getKey()), t.id + " " + r.getKey() + " not under dashboard");
                T.eq(r.getValue(), m.leaf, t.id + " " + r.getKey() + " leaf");
                boolean wantPm = perMille.contains(t.id + " " + r.getKey());
                T.eq(wantPm, m.perMille, t.id + " " + r.getKey() + " per-mille");
                if (wantPm) pm++;
                T.eq(r.getValue(), v3Leaf(t.id, r.getKey(), units.get(r.getKey()), released),
                        t.id + " " + r.getKey() + " follows v3 rules 1 to 7");
            }
        }
        T.eq(15, pm, "per-mille on exactly 15 new keys");
        for (java.util.Map.Entry<String, List<String>> e : extras.entrySet()) {
            EntityType t = EntityType.byId(e.getKey());
            for (String l : e.getValue()) {
                EntityType.Metric m = t.metric(l, EntityType.Scope.ALL);
                T.check(m != null && !m.dashboard && !m.perMille, t.id + " P3 label " + l);
                T.check(!released.containsKey(l), l + " is not a released label");
                T.eq(l, m.leaf, t.id + " " + l + ": unit unknown, no suffix (rule 4)");
            }
        }
        // Nothing else: every all-scope row is in Appendix A or the P3 list.
        int all = 0;
        java.util.Map<EntityType.Tier, Integer> perTier = new java.util.EnumMap<>(EntityType.Tier.class);
        java.util.Map<EntityType.Tier, Integer> typesPerTier = new java.util.EnumMap<>(EntityType.Tier.class);
        for (EntityType t : EntityType.values()) {
            all += t.size(EntityType.Scope.ALL);
            perTier.merge(t.tier, t.size(EntityType.Scope.ALL), Integer::sum);
            typesPerTier.merge(t.tier, 1, Integer::sum);
            int pos = 0;
            for (EntityType.Metric m : t.metrics(EntityType.Scope.ALL)) {
                T.eq(pos++, m.position, t.id + " positions: dashboard rows first");
                // One leaf per label, except the 8 disputed rows of rule 1, which
                // stay verbatim even where the label is released with a suffix.
                if (!DISPUTED.contains(t.id + " " + m.label)) {
                    String prev = leafOfLabel.put(m.label, m.leaf);
                    T.check(prev == null || prev.equals(m.leaf), "one leaf per label: " + m.label);
                }
                if (m.dashboard) continue;
                java.util.Map<String, String> a = appendix.get(t.id);
                boolean inA = a != null && a.containsKey(m.label);
                boolean inP3 = extras.containsKey(t.id) && extras.get(t.id).contains(m.label);
                T.check(inA ^ inP3, t.id + " " + m.label + " in exactly one source");
            }
            T.eq(t.size(), t.metrics().size(), t.id + " dashboard size");
        }
        T.eq(1805, all, "1,805 keys under all: 451 + 1,144 + 210 (P3)");
        T.eq(69, EntityType.values().length, "69 types");
        T.eq(407, perTier.get(EntityType.Tier.CORE), "core keys under all");
        T.eq(390 + 105, perTier.get(EntityType.Tier.CLUSTER), "cluster: 390 + 105 P3");
        T.eq(634 + 105, perTier.get(EntityType.Tier.HOST), "host: 634 + 105 P3");
        T.eq(126, perTier.get(EntityType.Tier.DISK), "disk keys under all");
        T.eq(38, perTier.get(EntityType.Tier.VM), "VM keys under all");
        T.eq(List.of(3, 34, 19, 7, 6), List.of(typesPerTier.get(EntityType.Tier.CORE),
                typesPerTier.get(EntityType.Tier.HOST), typesPerTier.get(EntityType.Tier.CLUSTER),
                typesPerTier.get(EntityType.Tier.DISK), typesPerTier.get(EntityType.Tier.VM)),
                "types per tier");
        T.eq(null, EntityType.HOST_DOMOWNER.requestLabels(EntityType.Scope.ALL), "no filter under all");
        T.eq(EntityType.HOST_DOMOWNER.labels(),
                EntityType.HOST_DOMOWNER.requestLabels(EntityType.Scope.DASHBOARD), "dashboard filter");

        // The 36 new types: forms, targets, flags (v3 "New types", Q5, Q6).
        Object[][] forms = {
                {EntityType.HOST_CPU, EntityType.IdForm.ONE_OR_TWO, EntityType.Target.HOST},
                {EntityType.DOM_WORLD_CPU, EntityType.IdForm.WORLD, EntityType.Target.HOST},
                {EntityType.LSOM_WORLD_CPU, EntityType.IdForm.WORLD, EntityType.Target.HOST},
                {EntityType.HEAP_MEMORY, EntityType.IdForm.TWO, EntityType.Target.HOST},
                {EntityType.SLAB_MEMORY, EntityType.IdForm.TWO, EntityType.Target.HOST},
                {EntityType.VSAN_ISCSI_TARGET, EntityType.IdForm.TWO, EntityType.Target.HOST},
                {EntityType.VSAN_ISCSI_LUN, EntityType.IdForm.LUN, EntityType.Target.HOST},
                {EntityType.DISK_GROUP, EntityType.IdForm.SELF, EntityType.Target.DISK},
                {EntityType.CLOM_DISK, EntityType.IdForm.SELF, EntityType.Target.DISK},
                {EntityType.DDH_DISK, EntityType.IdForm.SELF, EntityType.Target.DISK},
                {EntityType.REMOTE_VIRTUAL_MACHINE, EntityType.IdForm.ONE, EntityType.Target.VM},
                {EntityType.REMOTE_VIRTUAL_DISK, EntityType.IdForm.REMOTE_PATH, EntityType.Target.VM},
                {EntityType.REMOTE_VSCSI, EntityType.IdForm.TWO, EntityType.Target.VM},
                {EntityType.CLUSTER_RESYNC, EntityType.IdForm.ONE, EntityType.Target.CLUSTER_OR_HOST},
                {EntityType.CLUSTER_PMEM, EntityType.IdForm.ONE, EntityType.Target.CLUSTER_OR_HOST},
                {EntityType.VSAN_DP_HISTORICAL_STATS, EntityType.IdForm.ONE, EntityType.Target.CLUSTER},
                {EntityType.CLUSTER_REMOTEDOMCLIENT, EntityType.IdForm.ONE, EntityType.Target.CLUSTER}};
        for (Object[] f : forms) {
            EntityType t = (EntityType) f[0];
            T.eq(f[1], t.form, t.id + " form");
            T.eq(f[2], t.target, t.id + " target");
        }
        java.util.Set<EntityType> strikes = java.util.EnumSet.of(EntityType.CLOM_DISK,
                EntityType.CLOM_HOST, EntityType.CMMDS_WORKLOAD, EntityType.DDH_DISK,
                EntityType.HEAP_MEMORY, EntityType.SLAB_MEMORY, EntityType.REMOTE_VIRTUAL_DISK,
                EntityType.REMOTE_VIRTUAL_MACHINE, EntityType.REMOTE_VSCSI);
        java.util.Set<EntityType> remote = java.util.EnumSet.of(EntityType.CLUSTER_REMOTEDOMCLIENT,
                EntityType.COMPUTECLUSTER_REMOTEDOMCLIENT, EntityType.HOST_REMOTEDOMCLIENT,
                EntityType.COMPUTEHOST_REMOTEDOMCLIENT, EntityType.REMOTE_VIRTUAL_MACHINE,
                EntityType.REMOTE_VIRTUAL_DISK, EntityType.REMOTE_VSCSI);
        for (EntityType t : EntityType.values()) {
            T.eq(strikes.contains(t), t.faultStrikes, t.id + " Q5 strikes");
            T.eq(remote.contains(t), t.remote, t.id + " remote");
        }
        T.eq("vSAN Perf|pCPU|pcpuUsedPct", EntityType.HOST_CPU.statKey(null,
                EntityType.HOST_CPU.metric("pcpuUsedPct", EntityType.Scope.ALL)), "pCPU aggregate");
        T.eq("vSAN Perf|pCPU:cpu-0|pcpuUsedPct", EntityType.HOST_CPU.statKey("cpu-0",
                EntityType.HOST_CPU.metric("pcpuUsedPct", EntityType.Scope.ALL)), "pCPU per CPU");
        T.eq("vSAN Perf|System Memory|pctMemUsed", EntityType.SYSTEM_MEM.statKey(null,
                EntityType.SYSTEM_MEM.metric("pctMemUsed", EntityType.Scope.ALL)),
                "Q4: pctMemUsed verbatim, integer percent as served");
        T.check(!EntityType.SYSTEM_MEM.metric("pctMemUsed", EntityType.Scope.ALL).perMille,
                "Q4: no per-mille transform");
    }

    /** The names in backticks of one markdown line. */
    static List<String> ticks(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("`([A-Za-z0-9-]+)`").matcher(s);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** v3 "Units: leaf rule for new labels", rules 1 to 7, first match wins. */
    static String v3Leaf(String type, String label, String[] unit,
            java.util.Map<String, EntityType.Metric> released) {
        if (DISPUTED.contains(type + " " + label)) return label;                  // 1
        if (released.containsKey(label)) return released.get(label).leaf;         // 2
        if ("true".equals(unit[2])) return label + "Pct";                         // 3
        if (label.equals("totalMbMemUsed")) return label;                         // 7
        String u = unit[1];
        if (u.equals("none")) return label;                                       // 4
        if (u.equals("MISC.IO_OPERATIONS_PER_SECOND")) return label;              // 5
        switch (u) {                                                              // 6
            case "TIME.MICROSECONDS":
                return anyWord(label, "Us", "USec", "Sec", "Bps") ? label : label + "Us";
            case "DATA_RATE.BIBYTE_PER_SECOND":
                return anyWord(label, "Us", "USec", "Sec", "Bps") ? label : label + "BytesPerSec";
            case "DATA_SIZE.BYTE": return anyWord(label, "Bytes") ? label : label + "Bytes";
            case "TIME.MILLISECONDS": return anyWord(label, "Ms") ? label : label + "Ms";
            case "TIME.SECONDS": return anyWord(label, "Sec") ? label : label + "Sec";
            case "TIME.NANOSECONDS": return anyWord(label, "Ns") ? label : label + "Ns";
            case "RATIO.PERCENT": return anyWord(label, "Pct", "Percent") ? label : label + "Pct";
            default: throw new AssertionError("unit " + u + " of " + label);
        }
    }

    /** {@code label} contains a token as a camelCase word (not followed by a lower-case letter). */
    static boolean anyWord(String label, String... tokens) {
        for (String tok : tokens) {
            for (int i = label.indexOf(tok); i >= 0; i = label.indexOf(tok, i + 1)) {
                int j = i + tok.length();
                if (j == label.length() || !Character.isLowerCase(label.charAt(j))) return true;
            }
        }
        return false;
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
