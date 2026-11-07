package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * T1: the response parser and value selection against the verbatim
 * captures, the None rules, the empty-type echo and entityRefId splitting.
 */
public final class PerfParserTest {

    static final long T0010 = java.time.Instant.parse("2026-11-07T00:10:00Z").toEpochMilli();
    static final long T0230 = java.time.Instant.parse("2026-11-07T02:30:00Z").toEpochMilli();

    public static void main(String[] args) throws Exception {
        clusterDomclientSample();
        perTypeSamples();
        noneTokens();
        echoAndMalformed();
        entityRefSplitting();
        unreadable();
        System.out.println("PerfParserTest: all assertions passed");
    }

    /** Design T1: 1 entity, 14 series, two timestamps, iopsRead newest = 457 at 00:10 UTC. */
    static void clusterDomclientSample() throws Exception {
        SimpleJson root = SimpleJson.parse(T.vendor("queryperf-cluster-domclient-2026-11-06.json"));
        T.eq(1, root.size(), "entities");
        SimpleJson e = root.get(0);
        T.eq(14, e.get("value").size(), "series");
        long[] times = PerfParser.sampleTimes(e.get("sampleInfo").asString());
        T.eq(2, times.length, "timestamps");
        T.eq(T0010, times[1], "newest timestamp is 2026-11-07 00:10:00 UTC");
        String iopsRead = null;
        for (SimpleJson s : e.get("value").asList()) {
            if ("iopsRead".equals(s.get("metricId").get("label").asString())) {
                iopsRead = s.get("values").asString();
            }
        }
        PerfParser.Sample s = PerfParser.newest(new EntityType.Metric("iopsRead", "iopsRead", false),
                times, iopsRead);
        T.near(457, s.value, "iopsRead newest value");
        T.eq(T0010, s.timestamp, "iopsRead sample time");
        // Not a Build 1 type: parsed as host-domclient every entity is malformed.
        PerfParser.Result r = PerfParser.parse(T.vendor("queryperf-cluster-domclient-2026-11-06.json"),
                EntityType.HOST_DOMCLIENT);
        T.eq(0, r.entities.size(), "another type's entity is not taken");
        T.eq(1, r.malformed, "counted as malformed");
    }

    static void perTypeSamples() throws Exception {
        check("queryperf-host-domclient-2026-11-06.json", EntityType.HOST_DOMCLIENT, 5, 30);
        check("queryperf-host-domowner-2026-11-06.json", EntityType.HOST_DOMOWNER, 5, 190);
        PerfParser.Result p = check("queryperf-vsan-pnic-net-2026-11-06.json",
                EntityType.VSAN_PNIC_NET, 10, 170);
        Set<String> nics = new HashSet<>();
        for (PerfParser.Entity e : p.entities) {
            T.check(e.ref.instance != null, "pNIC instance");
            nics.add(e.ref.instance);
        }
        T.eq(Set.of("vmnic1", "vmnic2"), nics, "pNICs on the lab");
        // A per-mille value is divided by 10.
        PerfParser.Result raw = PerfParser.parse(T.vendor("queryperf-vsan-pnic-net-2026-11-06.json"),
                EntityType.VSAN_PNIC_NET);
        for (PerfParser.Entity e : raw.entities) {
            for (PerfParser.Sample s : e.samples) {
                T.eq(T0230, s.timestamp, "every pNIC sample from the 02:30 bucket");
            }
        }
    }

    static PerfParser.Result check(String file, EntityType type, int entities, int series)
            throws Exception {
        PerfParser.Result r = PerfParser.parse(T.vendor(file), type);
        T.eq(entities, r.entities.size(), file + " entities");
        T.eq(series, r.seriesSeen, file + " series seen");
        T.eq(series, r.seriesKept, file + " series kept (no None in the capture)");
        T.eq(0, r.echoes, file + " echoes");
        T.eq(0, r.malformed, file + " malformed");
        Set<String> nodes = new HashSet<>();
        for (PerfParser.Entity e : r.entities) {
            nodes.add(e.ref.node);
            for (PerfParser.Sample s : e.samples) {
                T.eq(T0230, s.timestamp, file + " newest bucket is 02:30 UTC");
            }
        }
        T.eq(5, nodes.size(), file + " node uuids");
        return r;
    }

    /** Constructed: None handling (the token is literal in live data, 2,218 in one cycle). */
    static void noneTokens() throws Exception {
        String body = "[{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"host-domclient:n1\","
                + "\"sampleInfo\":\"2026-11-07 00:05:00,2026-11-07 00:10:00\",\"value\":["
                + series("iopsRead", "806,None") + "," + series("iopsWrite", "None,None") + ","
                + series("oio", "NaN,7") + "," + series("congestion", "3,") + "]}]";
        PerfParser.Result r = PerfParser.parse(body, EntityType.HOST_DOMCLIENT);
        List<PerfParser.Sample> s = r.entities.get(0).samples;
        T.eq(3, s.size(), "all-None series yields no sample");
        T.eq("iopsRead", s.get(0).metric.label, "first kept");
        T.near(806, s.get(0).value, "newest non-None wins");
        T.eq(T0010 - 300_000, s.get(0).timestamp, "with its own (older) timestamp");
        T.near(7, s.get(1).value, "NaN is not a value");
        T.near(3, s.get(2).value, "blank newest token skipped");
        T.eq(null, PerfParser.number("None"), "None");
        // Review N3: newest by time, not by position (constructed, descending order).
        long[] desc = PerfParser.sampleTimes("2026-11-07 00:10:00,2026-11-07 00:05:00");
        PerfParser.Sample byTime = PerfParser.newest(new EntityType.Metric("oio", "oio", false),
                desc, "5,9");
        T.near(5, byTime.value, "the 00:10 value, though it is first");
        T.eq(T0010, byTime.timestamp, "its time");
        PerfParser.Sample skipNone = PerfParser.newest(new EntityType.Metric("oio", "oio", false),
                desc, "None,9");
        T.near(9, skipNone.value, "newest None falls back to the older value");
        T.eq(null, PerfParser.number("Infinity"), "Infinity");
        T.eq(Long.MIN_VALUE, PerfParser.parseSampleTime("2026-11-07T00:10:00"), "other format");
    }

    static String series(String label, String values) {
        return "{\"_typeName\":\"VsanPerfMetricSeriesCSV\",\"metricId\":{\"_typeName\":"
                + "\"VsanPerfMetricId\",\"label\":\"" + label + "\",\"metricsCollectInterval\":300},"
                + "\"values\":\"" + values + "\"}";
    }

    /** Constructed after the api map's recorded echo shape. */
    static void echoAndMalformed() throws Exception {
        String body = "[{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"vsan-pnic-net:*\","
                + "\"sampleInfo\":\"\"},"
                + "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"vsan-pnic-net:n1\","
                + "\"sampleInfo\":\"2026-11-07 00:10:00\",\"value\":[" + series("rxErr", "5") + "]},"
                + "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"vsan-pnic-net:n1|vmnic0|x\","
                + "\"sampleInfo\":\"2026-11-07 00:10:00\",\"value\":[" + series("rxErr", "5") + "]},"
                + "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"vsan-pnic-net:n1|vmnic0\","
                + "\"sampleInfo\":\"2026-11-07 00:10:00\",\"value\":[" + series("rxErr", "5") + ","
                + series("rxErrActual", "5") + "]}]";
        PerfParser.Result r = PerfParser.parse(body, EntityType.VSAN_PNIC_NET);
        T.eq(1, r.echoes, "echo counted, zero rows");
        T.eq(2, r.malformed, "two malformed pNIC ids skipped and counted");
        T.eq(1, r.entities.size(), "one usable entity");
        T.eq(1, r.entities.get(0).samples.size(), "Actual label dropped");
        T.near(0.5, r.entities.get(0).samples.get(0).value, "per mille 5 = 0.5 %");
        PerfParser.Result empty = PerfParser.parse("[{\"_typeName\":\"VsanPerfEntityMetricCSV\","
                + "\"entityRefId\":\"host-domowner:*\",\"sampleInfo\":\"\"}]", EntityType.HOST_DOMOWNER);
        T.eq(0, empty.entities.size(), "empty type: zero rows");
    }

    static void entityRefSplitting() {
        EntityRef a = EntityRef.parse("host-domowner:69fd149d-c1b8", EntityType.HOST_DOMOWNER);
        T.eq("69fd149d-c1b8", a.node, "host node");
        T.eq(null, a.instance, "no instance");
        EntityRef b = EntityRef.parse("vsan-pnic-net:69fd149d|vmnic1", EntityType.VSAN_PNIC_NET);
        T.eq("69fd149d", b.node, "pnic node");
        T.eq("vmnic1", b.instance, "pnic nic");
        T.eq(null, EntityRef.parse("vsan-pnic-net:69fd149d", EntityType.VSAN_PNIC_NET), "no nic");
        T.eq(null, EntityRef.parse("vsan-pnic-net:|vmnic1", EntityType.VSAN_PNIC_NET), "no node");
        T.eq(null, EntityRef.parse("vsan-pnic-net:a|", EntityType.VSAN_PNIC_NET), "empty nic");
        T.eq(null, EntityRef.parse("host-domowner:a|b", EntityType.HOST_DOMOWNER), "host with part");
        T.eq(null, EntityRef.parse("host-domowner:*", EntityType.HOST_DOMOWNER), "wildcard");
        T.eq(null, EntityRef.parse("host-domowner:a*", EntityType.HOST_DOMOWNER), "any star");
        T.eq(null, EntityRef.parse("host-domclient:a", EntityType.HOST_DOMOWNER), "other type");
        T.eq(null, EntityRef.parse("host-domowner:", EntityType.HOST_DOMOWNER), "empty id");
        T.eq(null, EntityRef.parse(null, EntityType.HOST_DOMOWNER), "null");
    }

    static void unreadable() {
        T.expect(java.io.IOException.class, () -> PerfParser.parse("{\"a\":1}",
                EntityType.HOST_DOMOWNER), "object is not an entity array");
        T.expect(java.io.IOException.class, () -> PerfParser.parse("<html>",
                EntityType.HOST_DOMOWNER), "not JSON");
    }
}
