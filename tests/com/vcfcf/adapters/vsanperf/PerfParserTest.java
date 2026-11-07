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
    /** Newest bucket of the build 10 captures (PROVENANCE-2026-11-06-build2.md). */
    static final long T0505 = java.time.Instant.parse("2026-11-07T05:05:00Z").toEpochMilli();
    static final String LAB_CLUSTER_UUID = "90d067b8-0077-11f1-af60-000c297e270a";

    /** The verbatim capture of each non-empty build 10 type (file, entities, series). */
    static final Object[][] BUILD2 = {
            {EntityType.VSAN_CLUSTER_CAPACITY, "queryperf-vsan-cluster-capacity-2026-11-06.json", 1, 5},
            {EntityType.CLUSTER_DOMCLIENT, "queryperf-cluster-domclient-labels-2026-11-06.json", 1, 11},
            {EntityType.CLUSTER_DOMCOMPMGR, "queryperf-cluster-domcompmgr-2026-11-06.json", 1, 12},
            {EntityType.CLUSTER_DOMOWNER, "queryperf-cluster-domowner-2026-11-06.json", 1, 23},
            {EntityType.CLUSTER_ESA_DEDUP_DOMCLIENT_IO,
                    "queryperf-cluster-esa-dedup-domclient-io-2026-11-06.json", 1, 24},
            {EntityType.CLUSTER_RDT_NETWORK_LATENCY,
                    "queryperf-cluster-rdt-network-latency-2026-11-06.json", 1, 9},
            {EntityType.CLUSTER_ZDOM_TOP_STATS, "queryperf-cluster-zdom-top-stats-2026-11-06.json", 1, 7},
            {EntityType.CMMDS_NET, "queryperf-cmmds-net-2026-11-06.json", 5, 50},
            {EntityType.VSAN_CPU, "queryperf-vsan-cpu-2026-11-06.json", 5, 35},
            {EntityType.HOST_DOMCOMPMGR, "queryperf-host-domcompmgr-2026-11-06.json", 5, 80},
            {EntityType.HOST_ESA_DEDUP_DOMCLIENT_IO,
                    "queryperf-host-esa-dedup-domclient-io-2026-11-06.json", 5, 120},
            {EntityType.VSAN_HOST_NET, "queryperf-vsan-host-net-2026-11-06.json", 5, 40},
            {EntityType.HOST_ZDOM_TOP_STATS, "queryperf-host-zdom-top-stats-2026-11-06.json", 5, 55},
            {EntityType.VSAN_MEMORY, "queryperf-vsan-memory-2026-11-06.json", 5, 60},
            {EntityType.RDT_NETWORK_LATENCY, "queryperf-rdt-network-latency-2026-11-06.json", 5, 55},
            {EntityType.VSAN_TCPIP_STATS, "queryperf-vsan-tcpip-stats-2026-11-06.json", 5, 60},
            {EntityType.VSAN_VNIC_NET, "queryperf-vsan-vnic-net-2026-11-06.json", 5, 50},
            {EntityType.VNIC_RDT_NETWORK_LATENCY,
                    "queryperf-vnic-rdt-network-latency-2026-11-06.json", 5, 50},
            {EntityType.ZDOM_VTX, "queryperf-zdom-vtx-2026-11-06.json", 5, 50},
    };

    /** The types empty on the lab (wildcard echo, PROVENANCE-2026-11-06-build2.md). */
    static final EntityType[] EMPTY_ON_LAB = {EntityType.VSAN_DIRECT_CLUSTER,
            EntityType.VSAN_FILE_SERVICE, EntityType.IOINSIGHT, EntityType.VSAN_DIRECT_HOST};

    /** The echo body exactly as quoted in the provenance file, for {@code type}. */
    static String echo(EntityType type) {
        return "[{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"" + type.id
                + ":*\",\"sampleInfo\":\"\"}]";
    }

    public static void main(String[] args) throws Exception {
        clusterDomclientSample();
        perTypeSamples();
        build2Captures();
        instancedIdForms();
        emptyEchoes();
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
        List<PerfParser.Sample> b = PerfParser.buckets(
                new EntityType.Metric("iopsRead", "iopsRead", false), times, iopsRead);
        T.eq(2, b.size(), "both buckets (v2 one bucket rule)");
        PerfParser.Sample s = b.get(1);
        T.near(457, s.value, "iopsRead newest value");
        T.eq(T0010, s.timestamp, "iopsRead sample time");
        T.eq(T0010 - 300_000, b.get(0).timestamp, "older bucket first");
        // Another type: parsed as host-domclient every entity is malformed.
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
                T.check(s.timestamp == T0230 || s.timestamp == T0230 - 300_000,
                        "every pNIC sample from the 02:25 or 02:30 bucket");
            }
        }
    }

    static PerfParser.Result check(String file, EntityType type, int entities, int series)
            throws Exception {
        return check(file, type, entities, series, T0230, 5);
    }

    /**
     * Per capture: entity and series counts (PROVENANCE tables), every series
     * kept with both of its buckets (no None in these captures), samples at
     * the newest bucket and the one before it only, and the distinct first
     * id parts.
     */
    static PerfParser.Result check(String file, EntityType type, int entities, int series,
            long newest, int uuids) throws Exception {
        PerfParser.Result r = PerfParser.parse(T.vendor(file), type);
        T.eq(entities, r.entities.size(), file + " entities");
        T.eq(series, r.seriesSeen, file + " series seen");
        T.eq(series, r.seriesKept, file + " series kept (no None in the capture)");
        T.eq(2 * series, r.samples, file + " two buckets per series");
        T.eq(0, r.echoes, file + " echoes");
        T.eq(0, r.malformed, file + " malformed");
        T.check(!r.isEmptyEcho(), file + " not empty");
        Set<String> ids = new HashSet<>();
        for (PerfParser.Entity e : r.entities) {
            ids.add(e.ref.uuid);
            T.eq(2 * type.size(), e.samples.size(), file + " every label, two buckets");
            for (PerfParser.Sample s : e.samples) {
                T.check(s.timestamp == newest || s.timestamp == newest - 300_000,
                        file + " samples from the window's two buckets");
            }
        }
        T.eq(uuids, ids.size(), file + " distinct uuids");
        return r;
    }

    /**
     * T1, the 19 verbatim build 10 captures: entity and series counts from
     * PROVENANCE-2026-11-06-build2.md, both buckets of every series (705 host
     * and 91 cluster series on the lab, 1,410 and 182 samples), one cluster
     * uuid for the cluster types and 5 node uuids for the host types.
     */
    static void build2Captures() throws Exception {
        int clusterSeries = 0;
        int hostSeries = 0;
        for (Object[] row : BUILD2) {
            EntityType t = (EntityType) row[0];
            int entities = (Integer) row[2];
            int series = (Integer) row[3];
            PerfParser.Result r = check((String) row[1], t, entities, series, T0505,
                    t.tier == EntityType.Tier.CLUSTER ? 1 : 5);
            if (t.tier == EntityType.Tier.CLUSTER) {
                clusterSeries += series;
                T.eq(LAB_CLUSTER_UUID, r.entities.get(0).ref.uuid, t.id + " cluster uuid");
            } else {
                hostSeries += series;
            }
        }
        T.eq(91, clusterSeries, "cluster tier lab series (design)");
        T.eq(705, hostSeries, "host tier lab series (design)");
        // The capacity ratio is pushed as served (x 100; 145 on the lab per the design).
        PerfParser.Result cap = PerfParser.parse(T.vendor(
                "queryperf-vsan-cluster-capacity-2026-11-06.json"), EntityType.VSAN_CLUSTER_CAPACITY);
        for (PerfParser.Sample x : cap.entities.get(0).samples) {
            if (x.metric.label.equals("dedupRatio")) T.check(x.value > 1, "dedupRatio as served");
        }
    }

    /** T1 grammar: the instanced forms, from the captures and constructed edge cases. */
    static void instancedIdForms() throws Exception {
        for (Object[] row : BUILD2) {
            EntityType t = (EntityType) row[0];
            if (!t.instanced) continue;
            String want = t == EntityType.VSAN_TCPIP_STATS ? "defaultTcpipStack" : "vmk2";
            for (PerfParser.Entity e : PerfParser.parse(T.vendor((String) row[1]), t).entities) {
                T.eq(want, e.ref.instance, t.id + " instance");
                T.check(e.ref.raw.startsWith(t.id + ":" + e.ref.uuid + "|"), t.id + " raw id kept");
            }
        }
        // nodeUuid|stack
        EntityRef a = EntityRef.parse("vsan-tcpip-stats:n1|defaultTcpipStack",
                EntityType.VSAN_TCPIP_STATS);
        T.eq("n1", a.uuid, "tcpip node");
        T.eq("defaultTcpipStack", a.instance, "tcpip stack");
        T.eq(null, EntityRef.parse("vsan-tcpip-stats:n1", EntityType.VSAN_TCPIP_STATS), "no stack");
        T.eq(null, EntityRef.parse("vsan-tcpip-stats:n1|s|x", EntityType.VSAN_TCPIP_STATS),
                "extra part");
        // nodeUuid|stack|vmk: the vmk is the instance, the stack is dropped
        EntityRef b = EntityRef.parse("vsan-vnic-net:n1|defaultTcpipStack|vmk2",
                EntityType.VSAN_VNIC_NET);
        T.eq("n1", b.uuid, "vnic node");
        T.eq("vmk2", b.instance, "vnic vmk");
        T.eq("vSAN Perf|vNIC:vmk2|" + EntityType.VSAN_VNIC_NET.metrics().get(0).leaf,
                EntityType.VSAN_VNIC_NET.statKey(b.instance, EntityType.VSAN_VNIC_NET.metrics().get(0)),
                "stack not in the key");
        T.eq(null, EntityRef.parse("vsan-vnic-net:n1|vmk2", EntityType.VSAN_VNIC_NET), "two parts");
        T.eq(null, EntityRef.parse("vsan-vnic-net:n1||vmk2", EntityType.VSAN_VNIC_NET), "empty stack");
        // nodeUuid|vmk
        EntityRef c = EntityRef.parse("vnic-rdt-network-latency:n1|vmk2",
                EntityType.VNIC_RDT_NETWORK_LATENCY);
        T.eq("vmk2", c.instance, "vnic rdt vmk");
        T.eq(null, EntityRef.parse("vnic-rdt-network-latency:n1|s|vmk2",
                EntityType.VNIC_RDT_NETWORK_LATENCY), "three parts");
        // Cluster uuid form
        EntityRef d = EntityRef.parse("cluster-domowner:" + LAB_CLUSTER_UUID, EntityType.CLUSTER_DOMOWNER);
        T.eq(LAB_CLUSTER_UUID, d.uuid, "cluster uuid");
        T.eq(null, d.instance, "no instance");
        // A wrong part count is malformed, counted with its part count, never the id.
        String body = "[{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":"
                + "\"vsan-vnic-net:n1|vmk2\",\"sampleInfo\":\"2026-11-07 05:05:00\",\"value\":["
                + series("rxThroughput", "1") + "]}]";
        PerfParser.Result r = PerfParser.parse(body, EntityType.VSAN_VNIC_NET);
        T.eq(1, r.malformed, "counted");
        T.eq(2, r.malformedParts, "its part count");
        T.check(!r.isEmptyEcho(), "a malformed entity is not an empty answer");
    }

    /** T1: the echo quoted in PROVENANCE-2026-11-06-build2.md for the empty types. */
    static void emptyEchoes() throws Exception {
        for (EntityType t : EMPTY_ON_LAB) {
            PerfParser.Result r = PerfParser.parse(echo(t), t);
            T.check(r.isEmptyEcho(), t.id + " echo is empty");
            T.eq(0, r.entities.size(), t.id + " no rows");
        }
        PerfParser.Result data = PerfParser.parse(T.vendor("queryperf-zdom-vtx-2026-11-06.json"),
                EntityType.ZDOM_VTX);
        T.check(!data.isEmptyEcho(), "data is not empty");
        T.check(!PerfParser.parse("[]", EntityType.ZDOM_VTX).isEmptyEcho(),
                "an empty array is not the echo (never learned on it)");
    }

    /** Constructed: None handling (the token is literal in live data, 2,218 in one cycle). */
    static void noneTokens() throws Exception {
        String body = "[{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\"host-domclient:n1\","
                + "\"sampleInfo\":\"2026-11-07 00:05:00,2026-11-07 00:10:00\",\"value\":["
                + series("iopsRead", "806,None") + "," + series("iopsWrite", "None,None") + ","
                + series("oio", "NaN,7") + "," + series("congestion", "3,") + "]}]";
        PerfParser.Result r = PerfParser.parse(body, EntityType.HOST_DOMCLIENT);
        List<PerfParser.Sample> s = r.entities.get(0).samples;
        T.eq(3, s.size(), "all-None series yields no sample; None, NaN and blank skipped");
        T.eq(3, r.seriesKept, "three series kept");
        T.eq("iopsRead", s.get(0).metric.label, "first kept");
        T.near(806, s.get(0).value, "the non-None bucket");
        T.eq(T0010 - 300_000, s.get(0).timestamp, "with its own (older) timestamp");
        T.near(7, s.get(1).value, "NaN is not a value");
        T.near(3, s.get(2).value, "blank newest token skipped");
        T.eq(null, PerfParser.number("None"), "None");
        // Review N3: ordered by time, not by position (constructed, descending order).
        long[] desc = PerfParser.sampleTimes("2026-11-07 00:10:00,2026-11-07 00:05:00");
        EntityType.Metric oio = new EntityType.Metric("oio", "oio", false);
        List<PerfParser.Sample> byTime = PerfParser.buckets(oio, desc, "5,9");
        T.eq(2, byTime.size(), "both buckets");
        T.near(9, byTime.get(0).value, "00:05 first");
        T.near(5, byTime.get(1).value, "the 00:10 value, though it is first in the CSV");
        T.eq(T0010, byTime.get(1).timestamp, "its time");
        List<PerfParser.Sample> skipNone = PerfParser.buckets(oio, desc, "None,9");
        T.eq(1, skipNone.size(), "a None bucket is not a value");
        T.near(9, skipNone.get(0).value, "the older bucket stays");
        long[] dup = PerfParser.sampleTimes("2026-11-07 00:10:00,2026-11-07 00:10:00");
        List<PerfParser.Sample> d = PerfParser.buckets(oio, dup, "1,2");
        T.eq(1, d.size(), "one sample per timestamp");
        T.near(2, d.get(0).value, "later position wins on a repeated time");
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
        T.eq("69fd149d-c1b8", a.uuid, "host node");
        T.eq(null, a.instance, "no instance");
        EntityRef b = EntityRef.parse("vsan-pnic-net:69fd149d|vmnic1", EntityType.VSAN_PNIC_NET);
        T.eq("69fd149d", b.uuid, "pnic node");
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
