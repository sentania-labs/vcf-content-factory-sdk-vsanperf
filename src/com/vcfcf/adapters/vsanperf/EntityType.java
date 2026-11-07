package com.vcfcf.adapters.vsanperf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Build 1 vSAN Performance Service entity types and their metric
 * catalog: 61 key definitions (38 DOM owner, 6 DOM client, 17 pNIC), the
 * table in the design's "Build 1 metric list"
 * (factory {@code knowledge/designs/vsanperf-mp-v1.md}). Plain JDK, no SDK.
 *
 * <p>Declaration order is collection order within a cluster: DOM owner
 * first (the most valuable group and the slowest call), then DOM client,
 * then pNIC, so a deadline cut loses the least useful group first.
 *
 * <p><b>Key names are permanent once data exists</b> (the Suite API has no
 * per-key delete). {@code EntityTypeTest} checks this table against a copy
 * of the design table row for row, so the two cannot drift.
 *
 * <p>Leaf rule (design Q1): the vSAN label verbatim; a per-mille label
 * (Python pack {@code PERMILLE}) is divided by 10 and gets {@code Pct}; a
 * microsecond label gets {@code Us} and a byte-rate label
 * {@code BytesPerSec}, unless it already ends in {@code Us}, {@code Sec} or
 * {@code Bps}; everything else unchanged. The leaves below are the design's
 * literal values, not computed at run time.
 */
enum EntityType {

    HOST_DOMOWNER("host-domowner", "DOM Owner", false, list(
                m("highOIODurationPercentWrite", "highOIODurationPercentWrite", false),
                m("iops", "iops", false),
                m("iopsSegCleanerUnmap", "iopsSegCleanerUnmap", false),
                m("iopsUnmap", "iopsUnmap", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgSegCleanerUnmap", "latencyAvgSegCleanerUnmapUs", false),
                m("latencyAvgUnmap", "latencyAvgUnmapUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("latencyMaxRead", "latencyMaxReadUs", false),
                m("latencyMaxSegCleanerUnmap", "latencyMaxSegCleanerUnmapUs", false),
                m("latencyMaxUnmap", "latencyMaxUnmapUs", false),
                m("latencyMaxWrite", "latencyMaxWriteUs", false),
                m("latencyStddevSegCleanerUnmap", "latencyStddevSegCleanerUnmapUs", false),
                m("latencyStddevWrite", "latencyStddevWriteUs", false),
                m("numNetSchedControllerIopsLimit", "numNetSchedControllerIopsLimit", false),
                m("numNetSchedGuestLTLatUs", "numNetSchedGuestLTLatUs", false),
                m("numNetSchedLowBand", "numNetSchedLowBand", false),
                m("numNetSchedLowBandSec", "numNetSchedLowBandSec", false),
                m("numNetSchedLowBandThreshUs", "numNetSchedLowBandThreshUs", false),
                m("numNetSchedLowToMidBand", "numNetSchedLowToMidBand", false),
                m("numNetSchedMidBand", "numNetSchedMidBand", false),
                m("numNetSchedMidBandSec", "numNetSchedMidBandSec", false),
                m("numNetSchedMidToLowBand", "numNetSchedMidToLowBand", false),
                m("numNetSchedRollingAvgLatUs", "numNetSchedRollingAvgLatUs", false),
                m("numNetSchedSeenNonResyncTpBps", "numNetSchedSeenNonResyncTpBps", false),
                m("numNetSchedSeenTotalTpBps", "numNetSchedSeenTotalTpBps", false),
                m("oio", "oio", false),
                m("owner2PCCommitLatencyAvgUs", "owner2PCCommitLatencyAvgUs", false),
                m("readLeafOwnerLatencyLocal", "readLeafOwnerLatencyLocalUs", false),
                m("readLeafOwnerLatencyRemote", "readLeafOwnerLatencyRemoteUs", false),
                m("recoveryWriteCongestion", "recoveryWriteCongestion", false),
                m("segCleanerUnmapCongestion", "segCleanerUnmapCongestion", false),
                m("segCleanerUnmapLeafOwnerMaxLatencyAvgUs", "segCleanerUnmapLeafOwnerMaxLatencyAvgUs", false),
                m("serverSwitchCount", "serverSwitchCount", false),
                m("throughputSegCleanerUnmap", "throughputSegCleanerUnmapBytesPerSec", false),
                m("unmapCount", "unmapCount", false),
                m("writeLeafOwnerLatencyLocal", "writeLeafOwnerLatencyLocalUs", false),
                m("writeLeafOwnerLatencyRemote", "writeLeafOwnerLatencyRemoteUs", false))),

    HOST_DOMCLIENT("host-domclient", "DOM Client", false, list(
                m("congestion", "congestion", false),
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false))),

    VSAN_PNIC_NET("vsan-pnic-net", "pNIC", true, list(
                m("ioChainDrops", "ioChainDrops", false),
                m("ioChainRxdrops", "ioChainRxdrops", false),
                m("ioChainTxdrops", "ioChainTxdrops", false),
                m("pauseCount", "pauseCountPct", true),
                m("pfcCountRaw", "pfcCountRaw", false),
                m("portRxDrops", "portRxDropsPct", true),
                m("portTxDrops", "portTxDropsPct", true),
                m("portTxpkts", "portTxpkts", false),
                m("rxCrcErr", "rxCrcErrPct", true),
                m("rxCrcErrRaw", "rxCrcErrRaw", false),
                m("rxErr", "rxErrPct", true),
                m("rxFifoErr", "rxFifoErrPct", true),
                m("rxMissErr", "rxMissErrPct", true),
                m("rxMissErrRaw", "rxMissErrRaw", false),
                m("rxOvErr", "rxOvErrPct", true),
                m("txCarErr", "txCarErrPct", true),
                m("txErr", "txErrPct", true)));

    /** Root segment of every pushed key (design Q2). */
    static final String ROOT = "vSAN Perf";

    /** One catalog row: the vSAN label, the Ops key leaf, the transform. */
    static final class Metric {
        final String label;
        final String leaf;
        /** The service reports this label per mille; pushed divided by 10. */
        final boolean perMille;

        Metric(String label, String leaf, boolean perMille) {
            this.label = label;
            this.leaf = leaf;
            this.perMille = perMille;
        }

        /** The pushed value for a raw service value. */
        double convert(double raw) {
            return perMille ? raw / 10.0 : raw;
        }
    }

    /** The {@code entityRefId} type prefix, e.g. {@code host-domowner}. */
    final String id;
    /** The key group under {@link #ROOT}, from the Python pack's KIND_SUFFIX. */
    final String group;
    /** Instanced: the entity id carries a second part (the NIC name). */
    final boolean instanced;
    private final Map<String, Metric> byLabel;

    EntityType(String id, String group, boolean instanced, List<Metric> metrics) {
        this.id = id;
        this.group = group;
        this.instanced = instanced;
        Map<String, Metric> m = new LinkedHashMap<>();
        for (Metric x : metrics) {
            if (m.put(x.label, x) != null) {
                throw new IllegalStateException(id + ": duplicate label " + x.label);
            }
        }
        this.byLabel = Collections.unmodifiableMap(m);
    }

    private static Metric m(String label, String leaf, boolean perMille) {
        return new Metric(label, leaf, perMille);
    }

    private static List<Metric> list(Metric... ms) {
        List<Metric> l = new ArrayList<>();
        Collections.addAll(l, ms);
        return l;
    }

    /** The catalog row for a vSAN label, or {@code null} when not collected. */
    Metric metric(String label) {
        return byLabel.get(label);
    }

    /** Every catalog row, in table order. */
    List<Metric> metrics() {
        return new ArrayList<>(byLabel.values());
    }

    /** The {@code labels} array sent in the spec: exactly the catalog labels. */
    List<String> labels() {
        return new ArrayList<>(byLabel.keySet());
    }

    /**
     * The Ops stat key: {@code vSAN Perf|<group>|<leaf>}, or
     * {@code vSAN Perf|<group>:<instance>|<leaf>} for an instanced type.
     */
    String statKey(String instance, Metric metric) {
        StringBuilder b = new StringBuilder(ROOT).append('|').append(group);
        if (instanced) b.append(':').append(instance);
        return b.append('|').append(metric.leaf).toString();
    }

    /** The type with this {@code entityRefId} prefix, or {@code null}. */
    static EntityType byId(String id) {
        for (EntityType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
