package com.vcfcf.adapters.vsanperf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The vSAN Performance Service entity types and their metric catalog.
 * Core (Build 1, always on): 61 keys over 3 types, the design's "Build 1
 * metric list" (factory {@code knowledge/designs/vsanperf-mp-v1.md}).
 * Build 2 (design {@code knowledge/designs/vsanperf-mp-v2.md}): the cluster
 * tier (10 types, 189 keys, onto ClusterComputeResource), the host tier (13
 * types, 149 keys, onto HostSystem), the disk tier (4 types, 31 keys, onto
 * HostSystem instanced by disk uuid) and the VM tier (3 types, 21 keys, onto
 * VirtualMachine). 33 types, 451 keys. Plain JDK, no SDK.
 *
 * <p>Declaration order is collection order within a cluster (v2 "Steps"):
 * core (DOM owner first, the most valuable group and the slowest call, then
 * DOM client, then pNIC), then the cluster, host, disk and VM tiers, so a
 * deadline cut loses the tail first (the VM types, which also run on a
 * cadence).
 *
 * <p><b>Key names are permanent once data exists</b> (the Suite API has no
 * per-key delete). {@code EntityTypeTest} checks this table against copies
 * of the design tables row for row, so the two cannot drift. ROOT and the
 * group column are the only source of key names (v2 "Key tree alignment").
 *
 * <p>Leaf rule (v1 Q1, extended by v2 Q6): the vSAN label verbatim; a
 * per-mille label (Python pack {@code PERMILLE}) is divided by 10 and gets
 * {@code Pct}; a microsecond label gets {@code Us} and a byte-rate label
 * {@code BytesPerSec}, unless it already ends in {@code Us}, {@code Sec} or
 * {@code Bps}; a byte size gets {@code Bytes} (except {@code capacity-disk}
 * {@code deleteCongestion}, a congestion score, R3b); everything else
 * unchanged.
 * The leaves below are the design's literal values, not computed at run
 * time.
 */
enum EntityType {

    HOST_DOMOWNER("host-domowner", Tier.CORE, Target.HOST, "DOM Owner", IdForm.ONE, list(
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

    HOST_DOMCLIENT("host-domclient", Tier.CORE, Target.HOST, "DOM Client", IdForm.ONE, list(
                m("congestion", "congestion", false),
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false))),

    VSAN_PNIC_NET("vsan-pnic-net", Tier.CORE, Target.HOST, "pNIC", IdForm.TWO, list(
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
                m("txErr", "txErrPct", true))),

    // ---- Cluster tier (v2 "Cluster tier: 10 types, 189 keys") ----

    VSAN_CLUSTER_CAPACITY("vsan-cluster-capacity", Tier.CLUSTER, Target.CLUSTER, "Capacity", IdForm.ONE, list(
                m("dedupRatio", "dedupRatio", false),
                m("free", "freeBytes", false),
                m("savedByDedup", "savedByDedupBytes", false),
                m("total", "totalBytes", false),
                m("used", "usedBytes", false))),

    CLUSTER_DOMCLIENT("cluster-domclient", Tier.CLUSTER, Target.CLUSTER, "DOM Client", IdForm.ONE, list(
                m("congestion", "congestion", false),
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false),
                m("readCongestion", "readCongestion", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false),
                m("unmapCongestion", "unmapCongestion", false),
                m("writeCongestion", "writeCongestion", false))),

    CLUSTER_DOMCOMPMGR("cluster-domcompmgr", Tier.CLUSTER, Target.CLUSTER, "DOM Comp Mgr", IdForm.ONE, list(
                m("congestion", "congestion", false),
                m("iopsRead", "iopsRead", false),
                m("iopsRecWrite", "iopsRecWrite", false),
                m("iopsResyncRead", "iopsResyncRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latAvgResyncRead", "latAvgResyncReadUs", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgRecWrite", "latencyAvgRecWriteUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false),
                m("throughputRecWrite", "throughputRecWriteBytesPerSec", false),
                m("tputResyncRead", "tputResyncReadBytesPerSec", false))),

    CLUSTER_DOMOWNER("cluster-domowner", Tier.CLUSTER, Target.CLUSTER, "DOM Owner", IdForm.ONE, list(
                m("avgResyncParallelism", "avgResyncParallelism", false),
                m("congestion", "congestion", false),
                m("iopsRead", "iopsRead", false),
                m("iopsRecWrite", "iopsRecWrite", false),
                m("iopsResyncRead", "iopsResyncRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("numCompleteFullResyncOps", "numCompleteFullResyncOps", false),
                m("numCompleteResyncOps", "numCompleteResyncOps", false),
                m("numInflightPriorityResyncJobs", "numInflightPriorityResyncJobs", false),
                m("numInflightSharedResyncJobs", "numInflightSharedResyncJobs", false),
                m("numPendingDecomResyncJobs", "numPendingDecomResyncJobs", false),
                m("numPendingFullResyncJobs", "numPendingFullResyncJobs", false),
                m("numPendingResyncJobs", "numPendingResyncJobs", false),
                m("numRunningFullResyncJobs", "numRunningFullResyncJobs", false),
                m("numRunningResyncJobs", "numRunningResyncJobs", false),
                m("numSuspendedResyncJobs", "numSuspendedResyncJobs", false),
                m("oio", "oio", false),
                m("readCongestion", "readCongestion", false),
                m("tputRead", "tputReadBytesPerSec", false),
                m("tputWrite", "tputWriteBytesPerSec", false),
                m("writeCongestion", "writeCongestion", false))),

    CLUSTER_ESA_DEDUP_DOMCLIENT_IO("cluster-esa-dedup-domclient-io", Tier.CLUSTER, Target.CLUSTER, "ESA Dedup DOM Client", IdForm.ONE, list(
                m("dedupChunkReadCount", "dedupChunkReadCount", false),
                m("dedupChunkWriteCount", "dedupChunkWriteCount", false),
                m("dedupEngineReadCount", "dedupEngineReadCount", false),
                m("dedupEngineWriteCount", "dedupEngineWriteCount", false),
                m("dedupHashReadCount", "dedupHashReadCount", false),
                m("dedupHashWriteCount", "dedupHashWriteCount", false),
                m("iopsDedupChunkObjRead", "iopsDedupChunkObjRead", false),
                m("iopsDedupChunkObjWrite", "iopsDedupChunkObjWrite", false),
                m("iopsDedupEngineRead", "iopsDedupEngineRead", false),
                m("iopsDedupEngineWrite", "iopsDedupEngineWrite", false),
                m("iopsDedupHashObjRead", "iopsDedupHashObjRead", false),
                m("iopsDedupHashObjWrite", "iopsDedupHashObjWrite", false),
                m("latencyAvgDedupChunkObjRead", "latencyAvgDedupChunkObjReadUs", false),
                m("latencyAvgDedupChunkObjWrite", "latencyAvgDedupChunkObjWriteUs", false),
                m("latencyAvgDedupEngineRead", "latencyAvgDedupEngineReadUs", false),
                m("latencyAvgDedupEngineWrite", "latencyAvgDedupEngineWriteUs", false),
                m("latencyAvgDedupHashObjRead", "latencyAvgDedupHashObjReadUs", false),
                m("latencyAvgDedupHashObjWrite", "latencyAvgDedupHashObjWriteUs", false),
                m("tputDedupChunkObjRead", "tputDedupChunkObjRead", false),
                m("tputDedupChunkObjWrite", "tputDedupChunkObjWrite", false),
                m("tputDedupEngineRead", "tputDedupEngineRead", false),
                m("tputDedupEngineWrite", "tputDedupEngineWrite", false),
                m("tputDedupHashObjRead", "tputDedupHashObjRead", false),
                m("tputDedupHashObjWrite", "tputDedupHashObjWrite", false))),

    CLUSTER_RDT_NETWORK_LATENCY("cluster-rdt-network-latency", Tier.CLUSTER, Target.CLUSTER, "RDT Latency", IdForm.ONE, list(
                m("avgLatency", "avgLatencyUs", false),
                m("kaReset", "kaReset", false),
                m("maxLatency", "maxLatencyUs", false),
                m("minLatency", "minLatencyUs", false),
                m("numReadyDelay", "numReadyDelay", false),
                m("rxSbSpaceMin", "rxSbSpaceMinBytes", false),
                m("txCtxQMax", "txCtxQMaxBytes", false),
                m("txQLatAvg", "txQLatAvgUs", false),
                m("txSbSpaceMin", "txSbSpaceMinBytes", false))),

    CLUSTER_ZDOM_TOP_STATS("cluster-zdom-top-stats", Tier.CLUSTER, Target.CLUSTER, "ZDOM Top Stats", IdForm.ONE, list(
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false))),

    VSAN_DIRECT_CLUSTER("vsan-direct-cluster", Tier.CLUSTER, Target.CLUSTER_OR_HOST, "vSAN Direct Cluster", IdForm.ONE, list(
                m("iopsDevRead", "iopsDevRead", false),
                m("iopsDevWrite", "iopsDevWrite", false),
                m("latencyDevRead", "latencyDevReadUs", false),
                m("latencyDevWrite", "latencyDevWriteUs", false),
                m("oioDevRead", "oioDevRead", false),
                m("oioDevWrite", "oioDevWrite", false),
                m("throughputDevRead", "throughputDevReadBytesPerSec", false),
                m("throughputDevWrite", "throughputDevWriteBytesPerSec", false))),

    VSAN_FILE_SERVICE("vsan-file-service", Tier.CLUSTER, Target.CLUSTER_OR_HOST, "File Service", IdForm.ONE, list(
                m("readLatency", "readLatencyUs", false),
                m("readOpTotal", "readOpTotal", false),
                m("readRequested", "readRequestedBytesPerSec", false),
                m("readTransferred", "readTransferredBytesPerSec", false),
                m("writeLatency", "writeLatencyUs", false),
                m("writeOpTotal", "writeOpTotal", false),
                m("writeRequested", "writeRequestedBytesPerSec", false),
                m("writeTransferred", "writeTransferredBytesPerSec", false))),

    IOINSIGHT("ioinsight", Tier.CLUSTER, Target.CLUSTER_OR_HOST, "IOInsight", IdForm.ONE, list(
                m("aligned4kRatio", "aligned4kRatio", false),
                m("aligned4kReadRatio", "aligned4kReadRatio", false),
                m("aligned4kWriteRatio", "aligned4kWriteRatio", false),
                m("iopsRead", "iopsRead", false),
                m("iopsTotal", "iopsTotal", false),
                m("iopsWrite", "iopsWrite", false),
                m("randomRatio", "randomRatio", false),
                m("randomReadRatio", "randomReadRatio", false),
                m("randomWriteRatio", "randomWriteRatio", false),
                m("readRatio", "readRatio", false),
                m("riosz0_4k", "riosz0_4k", false),
                m("riosz128k", "riosz128k", false),
                m("riosz128k_256k", "riosz128k_256k", false),
                m("riosz16k", "riosz16k", false),
                m("riosz16k_32k", "riosz16k_32k", false),
                m("riosz1_m", "riosz1_m", false),
                m("riosz1m", "riosz1m", false),
                m("riosz256k", "riosz256k", false),
                m("riosz256k_512k", "riosz256k_512k", false),
                m("riosz32k", "riosz32k", false),
                m("riosz32k_64k", "riosz32k_64k", false),
                m("riosz4k", "riosz4k", false),
                m("riosz4k_8k", "riosz4k_8k", false),
                m("riosz512k", "riosz512k", false),
                m("riosz512k_1m", "riosz512k_1m", false),
                m("riosz64k", "riosz64k", false),
                m("riosz64k_128k", "riosz64k_128k", false),
                m("riosz8k", "riosz8k", false),
                m("riosz8k_16k", "riosz8k_16k", false),
                m("rlat0_1us", "rlat0_1us", false),
                m("rlat100_500us", "rlat100_500us", false),
                m("rlat100_ms", "rlat100_ms", false),
                m("rlat10_100us", "rlat10_100us", false),
                m("rlat10_25ms", "rlat10_25ms", false),
                m("rlat1_10us", "rlat1_10us", false),
                m("rlat1_5ms", "rlat1_5ms", false),
                m("rlat25_50ms", "rlat25_50ms", false),
                m("rlat500us_1ms", "rlat500us_1ms", false),
                m("rlat50_100ms", "rlat50_100ms", false),
                m("rlat5_10ms", "rlat5_10ms", false),
                m("sequentialRatio", "sequentialRatio", false),
                m("sequentialReadRatio", "sequentialReadRatio", false),
                m("sequentialWriteRatio", "sequentialWriteRatio", false),
                m("throughputRandom", "throughputRandomBytesPerSec", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputSequential", "throughputSequentialBytesPerSec", false),
                m("throughputTotal", "throughputTotalBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false),
                m("unaligned4kRatio", "unaligned4kRatio", false),
                m("unaligned4kReadRatio", "unaligned4kReadRatio", false),
                m("unaligned4kWriteRatio", "unaligned4kWriteRatio", false),
                m("wiosz0_4k", "wiosz0_4k", false),
                m("wiosz128k", "wiosz128k", false),
                m("wiosz128k_256k", "wiosz128k_256k", false),
                m("wiosz16k", "wiosz16k", false),
                m("wiosz16k_32k", "wiosz16k_32k", false),
                m("wiosz1_m", "wiosz1_m", false),
                m("wiosz1m", "wiosz1m", false),
                m("wiosz256k", "wiosz256k", false),
                m("wiosz256k_512k", "wiosz256k_512k", false),
                m("wiosz32k", "wiosz32k", false),
                m("wiosz32k_64k", "wiosz32k_64k", false),
                m("wiosz4k", "wiosz4k", false),
                m("wiosz4k_8k", "wiosz4k_8k", false),
                m("wiosz512k", "wiosz512k", false),
                m("wiosz512k_1m", "wiosz512k_1m", false),
                m("wiosz64k", "wiosz64k", false),
                m("wiosz64k_128k", "wiosz64k_128k", false),
                m("wiosz8k", "wiosz8k", false),
                m("wiosz8k_16k", "wiosz8k_16k", false),
                m("wlat0_1us", "wlat0_1us", false),
                m("wlat100_500us", "wlat100_500us", false),
                m("wlat100_ms", "wlat100_ms", false),
                m("wlat10_100us", "wlat10_100us", false),
                m("wlat10_25ms", "wlat10_25ms", false),
                m("wlat1_10us", "wlat1_10us", false),
                m("wlat1_5ms", "wlat1_5ms", false),
                m("wlat25_50ms", "wlat25_50ms", false),
                m("wlat500us_1ms", "wlat500us_1ms", false),
                m("wlat50_100ms", "wlat50_100ms", false),
                m("wlat5_10ms", "wlat5_10ms", false),
                m("writeRatio", "writeRatio", false))),

    // ---- Host tier (v2 "Host tier: 13 types, 149 keys") ----

    CMMDS_NET("cmmds-net", Tier.HOST, Target.HOST, "CMMDS Network", IdForm.ONE, list(
                m("groupRx", "groupRx", false),
                m("groupRxThroughput", "groupRxThroughputBytesPerSec", false),
                m("groupTxMcast", "groupTxMcast", false),
                m("groupTxMcastThroughput", "groupTxMcastThroughputBytesPerSec", false),
                m("groupTxUcast", "groupTxUcast", false),
                m("groupTxUcastThroughput", "groupTxUcastThroughputBytesPerSec", false),
                m("rdtRx", "rdtRx", false),
                m("rdtRxThroughput", "rdtRxThroughputBytesPerSec", false),
                m("rdtTx", "rdtTx", false),
                m("rdtTxThroughput", "rdtTxThroughputBytesPerSec", false))),

    VSAN_CPU("vsan-cpu", Tier.HOST, Target.HOST, "CPU", IdForm.ONE, list(
                m("avgRunPct", "avgRunPct", false),
                m("avgUsedPct", "avgUsedPct", false),
                m("maxRunPct", "maxRunPct", false),
                m("maxUsedPct", "maxUsedPct", false),
                m("readyPct", "readyPct", false),
                m("runPct", "runPct", false),
                m("usedPct", "usedPct", false))),

    VSAN_DIRECT_HOST("vsan-direct-host", Tier.HOST, Target.HOST, "vSAN Direct", IdForm.ONE, list(
                m("iopsDevRead", "iopsDevRead", false),
                m("iopsDevWrite", "iopsDevWrite", false),
                m("latencyDevRead", "latencyDevReadUs", false),
                m("latencyDevWrite", "latencyDevWriteUs", false),
                m("oioDevRead", "oioDevRead", false),
                m("oioDevWrite", "oioDevWrite", false),
                m("throughputDevRead", "throughputDevReadBytesPerSec", false),
                m("throughputDevWrite", "throughputDevWriteBytesPerSec", false))),

    HOST_DOMCOMPMGR("host-domcompmgr", Tier.HOST, Target.HOST, "DOM Comp Mgr", IdForm.ONE, list(
                m("componentCongestion", "componentCongestion", false),
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("latencyMaxRead", "latencyMaxReadUs", false),
                m("latencyMaxWrite", "latencyMaxWriteUs", false),
                m("latencyStddevWrite", "latencyStddevWriteUs", false),
                m("namespaceDispatchedCostWrite", "namespaceDispatchedCostWriteBytesPerSec", false),
                m("oio", "oio", false),
                m("resyncReadCongestion", "resyncReadCongestion", false),
                m("sharedCongestion", "sharedCongestion", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false),
                m("vmdiskDispatchedCostWrite", "vmdiskDispatchedCostWriteBytesPerSec", false),
                m("vmdiskQueueDepthWrite", "vmdiskQueueDepthWrite", false))),

    HOST_ESA_DEDUP_DOMCLIENT_IO("host-esa-dedup-domclient-io", Tier.HOST, Target.HOST, "ESA Dedup DOM Client", IdForm.ONE, list(
                m("dedupChunkReadCount", "dedupChunkReadCount", false),
                m("dedupChunkWriteCount", "dedupChunkWriteCount", false),
                m("dedupEngineReadCount", "dedupEngineReadCount", false),
                m("dedupEngineWriteCount", "dedupEngineWriteCount", false),
                m("dedupHashReadCount", "dedupHashReadCount", false),
                m("dedupHashWriteCount", "dedupHashWriteCount", false),
                m("iopsDedupChunkObjRead", "iopsDedupChunkObjRead", false),
                m("iopsDedupChunkObjWrite", "iopsDedupChunkObjWrite", false),
                m("iopsDedupEngineRead", "iopsDedupEngineRead", false),
                m("iopsDedupEngineWrite", "iopsDedupEngineWrite", false),
                m("iopsDedupHashObjRead", "iopsDedupHashObjRead", false),
                m("iopsDedupHashObjWrite", "iopsDedupHashObjWrite", false),
                m("latencyAvgDedupChunkObjRead", "latencyAvgDedupChunkObjReadUs", false),
                m("latencyAvgDedupChunkObjWrite", "latencyAvgDedupChunkObjWriteUs", false),
                m("latencyAvgDedupEngineRead", "latencyAvgDedupEngineReadUs", false),
                m("latencyAvgDedupEngineWrite", "latencyAvgDedupEngineWriteUs", false),
                m("latencyAvgDedupHashObjRead", "latencyAvgDedupHashObjReadUs", false),
                m("latencyAvgDedupHashObjWrite", "latencyAvgDedupHashObjWriteUs", false),
                m("tputDedupChunkObjRead", "tputDedupChunkObjRead", false),
                m("tputDedupChunkObjWrite", "tputDedupChunkObjWrite", false),
                m("tputDedupEngineRead", "tputDedupEngineRead", false),
                m("tputDedupEngineWrite", "tputDedupEngineWrite", false),
                m("tputDedupHashObjRead", "tputDedupHashObjRead", false),
                m("tputDedupHashObjWrite", "tputDedupHashObjWrite", false))),

    VSAN_HOST_NET("vsan-host-net", Tier.HOST, Target.HOST, "Host Network", IdForm.ONE, list(
                m("portRxDrops", "portRxDropsPct", true),
                m("portTxDrops", "portTxDropsPct", true),
                m("rxPackets", "rxPackets", false),
                m("rxPacketsLossRate", "rxPacketsLossRatePct", true),
                m("rxThroughput", "rxThroughputBytesPerSec", false),
                m("txPackets", "txPackets", false),
                m("txPacketsLossRate", "txPacketsLossRatePct", true),
                m("txThroughput", "txThroughputBytesPerSec", false))),

    HOST_ZDOM_TOP_STATS("host-zdom-top-stats", Tier.HOST, Target.HOST, "ZDOM Top Stats", IdForm.ONE, list(
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false),
                m("oioWrite", "oioWrite", false),
                m("readLatencyMaxUs", "readLatencyMaxUs", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false),
                m("unmapLatencyMaxUs", "unmapLatencyMaxUs", false),
                m("writeLatencyMaxUs", "writeLatencyMaxUs", false))),

    VSAN_MEMORY("vsan-memory", Tier.HOST, Target.HOST, "Memory", IdForm.ONE, list(
                m("clomdConsumedSize", "clomdConsumedSizeBytes", false),
                m("clomdReservedSize", "clomdReservedSizeBytes", false),
                m("cmmdsdConsumedSize", "cmmdsdConsumedSizeBytes", false),
                m("cmmdsdReservedSize", "cmmdsdReservedSizeBytes", false),
                m("epdConsumedSize", "epdConsumedSizeBytes", false),
                m("kernelConsumedSize", "kernelConsumedSize", false),
                m("kernelReservedSize", "kernelReservedSizeBytes", false),
                m("osfsdConsumedSize", "osfsdConsumedSizeBytes", false),
                m("uwConsumedSize", "uwConsumedSizeBytes", false),
                m("uwReservedSize", "uwReservedSizeBytes", false),
                m("vsandevicemonitordConsumedSize", "vsandevicemonitordConsumedSizeBytes", false),
                m("vsanmgmtdConsumedSize", "vsanmgmtdConsumedSizeBytes", false))),

    RDT_NETWORK_LATENCY("rdt-network-latency", Tier.HOST, Target.HOST, "RDT Latency", IdForm.ONE, list(
                m("avgLatency", "avgLatencyUs", false),
                m("kaReset", "kaReset", false),
                m("maxLatency", "maxLatencyUs", false),
                m("minLatency", "minLatencyUs", false),
                m("numReadyDelay", "numReadyDelay", false),
                m("rxSbSpaceMax", "rxSbSpaceMaxBytes", false),
                m("rxSbSpaceMin", "rxSbSpaceMinBytes", false),
                m("txCtxQMax", "txCtxQMaxBytes", false),
                m("txQLatAvg", "txQLatAvgUs", false),
                m("txQLatMax", "txQLatMaxUs", false),
                m("txSbSpaceMin", "txSbSpaceMinBytes", false))),

    VSAN_TCPIP_STATS("vsan-tcpip-stats", Tier.HOST, Target.HOST, "TCP/IP", IdForm.TWO, list(
                m("tcpErrs", "tcpErrsPct", true),
                m("tcpHalfopenDropRate", "tcpHalfopenDropRatePct", true),
                m("tcpRcvdupackRate", "tcpRcvdupackRatePct", true),
                m("tcpRcvduppackRate", "tcpRcvduppackRatePct", true),
                m("tcpRcvoopackRate", "tcpRcvoopackRatePct", true),
                m("tcpRxErrRate", "tcpRxErrRate", false),
                m("tcpSackRcvBlocksRate", "tcpSackRcvBlocksRatePct", true),
                m("tcpSackRexmitsRate", "tcpSackRexmitsRatePct", true),
                m("tcpSackSendBlocksRate", "tcpSackSendBlocksRatePct", true),
                m("tcpSndZeroWin", "tcpSndZeroWin", false),
                m("tcpTimeoutDropRate", "tcpTimeoutDropRatePct", true),
                m("tcpTxRexmitRate", "tcpTxRexmitRate", false))),

    VSAN_VNIC_NET("vsan-vnic-net", Tier.HOST, Target.HOST, "vNIC", IdForm.THREE_LAST, list(
                m("portRxDrops", "portRxDropsPct", true),
                m("portRxpkts", "portRxpkts", false),
                m("portTxDrops", "portTxDropsPct", true),
                m("portTxpkts", "portTxpkts", false),
                m("rxPackets", "rxPackets", false),
                m("rxPacketsLossRate", "rxPacketsLossRatePct", true),
                m("rxThroughput", "rxThroughputBytesPerSec", false),
                m("txPackets", "txPackets", false),
                m("txPacketsLossRate", "txPacketsLossRatePct", true),
                m("txThroughput", "txThroughputBytesPerSec", false))),

    VNIC_RDT_NETWORK_LATENCY("vnic-rdt-network-latency", Tier.HOST, Target.HOST, "vNIC RDT Latency", IdForm.TWO, list(
                m("avgLatency", "avgLatencyUs", false),
                m("kaReset", "kaReset", false),
                m("maxLatency", "maxLatencyUs", false),
                m("minLatency", "minLatencyUs", false),
                m("numReadyDelay", "numReadyDelay", false),
                m("rxSbSpaceMin", "rxSbSpaceMinBytes", false),
                m("txCtxQMax", "txCtxQMaxBytes", false),
                m("txQLatAvg", "txQLatAvgUs", false),
                m("txQLatMax", "txQLatMaxUs", false),
                m("txSbSpaceMin", "txSbSpaceMinBytes", false))),

    ZDOM_VTX("zdom-vtx", Tier.HOST, Target.HOST, "ZDOM VTX", IdForm.ONE, list(
                m("checkpointWorkerWakeupMs", "checkpointWorkerWakeupMs", false),
                m("latAvgTotalOpIO", "latAvgTotalOpIOUs", false),
                m("latAvgTxnBank", "latAvgTxnBankUs", false),
                m("maxDiscUsedPct", "maxDiscUsedPct", false),
                m("rateBankFlushTotalCacheRef", "rateBankFlushTotalCacheRef", false),
                m("rateSegCleaningCtxDataTotalCacheRef", "rateSegCleaningCtxDataTotalCacheRef", false),
                m("rateTotalCacheRef", "rateTotalCacheRef", false),
                m("rateTxnLookupCacheRef", "rateTxnLookupCacheRef", false),
                m("rateTxnPrefetchTotalCacheRef", "rateTxnPrefetchTotalCacheRef", false),
                m("rateTxnReadWrite", "rateTxnReadWrite", false))),

    // ---- Disk tier (v2 "Disk tier: 4 types, 31 keys"), on HostSystem by disk ----

    CACHE_DISK("cache-disk", Tier.DISK, Target.DISK, "Cache Disk", IdForm.SELF, list(
                m("iopsDevRead", "iopsDevRead", false),
                m("iopsDevWrite", "iopsDevWrite", false),
                m("latencyDevDAvg", "latencyDevDAvgUs", false),
                m("latencyDevGAvg", "latencyDevGAvgUs", false),
                m("latencyDevRead", "latencyDevReadUs", false),
                m("latencyDevWrite", "latencyDevWriteUs", false),
                m("throughputDevRead", "throughputDevReadBytesPerSec", false),
                m("throughputDevWrite", "throughputDevWriteBytesPerSec", false))),

    CAPACITY_DISK("capacity-disk", Tier.DISK, Target.DISK, "Capacity Disk", IdForm.SELF, list(
                m("deleteCongestion", "deleteCongestion", false),
                m("iopsDevRead", "iopsDevRead", false),
                m("iopsDevWrite", "iopsDevWrite", false),
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyDevDAvg", "latencyDevDAvgUs", false),
                m("latencyDevGAvg", "latencyDevGAvgUs", false),
                m("latencyDevRead", "latencyDevReadUs", false),
                m("latencyDevWrite", "latencyDevWriteUs", false),
                m("latencyRead", "latencyReadUs", false),
                m("latencyWrite", "latencyWriteUs", false),
                m("throughputDevRead", "throughputDevReadBytesPerSec", false),
                m("throughputDevWrite", "throughputDevWriteBytesPerSec", false))),

    VSAN_ESA_DISK_LAYER("vsan-esa-disk-layer", Tier.DISK, Target.DISK, "ESA Disk Layer", IdForm.SELF, list(
                m("avgLatReadCapacity", "avgLatReadCapacity", false),
                m("avgLatWriteCapacity", "avgLatWriteCapacity", false),
                m("avgLatWritePerf", "avgLatWritePerf", false),
                m("iopsReadCapacity", "iopsReadCapacity", false),
                m("iopsWriteCapacity", "iopsWriteCapacity", false))),

    VSAN_ESA_DISK_SCSIFW("vsan-esa-disk-scsifw", Tier.DISK, Target.DISK, "ESA Disk SCSI", IdForm.SELF, list(
                m("latencyDevDAvg", "latencyDevDAvgUs", false),
                m("latencyDevGAvg", "latencyDevGAvgUs", false),
                m("latencyDevKAvg", "latencyDevKAvgUs", false),
                m("latencyDevRead", "latencyDevReadUs", false),
                m("latencyDevWrite", "latencyDevWriteUs", false))),

    // ---- VM tier (v2 "VM tier: 3 types, 21 keys"), on VirtualMachine ----

    VIRTUAL_MACHINE("virtual-machine", Tier.VM, Target.VM, "VM", IdForm.ONE, list(
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyRead", "latencyReadUs", false),
                m("latencyWrite", "latencyWriteUs", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false))),

    VIRTUAL_DISK("virtual-disk", Tier.VM, Target.VM, "Virtual Disk", IdForm.PATH, list(
                m("NIOPS", "NIOPS", false),
                m("NIOPSDelayed", "NIOPSDelayed", false),
                m("NIOPSRead", "NIOPSRead", false),
                m("NIOPSReadDelayed", "NIOPSReadDelayed", false),
                m("NIOPSWrite", "NIOPSWrite", false),
                m("NIOPSWriteDelayed", "NIOPSWriteDelayed", false),
                m("iopsLimit", "iopsLimit", false))),

    VSCSI("vscsi", Tier.VM, Target.VM, "vSCSI", IdForm.TWO, list(
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyRead", "latencyReadUs", false),
                m("latencyWrite", "latencyWriteUs", false),
                m("readCount", "readCount", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false),
                m("writeCount", "writeCount", false)));

    /** Root segment of every pushed key (design Q2). */
    static final String ROOT = "vSAN Perf";

    /**
     * Which tier a type belongs to (v2 "Settings"): core is always on; the
     * host and cluster tiers are instance settings. Declaration order is the
     * order the logs list tiers in.
     */
    enum Tier {
        CORE("core"), HOST("host"), CLUSTER("cluster"), DISK("disk"), VM("vm");

        final String label;

        Tier(String label) {
            this.label = label;
        }
    }

    /** The VMWARE object kind a type's keys land on (v2 "Identity and resolution"). */
    enum Target {
        /** HostSystem, by vSAN node uuid. */
        HOST,
        /** ClusterComputeResource, by vSAN cluster uuid. */
        CLUSTER,
        /**
         * Unobserved id form (empty on the lab): decided per entity at run
         * time, the cluster when the id is the cluster's vSAN uuid, the host
         * when it is one node uuid of that cluster, otherwise skipped and
         * counted (v2 "Runtime landing").
         */
        CLUSTER_OR_HOST,
        /** HostSystem, by vSAN disk uuid through the per-host QueryDisksForVsan map. */
        DISK,
        /** VirtualMachine, by {@code config.instanceUuid} (or backing path) through the VM read. */
        VM
    }

    /** The shape of the {@code entityRefId} after the first {@code :} (api map grammar table). */
    enum IdForm {
        /** {@code <uuid>}: a node uuid, the cluster uuid or a VM instanceUuid; no instance. */
        ONE(1, -1, false),
        /** {@code <uuid>|<instance>}: pNIC, TCP/IP stack, vmknic (RDT), vSCSI device. */
        TWO(2, 1, false),
        /** {@code <nodeUuid>|<stack>|<vmk>}: the instance is the vmk; the stack is dropped. */
        THREE_LAST(3, 2, false),
        /** {@code <disk uuid>}: the uuid is also the instance (disk tier). */
        SELF(1, 0, false),
        /**
         * {@code <datastore path>} ({@code virtual-disk}): the rest of the id
         * whole, never split; the instance (bus position) comes from the
         * VM device read.
         */
        PATH(1, -1, true);

        /** Exact number of {@code |} parts ({@link #PATH}: not split). */
        final int parts;
        /** Index of the instance part, {@code -1} for none or resolved later. */
        final int instancePart;
        /** The instance is resolved after parsing (bus position of a vdisk). */
        final boolean resolvedInstance;

        IdForm(int parts, int instancePart, boolean resolvedInstance) {
            this.parts = parts;
            this.instancePart = instancePart;
            this.resolvedInstance = resolvedInstance;
        }
    }

    /** One catalog row: the vSAN label, the Ops key leaf, the transform. */
    static final class Metric {
        final String label;
        final String leaf;
        /** The service reports this label per mille; pushed divided by 10. */
        final boolean perMille;
        /** Position in the type's catalog (the dedup mark slot); {@code -1} outside a catalog. */
        final int position;

        Metric(String label, String leaf, boolean perMille) {
            this(label, leaf, perMille, -1);
        }

        private Metric(String label, String leaf, boolean perMille, int position) {
            this.label = label;
            this.leaf = leaf;
            this.perMille = perMille;
            this.position = position;
        }

        /** The pushed value for a raw service value. */
        double convert(double raw) {
            return perMille ? raw / 10.0 : raw;
        }
    }

    /** The {@code entityRefId} type prefix, e.g. {@code host-domowner}. */
    final String id;
    final Tier tier;
    final Target target;
    /** The key group under {@link #ROOT}, from the Python pack's KIND_SUFFIX (v2 table). */
    final String group;
    final IdForm form;
    /** Instanced: the key carries {@code :<instance>} after the group. */
    final boolean instanced;
    private final Map<String, Metric> byLabel;

    EntityType(String id, Tier tier, Target target, String group, IdForm form,
            List<Metric> metrics) {
        this.id = id;
        this.tier = tier;
        this.target = target;
        this.group = group;
        this.form = form;
        this.instanced = form.instancePart >= 0 || form.resolvedInstance;
        Map<String, Metric> m = new LinkedHashMap<>();
        int pos = 0;
        for (Metric x : metrics) {
            if (m.put(x.label, new Metric(x.label, x.leaf, x.perMille, pos++)) != null) {
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

    /** Number of catalog rows (the dedup mark array length). */
    int size() {
        return byLabel.size();
    }

    /** The {@code labels} array sent in the spec: exactly the catalog labels. */
    List<String> labels() {
        return new ArrayList<>(byLabel.keySet());
    }

    /**
     * Core types are never learned from an empty echo (v2 Q7): they are
     * expected on every vSAN cluster, and a transient empty must not blind
     * DOM owner, DOM client or pNIC for a day.
     */
    boolean learnsEmpty() {
        return tier != Tier.CORE;
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

    /**
     * Wire cap for this type's 200 bodies (v2 Sizing requirement 3, streamed:
     * only the wire size is capped): 16 MB for the VM types, 4 MB otherwise.
     */
    long wireCap() {
        return tier == Tier.VM ? 16L * 1024 * 1024 : 4L * 1024 * 1024;
    }

    /** The type with this {@code entityRefId} prefix, or {@code null}. */
    static EntityType byId(String id) {
        for (EntityType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /** The types of these tiers, in collection order. */
    static List<EntityType> ofTiers(java.util.Set<Tier> tiers) {
        List<EntityType> out = new ArrayList<>();
        for (EntityType t : values()) {
            if (tiers.contains(t.tier)) out.add(t);
        }
        return out;
    }
}
