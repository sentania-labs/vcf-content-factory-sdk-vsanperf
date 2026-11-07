package com.vcfcf.adapters.vsanperf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * T1: the 61-key catalog equals the design's table (a verbatim copy in
 * tests/fixtures/design-build1-metrics.md) row for row: label, stat key,
 * per-mille transform; and the design's own leaf rule reproduces every leaf
 * from the label and unit columns, so the table is self-consistent.
 */
public final class EntityTypeTest {

    static final class Row {
        String type, label, key, unit, transform;
    }

    public static void main(String[] args) throws Exception {
        List<Row> rows = designRows();
        T.eq(61, rows.size(), "design rows");
        int n = 0;
        for (EntityType t : EntityType.values()) n += t.metrics().size();
        T.eq(61, n, "catalog rows");
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
        System.out.println("EntityTypeTest: all assertions passed");
    }

    /** The design's Q1 leaf rule. */
    static String ruleLeaf(String label, String unit, boolean perMille) {
        if (perMille) return label + "Pct";
        boolean suffixed = label.endsWith("Us") || label.endsWith("Sec") || label.endsWith("Bps");
        if (!suffixed && unit.equals("microseconds")) return label + "Us";
        if (!suffixed && unit.equals("bytes/s")) return label + "BytesPerSec";
        return label;
    }

    static List<Row> designRows() throws Exception {
        Pattern head = Pattern.compile("^### `([a-z-]+)`");
        List<Row> out = new ArrayList<>();
        String type = null;
        for (String line : T.read("design-build1-metrics.md").split("\n")) {
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
