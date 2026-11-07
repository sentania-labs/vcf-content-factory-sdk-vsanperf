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
 * VirtualMachine). 33 types, 451 keys: the {@code dashboard} scope.
 * Build 3 (design {@code knowledge/designs/vsanperf-mp-v3.md}, build 19):
 * under {@code metric_scope=all} 36 more types join the same tiers
 * ({@link F#ALL_ONLY}) and the shared types gain rows ({@code a(...)}):
 * the v3 Appendix A delta (1,144 keys, leaves by the v3 leaf rule) plus the
 * 210 ESA dedup labels the P3 capture returned beyond the Python model
 * (addendum Q3): 69 types, 1,805 keys. Dashboard rows come first in every
 * type, so their positions (dedup mark slots) are the same under either
 * scope. Plain JDK, no SDK.
 *
 * <p>Declaration order is collection order within a cluster (v2 "Steps"):
 * core (DOM owner first, the most valuable group and the slowest call, then
 * DOM client, then pNIC), then the cluster, host, disk and VM tiers, so a
 * deadline cut loses the tail first (the VM types, which also run on a
 * cadence). Build 3 (v3 C8): within each tier the dashboard types first,
 * the new ones after, {@code host-cpu} and {@code dom-world-cpu} last.
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
                m("writeLeafOwnerLatencyRemote", "writeLeafOwnerLatencyRemoteUs", false),
                a("avgDeltaCreationDomActiveLatency", "avgDeltaCreationDomActiveLatencySec", false),
                a("avgDeltaCreationDomCreationLatency", "avgDeltaCreationDomCreationLatencySec", false),
                a("avgResyncParallelism", "avgResyncParallelism", false),
                a("congestion", "congestion", false),
                a("deltaLeafGTEs", "deltaLeafGTEs", false),
                a("deltaSourceChanges", "deltaSourceChanges", false),
                a("ecCachedWriteCount", "ecCachedWriteCount", false),
                a("ecCachedWriteHits", "ecCachedWriteHits", false),
                a("ecFswCount", "ecFswCount", false),
                a("ecStridedWriteCount", "ecStridedWriteCount", false),
                a("ecStridedWriteHits", "ecStridedWriteHits", false),
                a("ecWriteCount", "ecWriteCount", false),
                a("highOIOAvgDurationMs", "highOIOAvgDurationMs", false),
                a("highOIOAvgDurationMsRead", "highOIOAvgDurationMsRead", false),
                a("highOIOAvgDurationMsWrite", "highOIOAvgDurationMsWrite", false),
                a("highOIOAvgIOCount", "highOIOAvgIOCount", false),
                a("highOIOAvgIOCountRead", "highOIOAvgIOCountRead", false),
                a("highOIOAvgIOCountWrite", "highOIOAvgIOCountWrite", false),
                a("highOIOAvgOIO", "highOIOAvgOIO", false),
                a("highOIOAvgOIORead", "highOIOAvgOIORead", false),
                a("highOIOAvgOIOWrite", "highOIOAvgOIOWrite", false),
                a("highOIOBytes", "highOIOBytes", false),
                a("highOIOBytesRead", "highOIOBytesRead", false),
                a("highOIOBytesWrite", "highOIOBytesWrite", false),
                a("highOIOCount", "highOIOCount", false),
                a("highOIOCountPercent", "highOIOCountPercent", false),
                a("highOIOCountPercentRead", "highOIOCountPercentRead", false),
                a("highOIOCountPercentWrite", "highOIOCountPercentWrite", false),
                a("highOIOCountRead", "highOIOCountRead", false),
                a("highOIOCountWrite", "highOIOCountWrite", false),
                a("highOIODurationMs", "highOIODurationMs", false),
                a("highOIODurationMsRead", "highOIODurationMsRead", false),
                a("highOIODurationMsWrite", "highOIODurationMsWrite", false),
                a("highOIODurationPercent", "highOIODurationPercent", false),
                a("highOIODurationPercentRead", "highOIODurationPercentRead", false),
                a("highOIONumTimes", "highOIONumTimes", false),
                a("highOIONumTimesRead", "highOIONumTimesRead", false),
                a("highOIONumTimesWrite", "highOIONumTimesWrite", false),
                a("highOIOThroughput", "highOIOThroughputBytesPerSec", false),
                a("highOIOThroughputRead", "highOIOThroughputReadBytesPerSec", false),
                a("highOIOThroughputWrite", "highOIOThroughputWriteBytesPerSec", false),
                a("ioCount", "ioCount", false),
                a("iopsRead", "iopsRead", false),
                a("iopsRecUnmap", "iopsRecUnmap", false),
                a("iopsRecWrite", "iopsRecWrite", false),
                a("iopsResyncRead", "iopsResyncRead", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyAvg", "latencyAvgUs", false),
                a("latencyAvgRecUnmap", "latencyAvgRecUnmapUs", false),
                a("latencyAvgRecWrite", "latencyAvgRecWriteUs", false),
                a("latencyAvgResyncRead", "latencyAvgResyncReadUs", false),
                a("latencyAvgServiceVMRead", "latencyAvgServiceVMReadUs", false),
                a("latencyAvgServiceVMWrite", "latencyAvgServiceVMWriteUs", false),
                a("latencyStddev", "latencyStddevUs", false),
                a("latencyStddevRead", "latencyStddevReadUs", false),
                a("latencyStddevRecoveryWrite", "latencyStddevRecoveryWriteUs", false),
                a("latencyStddevResyncRead", "latencyStddevResyncReadUs", false),
                a("latencyStddevUnmap", "latencyStddevUnmapUs", false),
                a("leafGTEs", "leafGTEs", false),
                a("netSchedAvgCompRecoveryWriteDelayMs", "netSchedAvgCompRecoveryWriteDelayMs", false),
                a("netSchedAvgCompResyncReadDelayMs", "netSchedAvgCompResyncReadDelayMs", false),
                a("netSchedAvgOwnerRecoveryWriteDelayMs", "netSchedAvgOwnerRecoveryWriteDelayMs", false),
                a("netSchedAvgOwnerResyncReadDelayMs", "netSchedAvgOwnerResyncReadDelayMs", false),
                a("netSchedBWDiscoveredPerDiscoverySession", "netSchedBWDiscoveredPerDiscoverySessionBytesPerSec", false),
                a("netSchedBWDiscoveryActions", "netSchedBWDiscoveryActions", false),
                a("netSchedBWDiscoveryAvgIntervalsUs", "netSchedBWDiscoveryAvgIntervalsUs", false),
                a("netSchedBWDiscoverySessions", "netSchedBWDiscoverySessions", false),
                a("netSchedBWDiscoveryStopsDueToGainsFromGuestPct", "netSchedBWDiscoveryStopsDueToGainsFromGuestPct", false),
                a("netSchedBWDiscoveryStopsDueToHighLatencyPct", "netSchedBWDiscoveryStopsDueToHighLatencyPct", false),
                a("netSchedBWDiscoveryStopsDueToLowUtilizationPct", "netSchedBWDiscoveryStopsDueToLowUtilizationPct", false),
                a("netSchedBWDiscoveryUtilizationPct", "netSchedBWDiscoveryUtilizationPct", false),
                a("netSchedBWReallocatedPerReallocationSession", "netSchedBWReallocatedPerReallocationSessionBytesPerSec", false),
                a("netSchedBWReallocationActions", "netSchedBWReallocationActions", false),
                a("netSchedBWReallocationAvgIntervalsUs", "netSchedBWReallocationAvgIntervalsUs", false),
                a("netSchedBWReallocationSessions", "netSchedBWReallocationSessions", false),
                a("netSchedBWReallocationStopsDueToHighSharePct", "netSchedBWReallocationStopsDueToHighSharePct", false),
                a("netSchedBWReallocationStopsDueToLowUtilizationPct", "netSchedBWReallocationStopsDueToLowUtilizationPct", false),
                a("netSchedBWReallocationUtilizationRatio", "netSchedBWReallocationUtilizationRatioPct", false),
                a("netSchedBWReallocationUtilizationRatioAbove", "netSchedBWReallocationUtilizationRatioAbove", false),
                a("netSchedBWReallocationUtilizationRatioBelow", "netSchedBWReallocationUtilizationRatioBelow", false),
                a("netSchedBandwidthDiscoveryBps", "netSchedBandwidthDiscoveryBps", false),
                a("netSchedGuestIopsDecreasePerDiscoverySession", "netSchedGuestIopsDecreasePerDiscoverySession", false),
                a("netSchedGuestIopsIncreasePerReallocationSession", "netSchedGuestIopsIncreasePerReallocationSession", false),
                a("netSchedLatencyIncreaseFromResyncPerDiscoverySessionPct", "netSchedLatencyIncreaseFromResyncPerDiscoverySessionPct", false),
                a("netSchedResyncGainsFromGuestCount", "netSchedResyncGainsFromGuestCount", false),
                a("netSchedResyncIopsDecreasePerReallocationSession", "netSchedResyncIopsDecreasePerReallocationSession", false),
                a("netSchedResyncIopsIncreasePerDiscoverySession", "netSchedResyncIopsIncreasePerDiscoverySession", false),
                a("netSchedUnderUtilCount", "netSchedUnderUtilCount", false),
                a("netSchedUnderUtilGuestBps", "netSchedUnderUtilGuestBps", false),
                a("netSchedUnderUtilTtlBps", "netSchedUnderUtilTtlBps", false),
                a("numBlockedDeltaToBaseResyncJobs", "numBlockedDeltaToBaseResyncJobs", false),
                a("numBlockedFullResyncJobs", "numBlockedFullResyncJobs", false),
                a("numBlockedPriorityResyncJobs", "numBlockedPriorityResyncJobs", false),
                a("numBlockedResyncJobs", "numBlockedResyncJobs", false),
                a("numBlockedSharedResyncJobs", "numBlockedSharedResyncJobs", false),
                a("numBlockedSiblingToDeltaResyncJobs", "numBlockedSiblingToDeltaResyncJobs", false),
                a("numCompleteDeltaToBaseResyncOps", "numCompleteDeltaToBaseResyncOps", false),
                a("numCompleteFullResyncOps", "numCompleteFullResyncOps", false),
                a("numCompleteResyncOps", "numCompleteResyncOps", false),
                a("numCompleteResyncRwrBatches", "numCompleteResyncRwrBatches", false),
                a("numCompleteRwrOps", "numCompleteRwrOps", false),
                a("numCompleteSiblingToDeltaResyncOps", "numCompleteSiblingToDeltaResyncOps", false),
                a("numInflightDedupResyncJobs", "numInflightDedupResyncJobs", false),
                a("numInflightPriorityResyncJobs", "numInflightPriorityResyncJobs", false),
                a("numInflightSharedResyncJobs", "numInflightSharedResyncJobs", false),
                a("numNetSchedCompIops", "numNetSchedCompIops", false),
                a("numNetSchedCompIopsLimit", "numNetSchedCompIopsLimit", false),
                a("numNetSchedCompRecoveryWriteIops", "numNetSchedCompRecoveryWriteIops", false),
                a("numNetSchedCompRecoveryWriteIopsLimit", "numNetSchedCompRecoveryWriteIopsLimit", false),
                a("numNetSchedCompRecoveryWriteTpThrottled", "numNetSchedCompRecoveryWriteTpThrottledBytesPerSec", false),
                a("numNetSchedCompResyncReadIops", "numNetSchedCompResyncReadIops", false),
                a("numNetSchedCompResyncReadIopsLimit", "numNetSchedCompResyncReadIopsLimit", false),
                a("numNetSchedCompResyncReadTpThrottled", "numNetSchedCompResyncReadTpThrottledBytesPerSec", false),
                a("numNetSchedCompTpThrottled", "numNetSchedCompTpThrottledBytesPerSec", false),
                a("numNetSchedControllerActivations", "numNetSchedControllerActivations", false),
                a("numNetSchedHighBand", "numNetSchedHighBand", false),
                a("numNetSchedHighBandHighShare", "numNetSchedHighBandHighShare", false),
                a("numNetSchedHighBandLowLimit", "numNetSchedHighBandLowLimit", false),
                a("numNetSchedHighBandSec", "numNetSchedHighBandSec", false),
                a("numNetSchedHighToLowBand", "numNetSchedHighToLowBand", false),
                a("numNetSchedHighToMidBand", "numNetSchedHighToMidBand", false),
                a("numNetSchedLowBandHighLimit", "numNetSchedLowBandHighLimit", false),
                a("numNetSchedLowToHighBand", "numNetSchedLowToHighBand", false),
                a("numNetSchedMidBandHighLimit", "numNetSchedMidBandHighLimit", false),
                a("numNetSchedMidBandLowShare", "numNetSchedMidBandLowShare", false),
                a("numNetSchedMidToHighBand", "numNetSchedMidToHighBand", false),
                a("numNetSchedOwnerIops", "numNetSchedOwnerIops", false),
                a("numNetSchedOwnerIopsLimit", "numNetSchedOwnerIopsLimit", false),
                a("numNetSchedOwnerRecoveryWriteIops", "numNetSchedOwnerRecoveryWriteIops", false),
                a("numNetSchedOwnerRecoveryWriteIopsLimit", "numNetSchedOwnerRecoveryWriteIopsLimit", false),
                a("numNetSchedOwnerRecoveryWriteTpThrottled", "numNetSchedOwnerRecoveryWriteTpThrottledBytesPerSec", false),
                a("numNetSchedOwnerResyncReadIops", "numNetSchedOwnerResyncReadIops", false),
                a("numNetSchedOwnerResyncReadIopsLimit", "numNetSchedOwnerResyncReadIopsLimit", false),
                a("numNetSchedOwnerResyncReadTpThrottled", "numNetSchedOwnerResyncReadTpThrottledBytesPerSec", false),
                a("numNetSchedOwnerTpThrottled", "numNetSchedOwnerTpThrottledBytesPerSec", false),
                a("numNetSchedRecoveryWriteTpThrottled", "numNetSchedRecoveryWriteTpThrottled", false),
                a("numNetSchedResyncReadTpThrottled", "numNetSchedResyncReadTpThrottled", false),
                a("numNetSchedResyncTotalSharePercent", "numNetSchedResyncTotalSharePercent", false),
                a("numNetSchedSeenResyncTpBps", "numNetSchedSeenResyncTpBps", false),
                a("numOio", "numOio", false),
                a("numPendingDecomResyncJobs", "numPendingDecomResyncJobs", false),
                a("numPendingDedupResyncJobs", "numPendingDedupResyncJobs", false),
                a("numPendingDeltaToBaseResyncJobs", "numPendingDeltaToBaseResyncJobs", false),
                a("numPendingFullResyncJobs", "numPendingFullResyncJobs", false),
                a("numPendingResyncJobs", "numPendingResyncJobs", false),
                a("numPendingSiblingToDeltaResyncJobs", "numPendingSiblingToDeltaResyncJobs", false),
                a("numRunningDeltaToBaseResyncJobs", "numRunningDeltaToBaseResyncJobs", false),
                a("numRunningFullResyncJobs", "numRunningFullResyncJobs", false),
                a("numRunningResyncJobs", "numRunningResyncJobs", false),
                a("numRunningSiblingToDeltaResyncJobs", "numRunningSiblingToDeltaResyncJobs", false),
                a("numSuspendedDedupResyncJobs", "numSuspendedDedupResyncJobs", false),
                a("numSuspendedDeltaToBaseResyncJobs", "numSuspendedDeltaToBaseResyncJobs", false),
                a("numSuspendedFullResyncJobs", "numSuspendedFullResyncJobs", false),
                a("numSuspendedPriorityResyncJobs", "numSuspendedPriorityResyncJobs", false),
                a("numSuspendedResyncChangemaps", "numSuspendedResyncChangemaps", false),
                a("numSuspendedResyncJobs", "numSuspendedResyncJobs", false),
                a("numSuspendedSharedResyncJobs", "numSuspendedSharedResyncJobs", false),
                a("numSuspendedSiblingToDeltaResyncJobs", "numSuspendedSiblingToDeltaResyncJobs", false),
                a("oioRead", "oioRead", false),
                a("oioRecoveryWrite", "oioRecoveryWrite", false),
                a("oioResyncRead", "oioResyncRead", false),
                a("oioWrite", "oioWrite", false),
                a("ownerECCacheHitMissRatio", "ownerECCacheHitMissRatioPct", false),
                a("ownerECCacheNumCachedObjects", "ownerECCacheNumCachedObjects", false),
                a("ownerECCacheNumFailedAllocs", "ownerECCacheNumFailedAllocs", false),
                a("ownerECCacheNumSuccessAllocs", "ownerECCacheNumSuccessAllocs", false),
                a("rateOtherOwnerOpsFailure", "rateOtherOwnerOpsFailure", false),
                a("rateOwnerOpsBadParam", "rateOwnerOpsBadParam", false),
                a("rateOwnerOpsChecksumMismatch", "rateOwnerOpsChecksumMismatch", false),
                a("rateOwnerOpsIoError", "rateOwnerOpsIoError", false),
                a("rateOwnerOpsLimitExceed", "rateOwnerOpsLimitExceed", false),
                a("rateOwnerOpsNoConnect", "rateOwnerOpsNoConnect", false),
                a("rateOwnerOpsNoMem", "rateOwnerOpsNoMem", false),
                a("rateOwnerOpsNoSpace", "rateOwnerOpsNoSpace", false),
                a("rateOwnerOpsNotSupported", "rateOwnerOpsNotSupported", false),
                a("rateOwnerOpsRetry", "rateOwnerOpsRetry", false),
                a("readCongestion", "readCongestion", false),
                a("readCount", "readCount", false),
                a("readECLeafOwnerMaxLatencyAvgUs", "readECLeafOwnerMaxLatencyAvgUs", false),
                a("readECLeafOwnerMaxLatencyUs", "readECLeafOwnerMaxLatencyUs", false),
                a("readLeafOwnerIops", "readLeafOwnerIops", false),
                a("readLeafOwnerLatency", "readLeafOwnerLatencyUs", false),
                a("readLeafOwnerMaxLatencyAvgUs", "readLeafOwnerMaxLatencyAvgUs", false),
                a("readLeafOwnerRetriesPerOp", "readLeafOwnerRetriesPerOp", false),
                a("readLeafOwnerThroughput", "readLeafOwnerThroughputBytesPerSec", false),
                a("readOwnerRetriesPerOp", "readOwnerRetriesPerOp", false),
                a("recUnmapCount", "recUnmapCount", false),
                a("recWriteCount", "recWriteCount", false),
                a("recoveryUnmapCongestion", "recoveryUnmapCongestion", false),
                a("recoveryUnmapLeafOwnerLatency", "recoveryUnmapLeafOwnerLatencyUs", false),
                a("recoveryWriteLeafOwnerLatency", "recoveryWriteLeafOwnerLatencyUs", false),
                a("recoveryWriteLeafOwnerLatencyLocal", "recoveryWriteLeafOwnerLatencyLocalUs", false),
                a("recoveryWriteLeafOwnerLatencyRemote", "recoveryWriteLeafOwnerLatencyRemoteUs", false),
                a("resyncReadCount", "resyncReadCount", false),
                a("resyncReadLeafOwnerLatency", "resyncReadLeafOwnerLatencyUs", false),
                a("resyncReadLeafOwnerLatencyLocal", "resyncReadLeafOwnerLatencyLocalUs", false),
                a("resyncReadLeafOwnerLatencyRemote", "resyncReadLeafOwnerLatencyRemoteUs", false),
                a("resyncsEndedAsDeltaToBase", "resyncsEndedAsDeltaToBase", false),
                a("resyncsStartedAsDeltaToBase", "resyncsStartedAsDeltaToBase", false),
                a("segCleanerUnmapLeafOwnerLatency", "segCleanerUnmapLeafOwnerLatencyUs", false),
                a("serverSwitchAvglatency", "serverSwitchAvglatencyUs", false),
                a("serverSwitchLatencyMaxUs", "serverSwitchLatencyMaxUs", false),
                a("serverSwitchlatencyStddev", "serverSwitchlatencyStddevUs", false),
                a("throughput", "throughputBytesPerSec", false),
                a("totalDeltaCreationDomActiveLatency", "totalDeltaCreationDomActiveLatencyUs", false),
                a("totalDeltaCreationDomCreationLatency", "totalDeltaCreationDomCreationLatencyUs", false),
                a("totalDeltasCreatedCount", "totalDeltasCreatedCount", false),
                a("tputRead", "tputReadBytesPerSec", false),
                a("tputRecUnmap", "tputRecUnmapBytesPerSec", false),
                a("tputRecWrite", "tputRecWriteBytesPerSec", false),
                a("tputResyncRead", "tputResyncReadBytesPerSec", false),
                a("tputServiceVMRead", "tputServiceVMReadBytesPerSec", false),
                a("tputServiceVMWrite", "tputServiceVMWriteBytesPerSec", false),
                a("tputUnmap", "tputUnmapBytesPerSec", false),
                a("tputWrite", "tputWriteBytesPerSec", false),
                a("twoPCBatchCommitBlockedByGuestIOPS", "twoPCBatchCommitBlockedByGuestIOPS", false),
                a("twoPCBatchCommitBlockedByOtherIOPS", "twoPCBatchCommitBlockedByOtherIOPS", false),
                a("twoPCBatchCommitBlockedByResyncIOPS", "twoPCBatchCommitBlockedByResyncIOPS", false),
                a("twoPCBatchCommitBlockedBySyncIOPS", "twoPCBatchCommitBlockedBySyncIOPS", false),
                a("twoPCBatchCommitGuestIOPS", "twoPCBatchCommitGuestIOPS", false),
                a("twoPCBatchCommitOtherIOPS", "twoPCBatchCommitOtherIOPS", false),
                a("twoPCBatchCommitResyncIOPS", "twoPCBatchCommitResyncIOPS", false),
                a("twoPCBatchCommitSyncIOPS", "twoPCBatchCommitSyncIOPS", false),
                a("twoPCCommitCount", "twoPCCommitCount", false),
                a("twoPCCommitSize", "twoPCCommitSize", false),
                a("twoPCPrepareLimitRetryGuestAvgTimeMs", "twoPCPrepareLimitRetryGuestAvgTimeMs", false),
                a("twoPCPrepareLimitRetryGuestIOPS", "twoPCPrepareLimitRetryGuestIOPS", false),
                a("twoPCPrepareLimitRetryOtherAvgTimeMs", "twoPCPrepareLimitRetryOtherAvgTimeMs", false),
                a("twoPCPrepareLimitRetryOtherIOPS", "twoPCPrepareLimitRetryOtherIOPS", false),
                a("twoPCPrepareLimitRetryResyncAvgTimeMs", "twoPCPrepareLimitRetryResyncAvgTimeMs", false),
                a("twoPCPrepareLimitRetryResyncIOPS", "twoPCPrepareLimitRetryResyncIOPS", false),
                a("twoPCPrepareRetryCount", "twoPCPrepareRetryCount", false),
                a("twoPCReservedLSNExhaustedCount", "twoPCReservedLSNExhaustedCount", false),
                a("twoPCReservedLSNUnusedCount", "twoPCReservedLSNUnusedCount", false),
                a("twoPCReservedLSNUsedCount", "twoPCReservedLSNUsedCount", false),
                a("unmapCongestion", "unmapCongestion", false),
                a("unmapECLeafOwnerMaxLatencyAvgUs", "unmapECLeafOwnerMaxLatencyAvgUs", false),
                a("unmapECLeafOwnerMaxLatencyUs", "unmapECLeafOwnerMaxLatencyUs", false),
                a("unmapLeafOwnerLatency", "unmapLeafOwnerLatencyUs", false),
                a("unmapLeafOwnerMaxLatencyAvgUs", "unmapLeafOwnerMaxLatencyAvgUs", false),
                a("writeCongestion", "writeCongestion", false),
                a("writeCount", "writeCount", false),
                a("writeECLeafOwnerMaxLatencyAvgUs", "writeECLeafOwnerMaxLatencyAvgUs", false),
                a("writeECLeafOwnerMaxLatencyUs", "writeECLeafOwnerMaxLatencyUs", false),
                a("writeLeafOwnerIops", "writeLeafOwnerIops", false),
                a("writeLeafOwnerLatency", "writeLeafOwnerLatencyUs", false),
                a("writeLeafOwnerMaxLatencyAvgUs", "writeLeafOwnerMaxLatencyAvgUs", false),
                a("writeLeafOwnerRetriesPerOp", "writeLeafOwnerRetriesPerOp", false),
                a("writeLeafOwnerThroughput", "writeLeafOwnerThroughputBytesPerSec", false),
                a("writeOwnerRetriesPerOp", "writeOwnerRetriesPerOp", false))),

    HOST_DOMCLIENT("host-domclient", Tier.CORE, Target.HOST, "DOM Client", IdForm.ONE, list(
                m("congestion", "congestion", false),
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false),
                a("IOPSWithinBursts", "IOPSWithinBursts", false),
                a("IOPSWithinBurstsExclFirstObj", "IOPSWithinBurstsExclFirstObj", false),
                a("IOPSWithinBurstsMultiObj", "IOPSWithinBurstsMultiObj", false),
                a("avgBurstArrivalTime", "avgBurstArrivalTimeUs", false),
                a("avgBurstArrivalTimeMultiObj", "avgBurstArrivalTimeMultiObj", false),
                a("avgBurstCompletionTime", "avgBurstCompletionTimeUs", false),
                a("avgBurstCompletionTimeMultiObj", "avgBurstCompletionTimeMultiObj", false),
                a("avgBurstPeakOIO", "avgBurstPeakOIO", false),
                a("avgBurstPeakOIOMultiObj", "avgBurstPeakOIOMultiObj", false),
                a("avgBurstPeakOutstandingKb", "avgBurstPeakOutstandingKb", false),
                a("avgBurstPeakOutstandingKbMultiObj", "avgBurstPeakOutstandingKbMultiObj", false),
                a("avgBurstTotalTime", "avgBurstTotalTimeUs", false),
                a("avgBurstTotalTimeMultiObj", "avgBurstTotalTimeMultiObj", false),
                a("avgPctTimeToPeak", "avgPctTimeToPeak", false),
                a("avgPctTimeToPeakMultiObj", "avgPctTimeToPeakMultiObj", false),
                a("bandwidthWithinBursts", "bandwidthWithinBursts", false),
                a("bandwidthWithinBurstsExclFirstObj", "bandwidthWithinBurstsExclFirstObj", false),
                a("bandwidthWithinBurstsMultiObj", "bandwidthWithinBurstsMultiObj", false),
                a("burstsPerSecond", "burstsPerSecond", false),
                a("burstsPerSecondMultiObj", "burstsPerSecondMultiObj", false),
                a("clientCacheHitRate", "clientCacheHitRate", false),
                a("clientCacheHits", "clientCacheHits", false),
                a("ioCount", "ioCount", false),
                a("iops", "iops", false),
                a("iopsSegCleanerUnmap", "iopsSegCleanerUnmap", false),
                a("iopsUnmap", "iopsUnmap", false),
                a("latencyAvg", "latencyAvgUs", false),
                a("latencyAvgSegCleanerUnmap", "latencyAvgSegCleanerUnmapUs", false),
                a("latencyAvgUnmap", "latencyAvgUnmapUs", false),
                a("latencyMaxRead", "latencyMaxReadUs", false),
                a("latencyMaxSegCleanerUnmap", "latencyMaxSegCleanerUnmapUs", false),
                a("latencyMaxUnmap", "latencyMaxUnmapUs", false),
                a("latencyMaxWrite", "latencyMaxWriteUs", false),
                a("latencyStddev", "latencyStddevUs", false),
                a("latencyStddevRead", "latencyStddevReadUs", false),
                a("latencyStddevSegCleanerUnmap", "latencyStddevSegCleanerUnmapUs", false),
                a("latencyStddevUnmap", "latencyStddevUnmapUs", false),
                a("latencyStddevWrite", "latencyStddevWriteUs", false),
                a("lessSkewedMultiBurstsPerSecond", "lessSkewedMultiBurstsPerSecond", false),
                a("numBurstStatsSkipped", "numBurstStatsSkipped", false),
                a("numOio", "numOio", false),
                a("oioRead", "oioRead", false),
                a("oioRecoveryWrite", "oioRecoveryWrite", false),
                a("oioResyncRead", "oioResyncRead", false),
                a("oioWrite", "oioWrite", false),
                a("rateClientOpsBadParam", "rateClientOpsBadParam", false),
                a("rateClientOpsChecksumMismatch", "rateClientOpsChecksumMismatch", false),
                a("rateClientOpsIoError", "rateClientOpsIoError", false),
                a("rateClientOpsLimitExceed", "rateClientOpsLimitExceed", false),
                a("rateClientOpsNoConnect", "rateClientOpsNoConnect", false),
                a("rateClientOpsNoMem", "rateClientOpsNoMem", false),
                a("rateClientOpsNoSpace", "rateClientOpsNoSpace", false),
                a("rateClientOpsNotSupported", "rateClientOpsNotSupported", false),
                a("rateClientOpsRetry", "rateClientOpsRetry", false),
                a("rateOtherClientOpsFailure", "rateOtherClientOpsFailure", false),
                a("readCongestion", "readCongestion", false),
                a("readCount", "readCount", false),
                a("segCleanerUnmapCongestion", "segCleanerUnmapCongestion", false),
                a("segCleanerUnmapLeafOwnerLatency", "segCleanerUnmapLeafOwnerLatencyUs", false),
                a("segCleanerUnmapLeafOwnerMaxLatencyAvgUs", "segCleanerUnmapLeafOwnerMaxLatencyAvgUs", false),
                a("serverSwitchAvglatency", "serverSwitchAvglatencyUs", false),
                a("serverSwitchCount", "serverSwitchCount", false),
                a("serverSwitchLatencyMaxUs", "serverSwitchLatencyMaxUs", false),
                a("throughput", "throughputBytesPerSec", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputSegCleanerUnmap", "throughputSegCleanerUnmapBytesPerSec", false),
                a("throughputUnmap", "throughputUnmapBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false),
                a("unmapCongestion", "unmapCongestion", false),
                a("unmapCount", "unmapCount", false),
                a("writeCongestion", "writeCongestion", false),
                a("writeCount", "writeCount", false))),

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
                m("txErr", "txErrPct", true),
                a("pauseCountRaw", "pauseCountRaw", false),
                a("pfcCount", "pfcCount", false),
                a("portRxpkts", "portRxpkts", false),
                a("rxDrp", "rxDrp", false),
                a("rxErrRaw", "rxErrRaw", false),
                a("rxFifoErrRaw", "rxFifoErrRaw", false),
                a("rxFrmErr", "rxFrmErr", false),
                a("rxLgtErr", "rxLgtErr", false),
                a("rxOvErrRaw", "rxOvErrRaw", false),
                a("rxPackets", "rxPackets", false),
                a("rxPacketsLossRate", "rxPacketsLossRatePct", true),
                a("rxPktRaw", "rxPktRaw", false),
                a("rxThroughput", "rxThroughputBytesPerSec", false),
                a("txAbortErr", "txAbortErr", false),
                a("txCarErrRaw", "txCarErrRaw", false),
                a("txDrp", "txDrp", false),
                a("txErrRaw", "txErrRaw", false),
                a("txFifoErr", "txFifoErr", false),
                a("txHeartErr", "txHeartErr", false),
                a("txPackets", "txPackets", false),
                a("txPacketsLossRate", "txPacketsLossRatePct", true),
                a("txPktRaw", "txPktRaw", false),
                a("txThroughput", "txThroughputBytesPerSec", false),
                a("txWinErr", "txWinErr", false))),

    // ---- Cluster tier (v2 "Cluster tier: 10 types, 189 keys") ----

    VSAN_CLUSTER_CAPACITY("vsan-cluster-capacity", Tier.CLUSTER, Target.CLUSTER, "Capacity", IdForm.ONE, list(
                m("dedupRatio", "dedupRatio", false),
                m("free", "freeBytes", false),
                m("savedByDedup", "savedByDedupBytes", false),
                m("total", "totalBytes", false),
                m("used", "usedBytes", false),
                a("totalDpOverhead", "totalDpOverheadBytes", false))),

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
                m("writeCongestion", "writeCongestion", false),
                a("numOio", "numOio", false),
                a("readCount", "readCount", false),
                a("writeCount", "writeCount", false))),

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
                m("tputResyncRead", "tputResyncReadBytesPerSec", false),
                a("numOio", "numOio", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false))),

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
                m("writeCongestion", "writeCongestion", false),
                a("avgDeltaCreationDomActiveLatency", "avgDeltaCreationDomActiveLatencySec", false),
                a("avgDeltaCreationDomCreationLatency", "avgDeltaCreationDomCreationLatencySec", false),
                a("latencyAvgRecWrite", "latencyAvgRecWriteUs", false),
                a("latencyAvgResyncRead", "latencyAvgResyncReadUs", false),
                a("numCompleteDeltaToBaseResyncOps", "numCompleteDeltaToBaseResyncOps", false),
                a("numCompleteResyncRwrBatches", "numCompleteResyncRwrBatches", false),
                a("numCompleteRwrOps", "numCompleteRwrOps", false),
                a("numCompleteSiblingToDeltaResyncOps", "numCompleteSiblingToDeltaResyncOps", false),
                a("numOio", "numOio", false),
                a("numPendingDeltaToBaseResyncJobs", "numPendingDeltaToBaseResyncJobs", false),
                a("numPendingSiblingToDeltaResyncJobs", "numPendingSiblingToDeltaResyncJobs", false),
                a("numRunningDeltaToBaseResyncJobs", "numRunningDeltaToBaseResyncJobs", false),
                a("numRunningSiblingToDeltaResyncJobs", "numRunningSiblingToDeltaResyncJobs", false),
                a("numSuspendedDeltaToBaseResyncJobs", "numSuspendedDeltaToBaseResyncJobs", false),
                a("numSuspendedFullResyncJobs", "numSuspendedFullResyncJobs", false),
                a("numSuspendedPriorityResyncJobs", "numSuspendedPriorityResyncJobs", false),
                a("numSuspendedResyncChangemaps", "numSuspendedResyncChangemaps", false),
                a("numSuspendedSharedResyncJobs", "numSuspendedSharedResyncJobs", false),
                a("numSuspendedSiblingToDeltaResyncJobs", "numSuspendedSiblingToDeltaResyncJobs", false),
                a("totalDeltasCreatedCount", "totalDeltasCreatedCount", false),
                a("tputRecWrite", "tputRecWriteBytesPerSec", false),
                a("tputResyncRead", "tputResyncReadBytesPerSec", false),
                a("unmapCongestion", "unmapCongestion", false))),

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
                m("tputDedupHashObjWrite", "tputDedupHashObjWrite", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("dedupChunkReadCongestion", "dedupChunkReadCongestion", false),
                a("dedupChunkRecoveryUnmapCongestion", "dedupChunkRecoveryUnmapCongestion", false),
                a("dedupChunkRecoveryWriteCongestion", "dedupChunkRecoveryWriteCongestion", false),
                a("dedupChunkResyncReadCongestion", "dedupChunkResyncReadCongestion", false),
                a("dedupChunkUnmapCongestion", "dedupChunkUnmapCongestion", false),
                a("dedupChunkUnmapCount", "dedupChunkUnmapCount", false),
                a("dedupChunkWriteCongestion", "dedupChunkWriteCongestion", false),
                a("dedupEngineReadCongestion", "dedupEngineReadCongestion", false),
                a("dedupEngineWriteCongestion", "dedupEngineWriteCongestion", false),
                a("dedupHashReadCongestion", "dedupHashReadCongestion", false),
                a("dedupHashRecoveryUnmapCongestion", "dedupHashRecoveryUnmapCongestion", false),
                a("dedupHashRecoveryWriteCongestion", "dedupHashRecoveryWriteCongestion", false),
                a("dedupHashResyncReadCongestion", "dedupHashResyncReadCongestion", false),
                a("dedupHashUnmapCongestion", "dedupHashUnmapCongestion", false),
                a("dedupHashUnmapCount", "dedupHashUnmapCount", false),
                a("dedupHashWriteCongestion", "dedupHashWriteCongestion", false),
                a("iopsDedupChunkObjUnmap", "iopsDedupChunkObjUnmap", false),
                a("iopsDedupHashObjUnmap", "iopsDedupHashObjUnmap", false))),

    CLUSTER_RDT_NETWORK_LATENCY("cluster-rdt-network-latency", Tier.CLUSTER, Target.CLUSTER, "RDT Latency", IdForm.ONE, list(
                m("avgLatency", "avgLatencyUs", false),
                m("kaReset", "kaReset", false),
                m("maxLatency", "maxLatencyUs", false),
                m("minLatency", "minLatencyUs", false),
                m("numReadyDelay", "numReadyDelay", false),
                m("rxSbSpaceMin", "rxSbSpaceMinBytes", false),
                m("txCtxQMax", "txCtxQMaxBytes", false),
                m("txQLatAvg", "txQLatAvgUs", false),
                m("txSbSpaceMin", "txSbSpaceMinBytes", false),
                a("rxSbSpaceAvg", "rxSbSpaceAvgBytes", false),
                a("rxSbSpaceMax", "rxSbSpaceMaxBytes", false),
                a("txCtxQAvg", "txCtxQAvgBytes", false),
                a("txCtxQMin", "txCtxQMinBytes", false),
                a("txQLatMax", "txQLatMaxUs", false),
                a("txQLatMin", "txQLatMinUs", false),
                a("txSbSpaceAvg", "txSbSpaceAvgBytes", false),
                a("txSbSpaceMax", "txSbSpaceMaxBytes", false))),

    CLUSTER_ZDOM_TOP_STATS("cluster-zdom-top-stats", Tier.CLUSTER, Target.CLUSTER, "ZDOM Top Stats", IdForm.ONE, list(
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyAvgRead", "latencyAvgReadUs", false),
                m("latencyAvgWrite", "latencyAvgWriteUs", false),
                m("oio", "oio", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false),
                a("iopsUnmap", "iopsUnmap", false),
                a("latencyAvgUnmap", "latencyAvgUnmapUs", false),
                a("oioRead", "oioRead", false),
                a("oioWrite", "oioWrite", false),
                a("throughputUnmap", "throughputUnmapBytesPerSec", false))),

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

    // ---- Build 3 (v3 "New types"), metric_scope=all only: cluster tier ----

    CLUSTER_ESA_DEDUP_DOMCOMPMGR_IO("cluster-esa-dedup-domcompmgr-io", Tier.CLUSTER, Target.CLUSTER, "ESA Dedup DOM Comp Mgr", IdForm.ONE, F.ALL_ONLY, list(
                a("dedupChunkReadCount", "dedupChunkReadCount", false),
                a("dedupChunkWriteCount", "dedupChunkWriteCount", false),
                a("dedupEngineReadCount", "dedupEngineReadCount", false),
                a("dedupEngineWriteCount", "dedupEngineWriteCount", false),
                a("dedupHashReadCount", "dedupHashReadCount", false),
                a("dedupHashWriteCount", "dedupHashWriteCount", false),
                a("iopsDedupChunkObjRead", "iopsDedupChunkObjRead", false),
                a("iopsDedupChunkObjWrite", "iopsDedupChunkObjWrite", false),
                a("iopsDedupEngineRead", "iopsDedupEngineRead", false),
                a("iopsDedupEngineWrite", "iopsDedupEngineWrite", false),
                a("iopsDedupHashObjRead", "iopsDedupHashObjRead", false),
                a("iopsDedupHashObjWrite", "iopsDedupHashObjWrite", false),
                a("latencyAvgDedupChunkObjRead", "latencyAvgDedupChunkObjReadUs", false),
                a("latencyAvgDedupChunkObjWrite", "latencyAvgDedupChunkObjWriteUs", false),
                a("latencyAvgDedupEngineRead", "latencyAvgDedupEngineReadUs", false),
                a("latencyAvgDedupEngineWrite", "latencyAvgDedupEngineWriteUs", false),
                a("latencyAvgDedupHashObjRead", "latencyAvgDedupHashObjReadUs", false),
                a("latencyAvgDedupHashObjWrite", "latencyAvgDedupHashObjWriteUs", false),
                a("tputDedupChunkObjRead", "tputDedupChunkObjRead", false),
                a("tputDedupChunkObjWrite", "tputDedupChunkObjWrite", false),
                a("tputDedupEngineRead", "tputDedupEngineRead", false),
                a("tputDedupEngineWrite", "tputDedupEngineWrite", false),
                a("tputDedupHashObjRead", "tputDedupHashObjRead", false),
                a("tputDedupHashObjWrite", "tputDedupHashObjWrite", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("dedupChunkReadCongestion", "dedupChunkReadCongestion", false),
                a("dedupChunkRecoveryUnmapCongestion", "dedupChunkRecoveryUnmapCongestion", false),
                a("dedupChunkRecoveryWriteCongestion", "dedupChunkRecoveryWriteCongestion", false),
                a("dedupChunkResyncReadCongestion", "dedupChunkResyncReadCongestion", false),
                a("dedupChunkUnmapCongestion", "dedupChunkUnmapCongestion", false),
                a("dedupChunkUnmapCount", "dedupChunkUnmapCount", false),
                a("dedupChunkWriteCongestion", "dedupChunkWriteCongestion", false),
                a("dedupEngineReadCongestion", "dedupEngineReadCongestion", false),
                a("dedupEngineWriteCongestion", "dedupEngineWriteCongestion", false),
                a("dedupHashReadCongestion", "dedupHashReadCongestion", false),
                a("dedupHashRecoveryUnmapCongestion", "dedupHashRecoveryUnmapCongestion", false),
                a("dedupHashRecoveryWriteCongestion", "dedupHashRecoveryWriteCongestion", false),
                a("dedupHashResyncReadCongestion", "dedupHashResyncReadCongestion", false),
                a("dedupHashUnmapCongestion", "dedupHashUnmapCongestion", false),
                a("dedupHashUnmapCount", "dedupHashUnmapCount", false),
                a("dedupHashWriteCongestion", "dedupHashWriteCongestion", false),
                a("iopsDedupChunkObjUnmap", "iopsDedupChunkObjUnmap", false),
                a("iopsDedupHashObjUnmap", "iopsDedupHashObjUnmap", false))),

    CLUSTER_ESA_DEDUP_DOMOWNER_IO("cluster-esa-dedup-domowner-io", Tier.CLUSTER, Target.CLUSTER, "ESA Dedup DOM Owner", IdForm.ONE, F.ALL_ONLY, list(
                a("dedupChunkReadCount", "dedupChunkReadCount", false),
                a("dedupChunkWriteCount", "dedupChunkWriteCount", false),
                a("dedupEngineReadCount", "dedupEngineReadCount", false),
                a("dedupEngineWriteCount", "dedupEngineWriteCount", false),
                a("dedupHashReadCount", "dedupHashReadCount", false),
                a("dedupHashWriteCount", "dedupHashWriteCount", false),
                a("iopsDedupChunkObjRead", "iopsDedupChunkObjRead", false),
                a("iopsDedupChunkObjWrite", "iopsDedupChunkObjWrite", false),
                a("iopsDedupEngineRead", "iopsDedupEngineRead", false),
                a("iopsDedupEngineWrite", "iopsDedupEngineWrite", false),
                a("iopsDedupHashObjRead", "iopsDedupHashObjRead", false),
                a("iopsDedupHashObjWrite", "iopsDedupHashObjWrite", false),
                a("latencyAvgDedupChunkObjRead", "latencyAvgDedupChunkObjReadUs", false),
                a("latencyAvgDedupChunkObjWrite", "latencyAvgDedupChunkObjWriteUs", false),
                a("latencyAvgDedupEngineRead", "latencyAvgDedupEngineReadUs", false),
                a("latencyAvgDedupEngineWrite", "latencyAvgDedupEngineWriteUs", false),
                a("latencyAvgDedupHashObjRead", "latencyAvgDedupHashObjReadUs", false),
                a("latencyAvgDedupHashObjWrite", "latencyAvgDedupHashObjWriteUs", false),
                a("tputDedupChunkObjRead", "tputDedupChunkObjRead", false),
                a("tputDedupChunkObjWrite", "tputDedupChunkObjWrite", false),
                a("tputDedupEngineRead", "tputDedupEngineRead", false),
                a("tputDedupEngineWrite", "tputDedupEngineWrite", false),
                a("tputDedupHashObjRead", "tputDedupHashObjRead", false),
                a("tputDedupHashObjWrite", "tputDedupHashObjWrite", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("dedupChunkReadCongestion", "dedupChunkReadCongestion", false),
                a("dedupChunkRecoveryUnmapCongestion", "dedupChunkRecoveryUnmapCongestion", false),
                a("dedupChunkRecoveryWriteCongestion", "dedupChunkRecoveryWriteCongestion", false),
                a("dedupChunkResyncReadCongestion", "dedupChunkResyncReadCongestion", false),
                a("dedupChunkUnmapCongestion", "dedupChunkUnmapCongestion", false),
                a("dedupChunkUnmapCount", "dedupChunkUnmapCount", false),
                a("dedupChunkWriteCongestion", "dedupChunkWriteCongestion", false),
                a("dedupEngineReadCongestion", "dedupEngineReadCongestion", false),
                a("dedupEngineWriteCongestion", "dedupEngineWriteCongestion", false),
                a("dedupHashReadCongestion", "dedupHashReadCongestion", false),
                a("dedupHashRecoveryUnmapCongestion", "dedupHashRecoveryUnmapCongestion", false),
                a("dedupHashRecoveryWriteCongestion", "dedupHashRecoveryWriteCongestion", false),
                a("dedupHashResyncReadCongestion", "dedupHashResyncReadCongestion", false),
                a("dedupHashUnmapCongestion", "dedupHashUnmapCongestion", false),
                a("dedupHashUnmapCount", "dedupHashUnmapCount", false),
                a("dedupHashWriteCongestion", "dedupHashWriteCongestion", false),
                a("iopsDedupChunkObjUnmap", "iopsDedupChunkObjUnmap", false),
                a("iopsDedupHashObjUnmap", "iopsDedupHashObjUnmap", false),
                a("dedupChunkReadLeafOwnerCountLocal", "dedupChunkReadLeafOwnerCountLocal", false),
                a("dedupChunkReadLeafOwnerCountRemote", "dedupChunkReadLeafOwnerCountRemote", false),
                a("dedupChunkWriteLeafOwnerCountLocal", "dedupChunkWriteLeafOwnerCountLocal", false),
                a("dedupChunkWriteLeafOwnerCountRemote", "dedupChunkWriteLeafOwnerCountRemote", false),
                a("dedupHashReadLeafOwnerCountLocal", "dedupHashReadLeafOwnerCountLocal", false),
                a("dedupHashReadLeafOwnerCountRemote", "dedupHashReadLeafOwnerCountRemote", false),
                a("dedupHashWriteLeafOwnerCountLocal", "dedupHashWriteLeafOwnerCountLocal", false),
                a("dedupHashWriteLeafOwnerCountRemote", "dedupHashWriteLeafOwnerCountRemote", false),
                a("iopsDedupChunkReadLeafOwnerLocal", "iopsDedupChunkReadLeafOwnerLocal", false),
                a("iopsDedupChunkReadLeafOwnerRemote", "iopsDedupChunkReadLeafOwnerRemote", false),
                a("iopsDedupChunkWriteLeafOwnerLocal", "iopsDedupChunkWriteLeafOwnerLocal", false),
                a("iopsDedupChunkWriteLeafOwnerRemote", "iopsDedupChunkWriteLeafOwnerRemote", false),
                a("iopsDedupHashReadLeafOwnerLocal", "iopsDedupHashReadLeafOwnerLocal", false),
                a("iopsDedupHashReadLeafOwnerRemote", "iopsDedupHashReadLeafOwnerRemote", false),
                a("iopsDedupHashWriteLeafOwnerLocal", "iopsDedupHashWriteLeafOwnerLocal", false),
                a("iopsDedupHashWriteLeafOwnerRemote", "iopsDedupHashWriteLeafOwnerRemote", false),
                a("latencyAvgDedupChunkReadLeafOwnerLocal", "latencyAvgDedupChunkReadLeafOwnerLocal", false),
                a("latencyAvgDedupChunkReadLeafOwnerRemote", "latencyAvgDedupChunkReadLeafOwnerRemote", false),
                a("latencyAvgDedupChunkWriteLeafOwnerLocal", "latencyAvgDedupChunkWriteLeafOwnerLocal", false),
                a("latencyAvgDedupChunkWriteLeafOwnerRemote", "latencyAvgDedupChunkWriteLeafOwnerRemote", false),
                a("latencyAvgDedupHashReadLeafOwnerLocal", "latencyAvgDedupHashReadLeafOwnerLocal", false),
                a("latencyAvgDedupHashReadLeafOwnerRemote", "latencyAvgDedupHashReadLeafOwnerRemote", false),
                a("latencyAvgDedupHashWriteLeafOwnerLocal", "latencyAvgDedupHashWriteLeafOwnerLocal", false),
                a("latencyAvgDedupHashWriteLeafOwnerRemote", "latencyAvgDedupHashWriteLeafOwnerRemote", false),
                a("tputDedupChunkReadLeafCompLocal", "tputDedupChunkReadLeafCompLocal", false),
                a("tputDedupChunkReadLeafCompRemote", "tputDedupChunkReadLeafCompRemote", false),
                a("tputDedupChunkReadLeafOwnerLocal", "tputDedupChunkReadLeafOwnerLocal", false),
                a("tputDedupChunkReadLeafOwnerRemote", "tputDedupChunkReadLeafOwnerRemote", false),
                a("tputDedupChunkWriteLeafCompLocal", "tputDedupChunkWriteLeafCompLocal", false),
                a("tputDedupChunkWriteLeafCompRemote", "tputDedupChunkWriteLeafCompRemote", false),
                a("tputDedupChunkWriteLeafOwnerLocal", "tputDedupChunkWriteLeafOwnerLocal", false),
                a("tputDedupChunkWriteLeafOwnerRemote", "tputDedupChunkWriteLeafOwnerRemote", false),
                a("tputDedupHashReadLeafCompLocal", "tputDedupHashReadLeafCompLocal", false),
                a("tputDedupHashReadLeafCompRemote", "tputDedupHashReadLeafCompRemote", false),
                a("tputDedupHashReadLeafOwnerLocal", "tputDedupHashReadLeafOwnerLocal", false),
                a("tputDedupHashReadLeafOwnerRemote", "tputDedupHashReadLeafOwnerRemote", false),
                a("tputDedupHashWriteLeafCompLocal", "tputDedupHashWriteLeafCompLocal", false),
                a("tputDedupHashWriteLeafCompRemote", "tputDedupHashWriteLeafCompRemote", false),
                a("tputDedupHashWriteLeafOwnerLocal", "tputDedupHashWriteLeafOwnerLocal", false),
                a("tputDedupHashWriteLeafOwnerRemote", "tputDedupHashWriteLeafOwnerRemote", false))),

    CLUSTER_ESA_DEDUP_STORE_CHUNKSVC("cluster-esa-dedup-store-chunksvc", Tier.CLUSTER, Target.CLUSTER, "ESA Dedup Chunk Service", IdForm.ONE, F.ALL_ONLY, list(
                a("latencyChangeRefCount", "latencyChangeRefCountUs", false),
                a("latencyDeleteChunkExtent", "latencyDeleteChunkExtentUs", false),
                a("latencyDeletedObjCleanup", "latencyDeletedObjCleanupUs", false),
                a("latencyGetChunkEntries", "latencyGetChunkEntriesUs", false),
                a("latencyReserveMba", "latencyReserveMbaUs", false),
                a("numChangeRefCount", "numChangeRefCount", false),
                a("numDeleteChunkExtent", "numDeleteChunkExtent", false),
                a("numDeletedObjCleanup", "numDeletedObjCleanup", false),
                a("numGetChunkEntries", "numGetChunkEntries", false),
                a("numObjsReported", "numObjsReported", false),
                a("numReserveMba", "numReserveMba", false),
                a("rateBlocksInChunkDeleted", "rateBlocksInChunkDeleted", false),
                a("rateBlocksInChunkWritten", "rateBlocksInChunkWritten", false),
                a("rateChangeRefCount", "rateChangeRefCount", false),
                a("rateChunkRefEntAdded", "rateChunkRefEntAdded", false),
                a("rateChunkRefEntRemoved", "rateChunkRefEntRemoved", false),
                a("rateChunksDeleted", "rateChunksDeleted", false),
                a("rateChunksInChangeRefCount", "rateChunksInChangeRefCount", false),
                a("rateChunksInDeleteChunkExtent", "rateChunksInDeleteChunkExtent", false),
                a("rateChunksInGetChunkEntries", "rateChunksInGetChunkEntries", false),
                a("rateChunksWritten", "rateChunksWritten", false),
                a("rateDeleteChunkExtent", "rateDeleteChunkExtent", false),
                a("rateDeletedObjCleanup", "rateDeletedObjCleanup", false),
                a("rateGetChunkEntries", "rateGetChunkEntries", false),
                a("rateReserveMba", "rateReserveMba", false),
                a("totalNumBlks", "totalNumBlks", false),
                a("totalNumRefCnt", "totalNumRefCnt", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("rateAsyncTaskBatches", "rateAsyncTaskBatches", false),
                a("rateChunksOpportunisticDelete", "rateChunksOpportunisticDelete", false),
                a("rateChunksRetained", "rateChunksRetained", false),
                a("rateChunksToDelete", "rateChunksToDelete", false),
                a("totalNumBytesRetained", "totalNumBytesRetained", false),
                a("totalTputRetained", "totalTputRetained", false))),

    CLUSTER_ESA_DEDUP_STORE_HASHSVC("cluster-esa-dedup-store-hashsvc", Tier.CLUSTER, Target.CLUSTER, "ESA Dedup Hash Service", IdForm.ONE, F.ALL_ONLY, list(
                a("currentGenNum", "currentGenNum", false),
                a("currentStaleGenNum", "currentStaleGenNum", false),
                a("latencyDeleteChunksReq", "latencyDeleteChunksReqUs", false),
                a("latencyNewDupReq", "latencyNewDupReqUs", false),
                a("latencyReadLockWait", "latencyReadLockWaitUs", false),
                a("latencyUniqueHashReq", "latencyUniqueHashReqUs", false),
                a("latencyWriteLockWait", "latencyWriteLockWaitUs", false),
                a("numDeleteChunksReq", "numDeleteChunksReq", false),
                a("numNewDupReq", "numNewDupReq", false),
                a("numObjsReported", "numObjsReported", false),
                a("numReadLockWait", "numReadLockWait", false),
                a("numUniqHashReq", "numUniqHashReq", false),
                a("numWriteLockWait", "numWriteLockWait", false),
                a("rateChunkInDeleteChunksReq", "rateChunkInDeleteChunksReq", false),
                a("rateChunkInNewDupReq", "rateChunkInNewDupReq", false),
                a("rateChunkInUniqHashReq", "rateChunkInUniqHashReq", false),
                a("rateDedupHashEntryAdded", "rateDedupHashEntryAdded", false),
                a("rateDedupHashEntryRemoved", "rateDedupHashEntryRemoved", false),
                a("rateDedupHashEntryUpdated", "rateDedupHashEntryUpdated", false),
                a("rateDeleteChunksReq", "rateDeleteChunksReq", false),
                a("rateDeletedChunks", "rateDeletedChunks", false),
                a("rateHashKeyRemovalEntriesPerGen", "rateHashKeyRemovalEntriesPerGen", false),
                a("rateHashKeyRemovalEntriesToScanInStep", "rateHashKeyRemovalEntriesToScanInStep", false),
                a("rateHashKeyRemovalStepsDone", "rateHashKeyRemovalStepsDone", false),
                a("rateNewDupChunks", "rateNewDupChunks", false),
                a("rateNewDupReq", "rateNewDupReq", false),
                a("rateOldDupChunks", "rateOldDupChunks", false),
                a("rateReadLockWait", "rateReadLockWait", false),
                a("rateRetryChunks", "rateRetryChunks", false),
                a("rateSentriesAdded", "rateSentriesAdded", false),
                a("rateSentriesRemoved", "rateSentriesRemoved", false),
                a("rateUniqChunks", "rateUniqChunks", false),
                a("rateUniqHashReq", "rateUniqHashReq", false),
                a("rateWriteLockWait", "rateWriteLockWait", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("capBytesPerUnitPerHashObj", "capBytesPerUnitPerHashObj", false),
                a("rateDupEntries", "rateDupEntries", false),
                a("rateLiveUniqEntries", "rateLiveUniqEntries", false),
                a("rateStaleUniqEntries", "rateStaleUniqEntries", false),
                a("rateUniqEntriesPerUnit", "rateUniqEntriesPerUnit", false))),

    VSAN_DP_HISTORICAL_STATS("vsan-dp-historical-stats", Tier.CLUSTER, Target.CLUSTER, "Data Protection", IdForm.ONE, F.ALL_ONLY, list(
                a("totalSnapshotCapacity", "totalSnapshotCapacity", false))),

    CLUSTER_RESYNC("cluster-resync", Tier.CLUSTER, Target.CLUSTER_OR_HOST, "Resync", IdForm.ONE, F.ALL_ONLY, list(
                a("iopsResyncReadDecom", "iopsResyncReadDecom", false),
                a("iopsResyncReadFixComp", "iopsResyncReadFixComp", false),
                a("iopsResyncReadPolicy", "iopsResyncReadPolicy", false),
                a("iopsResyncReadRebalance", "iopsResyncReadRebalance", false),
                a("iopsResyncWriteDecom", "iopsResyncWriteDecom", false),
                a("iopsResyncWriteFixComp", "iopsResyncWriteFixComp", false),
                a("iopsResyncWritePolicy", "iopsResyncWritePolicy", false),
                a("iopsResyncWriteRebalance", "iopsResyncWriteRebalance", false),
                a("latResyncReadDecom", "latResyncReadDecomUs", false),
                a("latResyncReadFixComp", "latResyncReadFixCompUs", false),
                a("latResyncReadPolicy", "latResyncReadPolicyUs", false),
                a("latResyncReadRebalance", "latResyncReadRebalanceUs", false),
                a("latResyncWriteDecom", "latResyncWriteDecomUs", false),
                a("latResyncWriteFixComp", "latResyncWriteFixCompUs", false),
                a("latResyncWritePolicy", "latResyncWritePolicyUs", false),
                a("latResyncWriteRebalance", "latResyncWriteRebalanceUs", false),
                a("tputResyncReadDecom", "tputResyncReadDecomBytesPerSec", false),
                a("tputResyncReadFixComp", "tputResyncReadFixCompBytesPerSec", false),
                a("tputResyncReadPolicy", "tputResyncReadPolicyBytesPerSec", false),
                a("tputResyncReadRebalance", "tputResyncReadRebalanceBytesPerSec", false),
                a("tputResyncWriteDecom", "tputResyncWriteDecomBytesPerSec", false),
                a("tputResyncWriteFixComp", "tputResyncWriteFixCompBytesPerSec", false),
                a("tputResyncWritePolicy", "tputResyncWritePolicyBytesPerSec", false),
                a("tputResyncWriteRebalance", "tputResyncWriteRebalanceBytesPerSec", false))),

    CLUSTER_PMEM("cluster-pmem", Tier.CLUSTER, Target.CLUSTER_OR_HOST, "PMem", IdForm.ONE, F.ALL_ONLY, list(
                a("bandwidthRead", "bandwidthReadBytesPerSec", false),
                a("bandwidthTotal", "bandwidthTotalBytesPerSec", false),
                a("bandwidthWrite", "bandwidthWriteBytesPerSec", false),
                a("iopsRead", "iopsRead", false),
                a("iopsTotal", "iopsTotal", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyRead", "latencyRead", false),
                a("latencyWrite", "latencyWrite", false))),

    CLUSTER_REMOTEDOMCLIENT("cluster-remotedomclient", Tier.CLUSTER, Target.CLUSTER, "Remote DOM Client", IdForm.ONE, F.ALL_ONLY | F.REMOTE, list(
                a("congestion", "congestion", false),
                a("iopsRead", "iopsRead", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyAvgRead", "latencyAvgReadUs", false),
                a("latencyAvgWrite", "latencyAvgWriteUs", false),
                a("oio", "oio", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false))),

    COMPUTECLUSTER_REMOTEDOMCLIENT("computeCluster-remotedomclient", Tier.CLUSTER, Target.CLUSTER, "Compute Remote DOM Client", IdForm.ONE, F.ALL_ONLY | F.REMOTE, list(
                a("congestion", "congestion", false),
                a("iopsRead", "iopsRead", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyAvgRead", "latencyAvgReadUs", false),
                a("latencyAvgWrite", "latencyAvgWriteUs", false),
                a("oio", "oio", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false))),

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
                m("vmdiskQueueDepthWrite", "vmdiskQueueDepthWrite", false),
                a("congestion", "congestion", false),
                a("ioCount", "ioCount", false),
                a("iops", "iops", false),
                a("iopsRecUnmap", "iopsRecUnmap", false),
                a("iopsRecWrite", "iopsRecWrite", false),
                a("iopsResyncRead", "iopsResyncRead", false),
                a("iopsUnmap", "iopsUnmap", false),
                a("latAvgResyncRead", "latAvgResyncReadUs", false),
                a("latencyAvg", "latencyAvgUs", false),
                a("latencyAvgRecUnmap", "latencyAvgRecUnmapUs", false),
                a("latencyAvgRecWrite", "latencyAvgRecWriteUs", false),
                a("latencyAvgServiceVMRead", "latencyAvgServiceVMReadUs", false),
                a("latencyAvgServiceVMWrite", "latencyAvgServiceVMWriteUs", false),
                a("latencyAvgUnmap", "latencyAvgUnmapUs", false),
                a("latencyMaxUnmap", "latencyMaxUnmapUs", false),
                a("latencyStddev", "latencyStddevUs", false),
                a("latencyStddevRead", "latencyStddevReadUs", false),
                a("latencyStddevUnmap", "latencyStddevUnmapUs", false),
                a("metadataDispatchedCostRead", "metadataDispatchedCostReadBytesPerSec", false),
                a("metadataDispatchedCostWrite", "metadataDispatchedCostWriteBytesPerSec", false),
                a("metadataQueueDepthRead", "metadataQueueDepthRead", false),
                a("metadataQueueDepthWrite", "metadataQueueDepthWrite", false),
                a("namespaceDispatchedCostRead", "namespaceDispatchedCostReadBytesPerSec", false),
                a("namespaceQueueDepthRead", "namespaceQueueDepthRead", false),
                a("namespaceQueueDepthWrite", "namespaceQueueDepthWrite", false),
                a("numOio", "numOio", false),
                a("oioRead", "oioRead", false),
                a("oioRecoveryWrite", "oioRecoveryWrite", false),
                a("oioResyncRead", "oioResyncRead", false),
                a("oioWrite", "oioWrite", false),
                a("rateCompmgrOpsBadParam", "rateCompmgrOpsBadParam", false),
                a("rateCompmgrOpsChecksumMismatch", "rateCompmgrOpsChecksumMismatch", false),
                a("rateCompmgrOpsIoError", "rateCompmgrOpsIoError", false),
                a("rateCompmgrOpsLimitExceed", "rateCompmgrOpsLimitExceed", false),
                a("rateCompmgrOpsNoConnect", "rateCompmgrOpsNoConnect", false),
                a("rateCompmgrOpsNoMem", "rateCompmgrOpsNoMem", false),
                a("rateCompmgrOpsNoSpace", "rateCompmgrOpsNoSpace", false),
                a("rateCompmgrOpsNotSupported", "rateCompmgrOpsNotSupported", false),
                a("rateCompmgrOpsRetry", "rateCompmgrOpsRetry", false),
                a("rateOtherCompmgrOpsFailure", "rateOtherCompmgrOpsFailure", false),
                a("readCongestion", "readCongestion", false),
                a("readCount", "readCount", false),
                a("recUnmapCongestion", "recUnmapCongestion", false),
                a("recUnmapCount", "recUnmapCount", false),
                a("recWriteCongestion", "recWriteCongestion", false),
                a("recWriteCount", "recWriteCount", false),
                a("regulatorIopsRead", "regulatorIopsRead", false),
                a("regulatorIopsWrite", "regulatorIopsWrite", false),
                a("resyncDispatchedCostRead", "resyncDispatchedCostReadBytesPerSec", false),
                a("resyncDispatchedCostWrite", "resyncDispatchedCostWriteBytesPerSec", false),
                a("resyncQueueDepthRead", "resyncQueueDepthRead", false),
                a("resyncQueueDepthWrite", "resyncQueueDepthWrite", false),
                a("resyncReadCount", "resyncReadCount", false),
                a("throughput", "throughputBytesPerSec", false),
                a("throughputRecUnmap", "throughputRecUnmapBytesPerSec", false),
                a("throughputRecWrite", "throughputRecWriteBytesPerSec", false),
                a("throughputUnmap", "throughputUnmapBytesPerSec", false),
                a("tputResyncRead", "tputResyncReadBytesPerSec", false),
                a("tputServiceVMRead", "tputServiceVMReadBytesPerSec", false),
                a("tputServiceVMWrite", "tputServiceVMWriteBytesPerSec", false),
                a("unmapCongestion", "unmapCongestion", false),
                a("unmapCount", "unmapCount", false),
                a("vmdiskDispatchedCostRead", "vmdiskDispatchedCostReadBytesPerSec", false),
                a("vmdiskQueueDepthRead", "vmdiskQueueDepthRead", false),
                a("writeCongestion", "writeCongestion", false),
                a("writeCount", "writeCount", false))),

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
                m("tputDedupHashObjWrite", "tputDedupHashObjWrite", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("dedupChunkReadCongestion", "dedupChunkReadCongestion", false),
                a("dedupChunkRecoveryUnmapCongestion", "dedupChunkRecoveryUnmapCongestion", false),
                a("dedupChunkRecoveryWriteCongestion", "dedupChunkRecoveryWriteCongestion", false),
                a("dedupChunkResyncReadCongestion", "dedupChunkResyncReadCongestion", false),
                a("dedupChunkUnmapCongestion", "dedupChunkUnmapCongestion", false),
                a("dedupChunkUnmapCount", "dedupChunkUnmapCount", false),
                a("dedupChunkWriteCongestion", "dedupChunkWriteCongestion", false),
                a("dedupEngineReadCongestion", "dedupEngineReadCongestion", false),
                a("dedupEngineWriteCongestion", "dedupEngineWriteCongestion", false),
                a("dedupHashReadCongestion", "dedupHashReadCongestion", false),
                a("dedupHashRecoveryUnmapCongestion", "dedupHashRecoveryUnmapCongestion", false),
                a("dedupHashRecoveryWriteCongestion", "dedupHashRecoveryWriteCongestion", false),
                a("dedupHashResyncReadCongestion", "dedupHashResyncReadCongestion", false),
                a("dedupHashUnmapCongestion", "dedupHashUnmapCongestion", false),
                a("dedupHashUnmapCount", "dedupHashUnmapCount", false),
                a("dedupHashWriteCongestion", "dedupHashWriteCongestion", false),
                a("iopsDedupChunkObjUnmap", "iopsDedupChunkObjUnmap", false),
                a("iopsDedupHashObjUnmap", "iopsDedupHashObjUnmap", false))),

    VSAN_HOST_NET("vsan-host-net", Tier.HOST, Target.HOST, "Host Network", IdForm.ONE, list(
                m("portRxDrops", "portRxDropsPct", true),
                m("portTxDrops", "portTxDropsPct", true),
                m("rxPackets", "rxPackets", false),
                m("rxPacketsLossRate", "rxPacketsLossRatePct", true),
                m("rxThroughput", "rxThroughputBytesPerSec", false),
                m("txPackets", "txPackets", false),
                m("txPacketsLossRate", "txPacketsLossRatePct", true),
                m("txThroughput", "txThroughputBytesPerSec", false),
                a("ioChainRxdrops", "ioChainRxdrops", false),
                a("ioChainTxdrops", "ioChainTxdrops", false),
                a("portRxpkts", "portRxpkts", false),
                a("portTxpkts", "portTxpkts", false),
                a("tcpRxErrRate", "tcpRxErrRate", false),
                a("tcpRxPackets", "tcpRxPackets", false),
                a("tcpTxPackets", "tcpTxPackets", false),
                a("tcpTxRexmitRate", "tcpTxRexmitRate", false))),

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
                m("writeLatencyMaxUs", "writeLatencyMaxUs", false),
                a("ioCount", "ioCount", false),
                a("iopsUnmap", "iopsUnmap", false),
                a("latencyAvgUnmap", "latencyAvgUnmapUs", false),
                a("oioRead", "oioRead", false),
                a("readCount", "readCount", false),
                a("throughputUnmap", "throughputUnmapBytesPerSec", false),
                a("unmapCount", "unmapCount", false),
                a("writeCount", "writeCount", false))),

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
                m("vsanmgmtdConsumedSize", "vsanmgmtdConsumedSizeBytes", false),
                a("clomdMemAllocMax", "clomdMemAllocMax", false),
                a("clomdMemAllocMin", "clomdMemAllocMin", false),
                a("cmmdsTimeMachineConsumedSize", "cmmdsTimeMachineConsumedSizeBytes", false),
                a("cmmdsTimeMachineMemAllocMax", "cmmdsTimeMachineMemAllocMax", false),
                a("cmmdsTimeMachineMemAllocMin", "cmmdsTimeMachineMemAllocMin", false),
                a("cmmdsTimeMachineReservedSize", "cmmdsTimeMachineReservedSizeBytes", false),
                a("cmmdsdMemAllocMax", "cmmdsdMemAllocMax", false),
                a("cmmdsdMemAllocMin", "cmmdsdMemAllocMin", false),
                a("epdMemAllocMax", "epdMemAllocMax", false),
                a("epdMemAllocMin", "epdMemAllocMin", false),
                a("epdReservedSize", "epdReservedSizeBytes", false),
                a("osfsdMemAllocMax", "osfsdMemAllocMax", false),
                a("osfsdMemAllocMin", "osfsdMemAllocMin", false),
                a("osfsdReservedSize", "osfsdReservedSizeBytes", false),
                a("uwMemAllocMax", "uwMemAllocMax", false),
                a("uwMemAllocMin", "uwMemAllocMin", false),
                a("vitdConsumedSize", "vitdConsumedSizeBytes", false),
                a("vitdMemAllocMax", "vitdMemAllocMax", false),
                a("vitdMemAllocMin", "vitdMemAllocMin", false),
                a("vitdReservedSize", "vitdReservedSizeBytes", false),
                a("vsandevicemonitordMemAllocMax", "vsandevicemonitordMemAllocMax", false),
                a("vsandevicemonitordMemAllocMin", "vsandevicemonitordMemAllocMin", false),
                a("vsandevicemonitordReservedSize", "vsandevicemonitordReservedSizeBytes", false),
                a("vsanmgmtdMemAllocMax", "vsanmgmtdMemAllocMax", false),
                a("vsanmgmtdMemAllocMin", "vsanmgmtdMemAllocMin", false),
                a("vsanmgmtdReservedSize", "vsanmgmtdReservedSizeBytes", false),
                a("vsanmgmtdWatchdogConsumedSize", "vsanmgmtdWatchdogConsumedSizeBytes", false),
                a("vsanmgmtdWatchdogMemAllocMax", "vsanmgmtdWatchdogMemAllocMax", false),
                a("vsanmgmtdWatchdogMemAllocMin", "vsanmgmtdWatchdogMemAllocMin", false),
                a("vsanmgmtdWatchdogReservedSize", "vsanmgmtdWatchdogReservedSizeBytes", false),
                a("vsanobserverConsumedSize", "vsanobserverConsumedSizeBytes", false),
                a("vsanobserverMemAllocMax", "vsanobserverMemAllocMax", false),
                a("vsanobserverMemAllocMin", "vsanobserverMemAllocMin", false),
                a("vsanobserverReservedSize", "vsanobserverReservedSizeBytes", false),
                a("vsantracedConsumedSize", "vsantracedConsumedSizeBytes", false),
                a("vsantracedMemAllocMax", "vsantracedMemAllocMax", false),
                a("vsantracedMemAllocMin", "vsantracedMemAllocMin", false),
                a("vsantracedReservedSize", "vsantracedReservedSizeBytes", false))),

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
                m("txSbSpaceMin", "txSbSpaceMinBytes", false),
                a("rxSbSpaceAvg", "rxSbSpaceAvgBytes", false),
                a("txCtxQAvg", "txCtxQAvgBytes", false),
                a("txCtxQMin", "txCtxQMinBytes", false),
                a("txQLatMin", "txQLatMinUs", false),
                a("txSbSpaceAvg", "txSbSpaceAvgBytes", false),
                a("txSbSpaceMax", "txSbSpaceMaxBytes", false))),

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
                m("tcpTxRexmitRate", "tcpTxRexmitRate", false),
                a("arpDropRate", "arpDropRate", false),
                a("ip6Errs", "ip6ErrsPct", true),
                a("ip6Total", "ip6Total", false),
                a("ipErrs", "ipErrsPct", true),
                a("ipTotal", "ipTotal", false),
                a("tcpEcnCe", "tcpEcnCe", false),
                a("tcpRxPackets", "tcpRxPackets", false),
                a("tcpRxThroughput", "tcpRxThroughputBytesPerSec", false),
                a("tcpTxPackets", "tcpTxPackets", false),
                a("tcpTxThroughput", "tcpTxThroughputBytesPerSec", false))),

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
                m("txThroughput", "txThroughputBytesPerSec", false),
                a("arpDropRate", "arpDropRate", false),
                a("ioChainDrops", "ioChainDrops", false),
                a("ioChainRxdrops", "ioChainRxdrops", false),
                a("ioChainTxdrops", "ioChainTxdrops", false),
                a("ip6Errs", "ip6ErrsPct", true),
                a("ip6Total", "ip6Total", false),
                a("ipErrs", "ipErrsPct", true),
                a("ipTotal", "ipTotal", false),
                a("tcpBadrst", "tcpBadrst", false),
                a("tcpBadsyn", "tcpBadsyn", false),
                a("tcpConndrops", "tcpConndrops", false),
                a("tcpConnects", "tcpConnects", false),
                a("tcpDrops", "tcpDrops", false),
                a("tcpEcnCe", "tcpEcnCe", false),
                a("tcpErrs", "tcpErrsPct", true),
                a("tcpHalfopenDropRate", "tcpHalfopenDropRatePct", true),
                a("tcpKeepdrops", "tcpKeepdrops", false),
                a("tcpKeeptimeo", "tcpKeeptimeo", false),
                a("tcpPersisttimeo", "tcpPersisttimeo", false),
                a("tcpRcvackpack", "tcpRcvackpack", false),
                a("tcpRcvbadoff", "tcpRcvbadoff", false),
                a("tcpRcvbadsum", "tcpRcvbadsum", false),
                a("tcpRcvdupack", "tcpRcvdupack", false),
                a("tcpRcvdupackRate", "tcpRcvdupackRatePct", true),
                a("tcpRcvduppack", "tcpRcvduppack", false),
                a("tcpRcvduppackRate", "tcpRcvduppackRatePct", true),
                a("tcpRcvmemdrop", "tcpRcvmemdrop", false),
                a("tcpRcvoopack", "tcpRcvoopack", false),
                a("tcpRcvoopackRate", "tcpRcvoopackRatePct", true),
                a("tcpRcvshort", "tcpRcvshort", false),
                a("tcpRexmttimeo", "tcpRexmttimeo", false),
                a("tcpRxErrRate", "tcpRxErrRate", false),
                a("tcpRxPackets", "tcpRxPackets", false),
                a("tcpRxThroughput", "tcpRxThroughputBytesPerSec", false),
                a("tcpSackRcvBlocks", "tcpSackRcvBlocks", false),
                a("tcpSackRcvBlocksRate", "tcpSackRcvBlocksRatePct", true),
                a("tcpSackRexmits", "tcpSackRexmits", false),
                a("tcpSackRexmitsRate", "tcpSackRexmitsRatePct", true),
                a("tcpSackSendBlocks", "tcpSackSendBlocks", false),
                a("tcpSackSendBlocksRate", "tcpSackSendBlocksRatePct", true),
                a("tcpSndZeroWin", "tcpSndZeroWin", false),
                a("tcpSndacks", "tcpSndacks", false),
                a("tcpTimeoutDropRate", "tcpTimeoutDropRatePct", true),
                a("tcpTimeoutdrop", "tcpTimeoutdrop", false),
                a("tcpTxPackets", "tcpTxPackets", false),
                a("tcpTxRexmitRate", "tcpTxRexmitRate", false),
                a("tcpTxThroughput", "tcpTxThroughputBytesPerSec", false))),

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
                m("txSbSpaceMin", "txSbSpaceMinBytes", false),
                a("rxSbSpaceAvg", "rxSbSpaceAvgBytes", false),
                a("rxSbSpaceMax", "rxSbSpaceMaxBytes", false),
                a("txCtxQAvg", "txCtxQAvgBytes", false),
                a("txCtxQMin", "txCtxQMinBytes", false),
                a("txQLatMin", "txQLatMinUs", false),
                a("txSbSpaceAvg", "txSbSpaceAvgBytes", false),
                a("txSbSpaceMax", "txSbSpaceMaxBytes", false))),

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
                m("rateTxnReadWrite", "rateTxnReadWrite", false),
                a("allOpCacheMaxGetLatUSec", "allOpCacheMaxGetLatUSec", false),
                a("cacheMissPerLookupTxn", "cacheMissPerLookupTxn", false),
                a("cacheMissPerPrefetchTxn", "cacheMissPerPrefetchTxn", false),
                a("cacheMissPerSegCleaningCtxDataTxn", "cacheMissPerSegCleaningCtxDataTxn", false),
                a("iopsCacheMissRate", "iopsCacheMissRatePct", false),
                a("latAvgCacheGet", "latAvgCacheGetUs", false),
                a("latAvgTxnBankTotalIO", "latAvgTxnBankTotalIOUs", false),
                a("latAvgTxnUnmap", "latAvgTxnUnmapUs", false),
                a("latAvgTxnUnmapTotalIO", "latAvgTxnUnmapTotalIOUs", false),
                a("missesPerIO", "missesPerIO", false),
                a("prefetchCacheMaxGetLatUs", "prefetchCacheMaxGetLatUs", false),
                a("rateTotDirtyCachePage", "rateTotDirtyCachePage", false),
                a("rateTotalBitmapCacheMiss", "rateTotalBitmapCacheMiss", false),
                a("rateTotalCacheMiss", "rateTotalCacheMiss", false),
                a("rateTotalLogicalTreeCacheMiss", "rateTotalLogicalTreeCacheMiss", false),
                a("rateTotalMiddleTreeCacheMiss", "rateTotalMiddleTreeCacheMiss", false),
                a("rateTotalSnapTreeCacheMiss", "rateTotalSnapTreeCacheMiss", false),
                a("rateTotalSutCacheMiss", "rateTotalSutCacheMiss", false),
                a("rateTxnBankLogicalTreeCacheMiss", "rateTxnBankLogicalTreeCacheMiss", false),
                a("rateTxnBankLogicalTreeCacheRef", "rateTxnBankLogicalTreeCacheRef", false),
                a("rateTxnBankMiddleTreeCacheMiss", "rateTxnBankMiddleTreeCacheMiss", false),
                a("rateTxnBankMiddleTreeCacheRef", "rateTxnBankMiddleTreeCacheRef", false),
                a("rateTxnBankSutCacheMiss", "rateTxnBankSutCacheMiss", false),
                a("rateTxnBankSutCacheRef", "rateTxnBankSutCacheRef", false),
                a("rateTxnBankTotalCacheMiss", "rateTxnBankTotalCacheMiss", false),
                a("rateTxnBankTotalCacheRef", "rateTxnBankTotalCacheRef", false),
                a("rateTxnLookupCacheMiss", "rateTxnLookupCacheMiss", false),
                a("rateTxnLookupLogicalTreeCacheMiss", "rateTxnLookupLogicalTreeCacheMiss", false),
                a("rateTxnLookupLogicalTreeCacheRef", "rateTxnLookupLogicalTreeCacheRef", false),
                a("rateTxnLookupMiddleTreeCacheMiss", "rateTxnLookupMiddleTreeCacheMiss", false),
                a("rateTxnLookupMiddleTreeCacheRef", "rateTxnLookupMiddleTreeCacheRef", false),
                a("rateTxnPrefetchLogicalTreeCacheMiss", "rateTxnPrefetchLogicalTreeCacheMiss", false),
                a("rateTxnPrefetchLogicalTreeCacheRef", "rateTxnPrefetchLogicalTreeCacheRef", false),
                a("rateTxnPrefetchMiddleTreeCacheMiss", "rateTxnPrefetchMiddleTreeCacheMiss", false),
                a("rateTxnPrefetchMiddleTreeCacheRef", "rateTxnPrefetchMiddleTreeCacheRef", false),
                a("rateTxnPrefetchSutCacheMiss", "rateTxnPrefetchSutCacheMiss", false),
                a("rateTxnPrefetchSutCacheRef", "rateTxnPrefetchSutCacheRef", false),
                a("rateTxnPrefetchTotalCacheMiss", "rateTxnPrefetchTotalCacheMiss", false),
                a("rateTxnSegCleaningCtxDataLogicalTreeCacheMiss", "rateTxnSegCleaningCtxDataLogicalTreeCacheMiss", false),
                a("rateTxnSegCleaningCtxDataLogicalTreeCacheRef", "rateTxnSegCleaningCtxDataLogicalTreeCacheRef", false),
                a("rateTxnSegCleaningCtxDataMiddleTreeCacheMiss", "rateTxnSegCleaningCtxDataMiddleTreeCacheMiss", false),
                a("rateTxnSegCleaningCtxDataMiddleTreeCacheRef", "rateTxnSegCleaningCtxDataMiddleTreeCacheRef", false),
                a("rateTxnSegCleaningCtxDataSutCacheMiss", "rateTxnSegCleaningCtxDataSutCacheMiss", false),
                a("rateTxnSegCleaningCtxDataSutCacheRef", "rateTxnSegCleaningCtxDataSutCacheRef", false),
                a("rateTxnSegCleaningCtxDataTotalCacheMiss", "rateTxnSegCleaningCtxDataTotalCacheMiss", false),
                a("rateTxnSegCleaningCtxDataTotalCacheRef", "rateTxnSegCleaningCtxDataTotalCacheRef", false),
                a("rateTxnUnmapLogicalTreeCacheMiss", "rateTxnUnmapLogicalTreeCacheMiss", false),
                a("rateTxnUnmapMiddleTreeCacheMiss", "rateTxnUnmapMiddleTreeCacheMiss", false),
                a("rateTxnUnmapSutCacheMiss", "rateTxnUnmapSutCacheMiss", false),
                a("rateTxnUnmapTotalCacheMiss", "rateTxnUnmapTotalCacheMiss", false),
                a("tputCacheMissRate", "tputCacheMissRatePct", false),
                a("txnBankMaxLatUs", "txnBankMaxLatUs", false),
                a("txnCacheFlushMaxLatUs", "txnCacheFlushMaxLatUs", false),
                a("txnDLogMaxLatUs", "txnDLogMaxLatUs", false),
                a("txnDelExtMiddleTreeMaxLatUs", "txnDelExtMiddleTreeMaxLatUs", false),
                a("txnLookupMaxLatUs", "txnLookupMaxLatUs", false),
                a("txnPrefetchMaxLatUs", "txnPrefetchMaxLatUs", false),
                a("txnSegCtxDataMaxLatUs", "txnSegCtxDataMaxLatUs", false),
                a("txnSegCtxLLPMaxLatUs", "txnSegCtxLLPMaxLatUs", false),
                a("txnUnmapMaxLatUs", "txnUnmapMaxLatUs", false),
                a("txnVatWriteSegMaxLatUs", "txnVatWriteSegMaxLatUs", false))),

    // ---- Build 3 (v3 "New types"), metric_scope=all only: host tier ----

    HOST_ESA_DEDUP_DOMCOMPMGR_IO("host-esa-dedup-domcompmgr-io", Tier.HOST, Target.HOST, "ESA Dedup DOM Comp Mgr", IdForm.ONE, F.ALL_ONLY, list(
                a("dedupChunkReadCount", "dedupChunkReadCount", false),
                a("dedupChunkWriteCount", "dedupChunkWriteCount", false),
                a("dedupEngineReadCount", "dedupEngineReadCount", false),
                a("dedupEngineWriteCount", "dedupEngineWriteCount", false),
                a("dedupHashReadCount", "dedupHashReadCount", false),
                a("dedupHashWriteCount", "dedupHashWriteCount", false),
                a("iopsDedupChunkObjRead", "iopsDedupChunkObjRead", false),
                a("iopsDedupChunkObjWrite", "iopsDedupChunkObjWrite", false),
                a("iopsDedupEngineRead", "iopsDedupEngineRead", false),
                a("iopsDedupEngineWrite", "iopsDedupEngineWrite", false),
                a("iopsDedupHashObjRead", "iopsDedupHashObjRead", false),
                a("iopsDedupHashObjWrite", "iopsDedupHashObjWrite", false),
                a("latencyAvgDedupChunkObjRead", "latencyAvgDedupChunkObjReadUs", false),
                a("latencyAvgDedupChunkObjWrite", "latencyAvgDedupChunkObjWriteUs", false),
                a("latencyAvgDedupEngineRead", "latencyAvgDedupEngineReadUs", false),
                a("latencyAvgDedupEngineWrite", "latencyAvgDedupEngineWriteUs", false),
                a("latencyAvgDedupHashObjRead", "latencyAvgDedupHashObjReadUs", false),
                a("latencyAvgDedupHashObjWrite", "latencyAvgDedupHashObjWriteUs", false),
                a("tputDedupChunkObjRead", "tputDedupChunkObjRead", false),
                a("tputDedupChunkObjWrite", "tputDedupChunkObjWrite", false),
                a("tputDedupEngineRead", "tputDedupEngineRead", false),
                a("tputDedupEngineWrite", "tputDedupEngineWrite", false),
                a("tputDedupHashObjRead", "tputDedupHashObjRead", false),
                a("tputDedupHashObjWrite", "tputDedupHashObjWrite", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("dedupChunkReadCongestion", "dedupChunkReadCongestion", false),
                a("dedupChunkRecoveryUnmapCongestion", "dedupChunkRecoveryUnmapCongestion", false),
                a("dedupChunkRecoveryWriteCongestion", "dedupChunkRecoveryWriteCongestion", false),
                a("dedupChunkResyncReadCongestion", "dedupChunkResyncReadCongestion", false),
                a("dedupChunkUnmapCongestion", "dedupChunkUnmapCongestion", false),
                a("dedupChunkUnmapCount", "dedupChunkUnmapCount", false),
                a("dedupChunkWriteCongestion", "dedupChunkWriteCongestion", false),
                a("dedupEngineReadCongestion", "dedupEngineReadCongestion", false),
                a("dedupEngineWriteCongestion", "dedupEngineWriteCongestion", false),
                a("dedupHashReadCongestion", "dedupHashReadCongestion", false),
                a("dedupHashRecoveryUnmapCongestion", "dedupHashRecoveryUnmapCongestion", false),
                a("dedupHashRecoveryWriteCongestion", "dedupHashRecoveryWriteCongestion", false),
                a("dedupHashResyncReadCongestion", "dedupHashResyncReadCongestion", false),
                a("dedupHashUnmapCongestion", "dedupHashUnmapCongestion", false),
                a("dedupHashUnmapCount", "dedupHashUnmapCount", false),
                a("dedupHashWriteCongestion", "dedupHashWriteCongestion", false),
                a("iopsDedupChunkObjUnmap", "iopsDedupChunkObjUnmap", false),
                a("iopsDedupHashObjUnmap", "iopsDedupHashObjUnmap", false))),

    HOST_ESA_DEDUP_DOMOWNER_IO("host-esa-dedup-domowner-io", Tier.HOST, Target.HOST, "ESA Dedup DOM Owner", IdForm.ONE, F.ALL_ONLY, list(
                a("dedupChunkReadCount", "dedupChunkReadCount", false),
                a("dedupChunkWriteCount", "dedupChunkWriteCount", false),
                a("dedupEngineReadCount", "dedupEngineReadCount", false),
                a("dedupEngineWriteCount", "dedupEngineWriteCount", false),
                a("dedupHashReadCount", "dedupHashReadCount", false),
                a("dedupHashWriteCount", "dedupHashWriteCount", false),
                a("iopsDedupChunkObjRead", "iopsDedupChunkObjRead", false),
                a("iopsDedupChunkObjWrite", "iopsDedupChunkObjWrite", false),
                a("iopsDedupEngineRead", "iopsDedupEngineRead", false),
                a("iopsDedupEngineWrite", "iopsDedupEngineWrite", false),
                a("iopsDedupHashObjRead", "iopsDedupHashObjRead", false),
                a("iopsDedupHashObjWrite", "iopsDedupHashObjWrite", false),
                a("latencyAvgDedupChunkObjRead", "latencyAvgDedupChunkObjReadUs", false),
                a("latencyAvgDedupChunkObjWrite", "latencyAvgDedupChunkObjWriteUs", false),
                a("latencyAvgDedupEngineRead", "latencyAvgDedupEngineReadUs", false),
                a("latencyAvgDedupEngineWrite", "latencyAvgDedupEngineWriteUs", false),
                a("latencyAvgDedupHashObjRead", "latencyAvgDedupHashObjReadUs", false),
                a("latencyAvgDedupHashObjWrite", "latencyAvgDedupHashObjWriteUs", false),
                a("tputDedupChunkObjRead", "tputDedupChunkObjRead", false),
                a("tputDedupChunkObjWrite", "tputDedupChunkObjWrite", false),
                a("tputDedupEngineRead", "tputDedupEngineRead", false),
                a("tputDedupEngineWrite", "tputDedupEngineWrite", false),
                a("tputDedupHashObjRead", "tputDedupHashObjRead", false),
                a("tputDedupHashObjWrite", "tputDedupHashObjWrite", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("dedupChunkReadCongestion", "dedupChunkReadCongestion", false),
                a("dedupChunkRecoveryUnmapCongestion", "dedupChunkRecoveryUnmapCongestion", false),
                a("dedupChunkRecoveryWriteCongestion", "dedupChunkRecoveryWriteCongestion", false),
                a("dedupChunkResyncReadCongestion", "dedupChunkResyncReadCongestion", false),
                a("dedupChunkUnmapCongestion", "dedupChunkUnmapCongestion", false),
                a("dedupChunkUnmapCount", "dedupChunkUnmapCount", false),
                a("dedupChunkWriteCongestion", "dedupChunkWriteCongestion", false),
                a("dedupEngineReadCongestion", "dedupEngineReadCongestion", false),
                a("dedupEngineWriteCongestion", "dedupEngineWriteCongestion", false),
                a("dedupHashReadCongestion", "dedupHashReadCongestion", false),
                a("dedupHashRecoveryUnmapCongestion", "dedupHashRecoveryUnmapCongestion", false),
                a("dedupHashRecoveryWriteCongestion", "dedupHashRecoveryWriteCongestion", false),
                a("dedupHashResyncReadCongestion", "dedupHashResyncReadCongestion", false),
                a("dedupHashUnmapCongestion", "dedupHashUnmapCongestion", false),
                a("dedupHashUnmapCount", "dedupHashUnmapCount", false),
                a("dedupHashWriteCongestion", "dedupHashWriteCongestion", false),
                a("iopsDedupChunkObjUnmap", "iopsDedupChunkObjUnmap", false),
                a("iopsDedupHashObjUnmap", "iopsDedupHashObjUnmap", false),
                a("dedupChunkReadLeafOwnerCountLocal", "dedupChunkReadLeafOwnerCountLocal", false),
                a("dedupChunkReadLeafOwnerCountRemote", "dedupChunkReadLeafOwnerCountRemote", false),
                a("dedupChunkWriteLeafOwnerCountLocal", "dedupChunkWriteLeafOwnerCountLocal", false),
                a("dedupChunkWriteLeafOwnerCountRemote", "dedupChunkWriteLeafOwnerCountRemote", false),
                a("dedupHashReadLeafOwnerCountLocal", "dedupHashReadLeafOwnerCountLocal", false),
                a("dedupHashReadLeafOwnerCountRemote", "dedupHashReadLeafOwnerCountRemote", false),
                a("dedupHashWriteLeafOwnerCountLocal", "dedupHashWriteLeafOwnerCountLocal", false),
                a("dedupHashWriteLeafOwnerCountRemote", "dedupHashWriteLeafOwnerCountRemote", false),
                a("iopsDedupChunkReadLeafOwnerLocal", "iopsDedupChunkReadLeafOwnerLocal", false),
                a("iopsDedupChunkReadLeafOwnerRemote", "iopsDedupChunkReadLeafOwnerRemote", false),
                a("iopsDedupChunkWriteLeafOwnerLocal", "iopsDedupChunkWriteLeafOwnerLocal", false),
                a("iopsDedupChunkWriteLeafOwnerRemote", "iopsDedupChunkWriteLeafOwnerRemote", false),
                a("iopsDedupHashReadLeafOwnerLocal", "iopsDedupHashReadLeafOwnerLocal", false),
                a("iopsDedupHashReadLeafOwnerRemote", "iopsDedupHashReadLeafOwnerRemote", false),
                a("iopsDedupHashWriteLeafOwnerLocal", "iopsDedupHashWriteLeafOwnerLocal", false),
                a("iopsDedupHashWriteLeafOwnerRemote", "iopsDedupHashWriteLeafOwnerRemote", false),
                a("latencyAvgDedupChunkReadLeafOwnerLocal", "latencyAvgDedupChunkReadLeafOwnerLocal", false),
                a("latencyAvgDedupChunkReadLeafOwnerRemote", "latencyAvgDedupChunkReadLeafOwnerRemote", false),
                a("latencyAvgDedupChunkWriteLeafOwnerLocal", "latencyAvgDedupChunkWriteLeafOwnerLocal", false),
                a("latencyAvgDedupChunkWriteLeafOwnerRemote", "latencyAvgDedupChunkWriteLeafOwnerRemote", false),
                a("latencyAvgDedupHashReadLeafOwnerLocal", "latencyAvgDedupHashReadLeafOwnerLocal", false),
                a("latencyAvgDedupHashReadLeafOwnerRemote", "latencyAvgDedupHashReadLeafOwnerRemote", false),
                a("latencyAvgDedupHashWriteLeafOwnerLocal", "latencyAvgDedupHashWriteLeafOwnerLocal", false),
                a("latencyAvgDedupHashWriteLeafOwnerRemote", "latencyAvgDedupHashWriteLeafOwnerRemote", false),
                a("tputDedupChunkReadLeafCompLocal", "tputDedupChunkReadLeafCompLocal", false),
                a("tputDedupChunkReadLeafCompRemote", "tputDedupChunkReadLeafCompRemote", false),
                a("tputDedupChunkReadLeafOwnerLocal", "tputDedupChunkReadLeafOwnerLocal", false),
                a("tputDedupChunkReadLeafOwnerRemote", "tputDedupChunkReadLeafOwnerRemote", false),
                a("tputDedupChunkWriteLeafCompLocal", "tputDedupChunkWriteLeafCompLocal", false),
                a("tputDedupChunkWriteLeafCompRemote", "tputDedupChunkWriteLeafCompRemote", false),
                a("tputDedupChunkWriteLeafOwnerLocal", "tputDedupChunkWriteLeafOwnerLocal", false),
                a("tputDedupChunkWriteLeafOwnerRemote", "tputDedupChunkWriteLeafOwnerRemote", false),
                a("tputDedupHashReadLeafCompLocal", "tputDedupHashReadLeafCompLocal", false),
                a("tputDedupHashReadLeafCompRemote", "tputDedupHashReadLeafCompRemote", false),
                a("tputDedupHashReadLeafOwnerLocal", "tputDedupHashReadLeafOwnerLocal", false),
                a("tputDedupHashReadLeafOwnerRemote", "tputDedupHashReadLeafOwnerRemote", false),
                a("tputDedupHashWriteLeafCompLocal", "tputDedupHashWriteLeafCompLocal", false),
                a("tputDedupHashWriteLeafCompRemote", "tputDedupHashWriteLeafCompRemote", false),
                a("tputDedupHashWriteLeafOwnerLocal", "tputDedupHashWriteLeafOwnerLocal", false),
                a("tputDedupHashWriteLeafOwnerRemote", "tputDedupHashWriteLeafOwnerRemote", false))),

    HOST_ESA_DEDUP_STORE_CHUNKSVC("host-esa-dedup-store-chunksvc", Tier.HOST, Target.HOST, "ESA Dedup Chunk Service", IdForm.ONE, F.ALL_ONLY, list(
                a("latencyChangeRefCount", "latencyChangeRefCountUs", false),
                a("latencyDeleteChunkExtent", "latencyDeleteChunkExtentUs", false),
                a("latencyDeletedObjCleanup", "latencyDeletedObjCleanupUs", false),
                a("latencyGetChunkEntries", "latencyGetChunkEntriesUs", false),
                a("latencyReserveMba", "latencyReserveMbaUs", false),
                a("numChangeRefCount", "numChangeRefCount", false),
                a("numDeleteChunkExtent", "numDeleteChunkExtent", false),
                a("numDeletedObjCleanup", "numDeletedObjCleanup", false),
                a("numGetChunkEntries", "numGetChunkEntries", false),
                a("numObjsReported", "numObjsReported", false),
                a("numReserveMba", "numReserveMba", false),
                a("rateBlocksInChunkDeleted", "rateBlocksInChunkDeleted", false),
                a("rateBlocksInChunkWritten", "rateBlocksInChunkWritten", false),
                a("rateChangeRefCount", "rateChangeRefCount", false),
                a("rateChunkRefEntAdded", "rateChunkRefEntAdded", false),
                a("rateChunkRefEntRemoved", "rateChunkRefEntRemoved", false),
                a("rateChunksDeleted", "rateChunksDeleted", false),
                a("rateChunksInChangeRefCount", "rateChunksInChangeRefCount", false),
                a("rateChunksInDeleteChunkExtent", "rateChunksInDeleteChunkExtent", false),
                a("rateChunksInGetChunkEntries", "rateChunksInGetChunkEntries", false),
                a("rateChunksWritten", "rateChunksWritten", false),
                a("rateDeleteChunkExtent", "rateDeleteChunkExtent", false),
                a("rateDeletedObjCleanup", "rateDeletedObjCleanup", false),
                a("rateGetChunkEntries", "rateGetChunkEntries", false),
                a("rateReserveMba", "rateReserveMba", false),
                a("totalNumBlks", "totalNumBlks", false),
                a("totalNumRefCnt", "totalNumRefCnt", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("rateAsyncTaskBatches", "rateAsyncTaskBatches", false),
                a("rateChunksOpportunisticDelete", "rateChunksOpportunisticDelete", false),
                a("rateChunksRetained", "rateChunksRetained", false),
                a("rateChunksToDelete", "rateChunksToDelete", false),
                a("totalNumBytesRetained", "totalNumBytesRetained", false),
                a("totalTputRetained", "totalTputRetained", false))),

    HOST_ESA_DEDUP_STORE_HASHSVC("host-esa-dedup-store-hashsvc", Tier.HOST, Target.HOST, "ESA Dedup Hash Service", IdForm.ONE, F.ALL_ONLY, list(
                a("currentGenNum", "currentGenNum", false),
                a("currentStaleGenNum", "currentStaleGenNum", false),
                a("latencyDeleteChunksReq", "latencyDeleteChunksReqUs", false),
                a("latencyNewDupReq", "latencyNewDupReqUs", false),
                a("latencyReadLockWait", "latencyReadLockWaitUs", false),
                a("latencyUniqueHashReq", "latencyUniqueHashReqUs", false),
                a("latencyWriteLockWait", "latencyWriteLockWaitUs", false),
                a("numDeleteChunksReq", "numDeleteChunksReq", false),
                a("numNewDupReq", "numNewDupReq", false),
                a("numObjsReported", "numObjsReported", false),
                a("numReadLockWait", "numReadLockWait", false),
                a("numUniqHashReq", "numUniqHashReq", false),
                a("numWriteLockWait", "numWriteLockWait", false),
                a("rateChunkInDeleteChunksReq", "rateChunkInDeleteChunksReq", false),
                a("rateChunkInNewDupReq", "rateChunkInNewDupReq", false),
                a("rateChunkInUniqHashReq", "rateChunkInUniqHashReq", false),
                a("rateDedupHashEntryAdded", "rateDedupHashEntryAdded", false),
                a("rateDedupHashEntryRemoved", "rateDedupHashEntryRemoved", false),
                a("rateDedupHashEntryUpdated", "rateDedupHashEntryUpdated", false),
                a("rateDeleteChunksReq", "rateDeleteChunksReq", false),
                a("rateDeletedChunks", "rateDeletedChunks", false),
                a("rateHashKeyRemovalEntriesPerGen", "rateHashKeyRemovalEntriesPerGen", false),
                a("rateHashKeyRemovalEntriesToScanInStep", "rateHashKeyRemovalEntriesToScanInStep", false),
                a("rateHashKeyRemovalStepsDone", "rateHashKeyRemovalStepsDone", false),
                a("rateNewDupChunks", "rateNewDupChunks", false),
                a("rateNewDupReq", "rateNewDupReq", false),
                a("rateOldDupChunks", "rateOldDupChunks", false),
                a("rateReadLockWait", "rateReadLockWait", false),
                a("rateRetryChunks", "rateRetryChunks", false),
                a("rateSentriesAdded", "rateSentriesAdded", false),
                a("rateSentriesRemoved", "rateSentriesRemoved", false),
                a("rateUniqChunks", "rateUniqChunks", false),
                a("rateUniqHashReq", "rateUniqHashReq", false),
                a("rateWriteLockWait", "rateWriteLockWait", false),
                // P3 (v3 addendum Q3): served on 9.1.1 beyond the Python model; units unknown, no suffix
                a("capBytesPerUnitPerHashObj", "capBytesPerUnitPerHashObj", false),
                a("rateDupEntries", "rateDupEntries", false),
                a("rateLiveUniqEntries", "rateLiveUniqEntries", false),
                a("rateStaleUniqEntries", "rateStaleUniqEntries", false),
                a("rateUniqEntriesPerUnit", "rateUniqEntriesPerUnit", false))),

    HOST_VSANSPARSE("host-vsansparse", Tier.HOST, Target.HOST, "vSAN Sparse", IdForm.ONE, F.ALL_ONLY, list(
                a("cacheAllocFails", "cacheAllocFails", false),
                a("cacheEntries", "cacheEntries", false),
                a("cacheEvictAttempts", "cacheEvictAttempts", false),
                a("cacheHitRate", "cacheHitRate", false),
                a("cacheInserts", "cacheInserts", false),
                a("cacheLockContentions", "cacheLockContentions", false),
                a("cacheRemoves", "cacheRemoves", false),
                a("cacheUpdateLatency", "cacheUpdateLatencyUs", false),
                a("iopsGwe", "iopsGwe", false),
                a("iopsRead", "iopsRead", false),
                a("iopsRmw", "iopsRmw", false),
                a("iopsWrite", "iopsWrite", false),
                a("iopsWriteConflicts", "iopsWriteConflicts", false),
                a("latencyCacheLookup", "latencyCacheLookupUs", false),
                a("latencyGwe", "latencyGweUs", false),
                a("latencyRead", "latencyReadUs", false),
                a("latencyWrite", "latencyWriteUs", false),
                a("lruLockContentions", "lruLockContentions", false),
                a("readsToLayer", "readsToLayer", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false))),

    RDT_NET("rdt-net", Tier.HOST, Target.HOST, "RDT Network", IdForm.ONE, F.ALL_ONLY, list(
                a("checksumMismatchCount", "checksumMismatchCount", false))),

    SYSTEM_MEM("system-mem", Tier.HOST, Target.HOST, "System Memory", IdForm.ONE, F.ALL_ONLY, list(
                a("overcommitRatio", "overcommitRatioPct", false),
                a("pctMemUsed", "pctMemUsed", false),
                a("totalMbMemUsed", "totalMbMemUsed", false))),

    LSOM_WORLD_CPU("lsom-world-cpu", Tier.HOST, Target.HOST, "LSOM World", IdForm.WORLD, F.ALL_ONLY, list(
                a("readyPct", "readyPct", false),
                a("usedPct", "usedPct", false))),

    HEAP_MEMORY("heap-memory", Tier.HOST, Target.HOST, "Heap", IdForm.TWO, F.ALL_ONLY | F.STRIKES, list(
                a("heapUtil", "heapUtilPct", false))),

    SLAB_MEMORY("slab-memory", Tier.HOST, Target.HOST, "Slab", IdForm.TWO, F.ALL_ONLY | F.STRIKES, list(
                a("slabUtil", "slabUtilPct", false))),

    CLOM_HOST("clom-host", Tier.HOST, Target.HOST, "CLOM", IdForm.ONE, F.ALL_ONLY | F.STRIKES, list(
                a("nodeCompCntRatio", "nodeCompCntRatio", false),
                a("nodeDataCompCntRatio", "nodeDataCompCntRatio", false))),

    CMMDS_WORKLOAD("cmmds-workload", Tier.HOST, Target.HOST, "CMMDS Workload", IdForm.ONE, F.ALL_ONLY | F.STRIKES, list(
                a("agentBatchesSent", "agentBatchesSent", false),
                a("checkPointBatchesSent", "checkPointBatchesSent", false),
                a("masterBatchesRecved", "masterBatchesRecved", false),
                a("rxAccept", "rxAccept", false),
                a("rxAgentUpdateRequest", "rxAgentUpdateRequest", false),
                a("rxHeartbeatRequest", "rxHeartbeatRequest", false),
                a("rxMasterUpdate", "rxMasterUpdate", false),
                a("rxMisc", "rxMisc", false),
                a("rxRetransmitRequest", "rxRetransmitRequest", false),
                a("rxSnapshot", "rxSnapshot", false),
                a("totalUpdInBatchCkpt", "totalUpdInBatchCkpt", false),
                a("totalUpdInMasterBatches", "totalUpdInMasterBatches", false),
                a("totalUpdSentInAgentBatch", "totalUpdSentInAgentBatch", false),
                a("txAgentUpdateRequest", "txAgentUpdateRequest", false),
                a("txHeartbeatRequest", "txHeartbeatRequest", false),
                a("txMasterCheckpoint", "txMasterCheckpoint", false),
                a("txMasterCkptTried", "txMasterCkptTried", false),
                a("txMasterUpdate", "txMasterUpdate", false),
                a("txMasterUpdateTried", "txMasterUpdateTried", false),
                a("txRetransmitRequest", "txRetransmitRequest", false),
                a("txSnapshot", "txSnapshot", false),
                a("txSnapshotBytes", "txSnapshotBytes", false),
                a("txSnapshotTried", "txSnapshotTried", false))),

    VSAN_DISTRIBUTION("vsan-distribution", Tier.HOST, Target.HOST, "Distribution", IdForm.ONE, F.ALL_ONLY, list(
                a("components", "components", false),
                a("domClients", "domClients", false),
                a("domColocated", "domColocated", false),
                a("domOwners", "domOwners", false),
                a("ioComponents", "ioComponents", false))),

    HOST_PMEM("host-pmem", Tier.HOST, Target.HOST, "PMem", IdForm.ONE, F.ALL_ONLY, list(
                a("bandwidthRead", "bandwidthReadBytesPerSec", false),
                a("bandwidthTotal", "bandwidthTotalBytesPerSec", false),
                a("bandwidthWrite", "bandwidthWriteBytesPerSec", false),
                a("iopsRead", "iopsRead", false),
                a("iopsTotal", "iopsTotal", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyRead", "latencyRead", false),
                a("latencyWrite", "latencyWrite", false))),

    HOST_REMOTEDOMCLIENT("host-remotedomclient", Tier.HOST, Target.HOST, "Remote DOM Client", IdForm.ONE, F.ALL_ONLY | F.REMOTE, list(
                a("congestion", "congestion", false),
                a("iopsRead", "iopsRead", false),
                a("iopsUnmap", "iopsUnmap", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyAvgRead", "latencyAvgReadUs", false),
                a("latencyAvgUnmap", "latencyAvgUnmapUs", false),
                a("latencyAvgWrite", "latencyAvgWriteUs", false),
                a("oio", "oio", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputUnmap", "throughputUnmapBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false))),

    COMPUTEHOST_REMOTEDOMCLIENT("computeHost-remotedomclient", Tier.HOST, Target.HOST, "Compute Remote DOM Client", IdForm.ONE, F.ALL_ONLY | F.REMOTE, list(
                a("congestion", "congestion", false),
                a("iopsRead", "iopsRead", false),
                a("iopsUnmap", "iopsUnmap", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyAvgRead", "latencyAvgReadUs", false),
                a("latencyAvgUnmap", "latencyAvgUnmapUs", false),
                a("latencyAvgWrite", "latencyAvgWriteUs", false),
                a("oio", "oio", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputUnmap", "throughputUnmapBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false))),

    VSAN_ISCSI_HOST("vsan-iscsi-host", Tier.HOST, Target.HOST, "iSCSI", IdForm.ONE, F.ALL_ONLY, list(
                a("bandwidthRead", "bandwidthReadBytesPerSec", false),
                a("bandwidthTotal", "bandwidthTotalBytesPerSec", false),
                a("bandwidthWrite", "bandwidthWriteBytesPerSec", false),
                a("iopsRead", "iopsRead", false),
                a("iopsTotal", "iopsTotal", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyRead", "latencyReadUs", false),
                a("latencyTotal", "latencyTotalUs", false),
                a("latencyWrite", "latencyWriteUs", false),
                a("queueDepth", "queueDepth", false))),

    VSAN_ISCSI_TARGET("vsan-iscsi-target", Tier.HOST, Target.HOST, "iSCSI Target", IdForm.TWO, F.ALL_ONLY, list(
                a("bandwidthRead", "bandwidthReadBytesPerSec", false),
                a("bandwidthTotal", "bandwidthTotalBytesPerSec", false),
                a("bandwidthWrite", "bandwidthWriteBytesPerSec", false),
                a("iopsRead", "iopsRead", false),
                a("iopsTotal", "iopsTotal", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyRead", "latencyReadUs", false),
                a("latencyTotal", "latencyTotalUs", false),
                a("latencyWrite", "latencyWriteUs", false),
                a("queueDepth", "queueDepth", false))),

    VSAN_ISCSI_LUN("vsan-iscsi-lun", Tier.HOST, Target.HOST, "iSCSI LUN", IdForm.LUN, F.ALL_ONLY, list(
                a("bandwidthRead", "bandwidthReadBytesPerSec", false),
                a("bandwidthTotal", "bandwidthTotalBytesPerSec", false),
                a("bandwidthWrite", "bandwidthWriteBytesPerSec", false),
                a("iopsRead", "iopsRead", false),
                a("iopsTotal", "iopsTotal", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyRead", "latencyReadUs", false),
                a("latencyTotal", "latencyTotalUs", false),
                a("latencyWrite", "latencyWriteUs", false),
                a("queueDepth", "queueDepth", false))),

    HOST_CPU("host-cpu", Tier.HOST, Target.HOST, "pCPU", IdForm.ONE_OR_TWO, F.ALL_ONLY, list(
                a("coreUtilPct", "coreUtilPct", false),
                a("pcpuUsedPct", "pcpuUsedPct", false),
                a("pcpuUtilPct", "pcpuUtilPct", false))),

    DOM_WORLD_CPU("dom-world-cpu", Tier.HOST, Target.HOST, "DOM World", IdForm.WORLD, F.ALL_ONLY, list(
                a("readyPct", "readyPct", false),
                a("runPct", "runPct", false),
                a("usedPct", "usedPct", false))),

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
                m("iopsWriteCapacity", "iopsWriteCapacity", false),
                a("avgLatReadPerf", "avgLatReadPerf", false),
                a("avgLatUnmapCapacity", "avgLatUnmapCapacity", false),
                a("avgLatUnmapPerf", "avgLatUnmapPerf", false),
                a("iopsReadPerf", "iopsReadPerf", false),
                a("iopsUnmapCapacity", "iopsUnmapCapacity", false),
                a("iopsUnmapPerf", "iopsUnmapPerf", false),
                a("iopsWritePerf", "iopsWritePerf", false),
                a("maxReadTimeCapacity", "maxReadTimeCapacity", false),
                a("maxReadTimePerf", "maxReadTimePerf", false),
                a("maxUnmapTimeCapacity", "maxUnmapTimeCapacity", false),
                a("maxUnmapTimePerf", "maxUnmapTimePerf", false),
                a("maxWriteTimeCapacity", "maxWriteTimeCapacity", false),
                a("maxWriteTimePerf", "maxWriteTimePerf", false),
                a("minReadTimeCapacity", "minReadTimeCapacity", false),
                a("minReadTimePerf", "minReadTimePerf", false),
                a("minUnmapTimeCapacity", "minUnmapTimeCapacity", false),
                a("minUnmapTimePerf", "minUnmapTimePerf", false),
                a("minWriteTimeCapacity", "minWriteTimeCapacity", false),
                a("minWriteTimePerf", "minWriteTimePerf", false),
                a("readCountCapacity", "readCountCapacity", false),
                a("readCountPerf", "readCountPerf", false),
                a("tputReadCapacity", "tputReadCapacity", false),
                a("tputReadDiskCapacity", "tputReadDiskCapacity", false),
                a("tputReadDiskPerf", "tputReadDiskPerf", false),
                a("tputReadPerf", "tputReadPerf", false),
                a("tputUnmapCapacity", "tputUnmapCapacity", false),
                a("tputUnmapPerf", "tputUnmapPerf", false),
                a("tputWriteCapacity", "tputWriteCapacity", false),
                a("tputWritePerf", "tputWritePerf", false),
                a("unmapCountCapacity", "unmapCountCapacity", false),
                a("unmapCountPerf", "unmapCountPerf", false),
                a("writeCountCapacity", "writeCountCapacity", false),
                a("writeCountPerf", "writeCountPerf", false))),

    VSAN_ESA_DISK_SCSIFW("vsan-esa-disk-scsifw", Tier.DISK, Target.DISK, "ESA Disk SCSI", IdForm.SELF, list(
                m("latencyDevDAvg", "latencyDevDAvgUs", false),
                m("latencyDevGAvg", "latencyDevGAvgUs", false),
                m("latencyDevKAvg", "latencyDevKAvgUs", false),
                m("latencyDevRead", "latencyDevReadUs", false),
                m("latencyDevWrite", "latencyDevWriteUs", false),
                a("ioCountDevRead", "ioCountDevRead", false),
                a("ioCountDevUnmap", "ioCountDevUnmap", false),
                a("ioCountDevWrite", "ioCountDevWrite", false),
                a("iopsDevRead", "iopsDevRead", false),
                a("iopsDevUnmap", "iopsDevUnmap", false),
                a("iopsDevWrite", "iopsDevWrite", false),
                a("latencyDevUnmap", "latencyDevUnmapUs", false),
                a("outstandingCmdCount", "outstandingCmdCount", false),
                a("throughputDevRead", "throughputDevReadBytesPerSec", false),
                a("throughputDevUnmap", "throughputDevUnmapBytesPerSec", false),
                a("throughputDevWrite", "throughputDevWriteBytesPerSec", false))),

    // ---- Build 3 (v3 "New types"), metric_scope=all only: disk tier ----

    DISK_GROUP("disk-group", Tier.DISK, Target.DISK, "Disk Group", IdForm.SELF, F.ALL_ONLY, list(
                a("componentCongestionReadSched", "componentCongestionReadSched", false),
                a("componentCongestionWriteSched", "componentCongestionWriteSched", false),
                a("diskgroupCongestionReadSched", "diskgroupCongestionReadSched", false),
                a("diskgroupCongestionWriteSched", "diskgroupCongestionWriteSched", false),
                a("iopsDelayPctSched", "iopsDelayPctSched", false),
                a("iopsDirectSched", "iopsDirectSched", false),
                a("iopsResyncRead", "iopsResyncRead", false),
                a("iopsResyncReadDecom", "iopsResyncReadDecom", false),
                a("iopsResyncReadFixComp", "iopsResyncReadFixComp", false),
                a("iopsResyncReadPolicy", "iopsResyncReadPolicy", false),
                a("iopsResyncReadRebalance", "iopsResyncReadRebalance", false),
                a("iopsResyncWrite", "iopsResyncWrite", false),
                a("iopsResyncWriteDecom", "iopsResyncWriteDecom", false),
                a("iopsResyncWriteFixComp", "iopsResyncWriteFixComp", false),
                a("iopsResyncWritePolicy", "iopsResyncWritePolicy", false),
                a("iopsResyncWriteRebalance", "iopsResyncWriteRebalance", false),
                a("latResyncRead", "latResyncReadUs", false),
                a("latResyncReadDecom", "latResyncReadDecomUs", false),
                a("latResyncReadFixComp", "latResyncReadFixCompUs", false),
                a("latResyncReadPolicy", "latResyncReadPolicyUs", false),
                a("latResyncReadRebalance", "latResyncReadRebalanceUs", false),
                a("latResyncWrite", "latResyncWriteUs", false),
                a("latResyncWriteDecom", "latResyncWriteDecomUs", false),
                a("latResyncWriteFixComp", "latResyncWriteFixCompUs", false),
                a("latResyncWritePolicy", "latResyncWritePolicyUs", false),
                a("latResyncWriteRebalance", "latResyncWriteRebalanceUs", false),
                a("latencySched", "latencySchedUs", false),
                a("outstandingBytesSched", "outstandingBytesSched", false),
                a("tputResyncRead", "tputResyncReadBytesPerSec", false),
                a("tputResyncReadDecom", "tputResyncReadDecomBytesPerSec", false),
                a("tputResyncReadFixComp", "tputResyncReadFixCompBytesPerSec", false),
                a("tputResyncReadPolicy", "tputResyncReadPolicyBytesPerSec", false),
                a("tputResyncReadRebalance", "tputResyncReadRebalanceBytesPerSec", false),
                a("tputResyncWrite", "tputResyncWriteBytesPerSec", false),
                a("tputResyncWriteDecom", "tputResyncWriteDecomBytesPerSec", false),
                a("tputResyncWriteFixComp", "tputResyncWriteFixCompBytesPerSec", false),
                a("tputResyncWritePolicy", "tputResyncWritePolicyBytesPerSec", false),
                a("tputResyncWriteRebalance", "tputResyncWriteRebalanceBytesPerSec", false))),

    CLOM_DISK("clom-disk", Tier.DISK, Target.DISK, "CLOM Disk", IdForm.SELF, F.ALL_ONLY | F.STRIKES, list(
                a("diskCompCntRatio", "diskCompCntRatio", false),
                a("diskDataCompCntRatio", "diskDataCompCntRatio", false),
                a("diskgrpDataCompCntRatio", "diskgrpDataCompCntRatio", false))),

    DDH_DISK("ddh-disk", Tier.DISK, Target.DISK, "Disk Health", IdForm.SELF, F.ALL_ONLY | F.STRIKES, list(
                a("avgReadIOPS", "avgReadIOPS", false),
                a("avgReadLatency", "avgReadLatency", false),
                a("avgReadTPut", "avgReadTPutBytesPerSec", false),
                a("avgUnmapIOPS", "avgUnmapIOPS", false),
                a("avgUnmapLatency", "avgUnmapLatencyUs", false),
                a("avgUnmapTPut", "avgUnmapTPut", false),
                a("avgWriteIOPS", "avgWriteIOPS", false),
                a("avgWriteLatency", "avgWriteLatency", false),
                a("avgWriteTPut", "avgWriteTPut", false),
                a("logCongestion", "logCongestion", false))),

    // ---- VM tier (v2 "VM tier: 3 types, 21 keys"), on VirtualMachine ----

    VIRTUAL_MACHINE("virtual-machine", Tier.VM, Target.VM, "VM", IdForm.ONE, list(
                m("iopsRead", "iopsRead", false),
                m("iopsWrite", "iopsWrite", false),
                m("latencyRead", "latencyReadUs", false),
                m("latencyWrite", "latencyWriteUs", false),
                m("throughputRead", "throughputReadBytesPerSec", false),
                m("throughputWrite", "throughputWriteBytesPerSec", false),
                a("readCount", "readCount", false),
                a("writeCount", "writeCount", false))),

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
                m("writeCount", "writeCount", false))),

    // ---- Build 3 (v3 "New types"), metric_scope=all only: VM tier ----

    REMOTE_VIRTUAL_MACHINE("remote-virtual-machine", Tier.VM, Target.VM, "Remote VM", IdForm.ONE, F.ALL_ONLY | F.REMOTE | F.STRIKES, list(
                a("iopsRead", "iopsRead", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyRead", "latencyReadUs", false),
                a("latencyWrite", "latencyWriteUs", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false))),

    REMOTE_VIRTUAL_DISK("remote-virtual-disk", Tier.VM, Target.VM, "Remote Virtual Disk", IdForm.REMOTE_PATH, F.ALL_ONLY | F.REMOTE | F.STRIKES, list(
                a("NIOPS", "NIOPS", false),
                a("NIOPSDelayed", "NIOPSDelayed", false),
                a("iopsLimit", "iopsLimit", false))),

    REMOTE_VSCSI("remote-vscsi", Tier.VM, Target.VM, "Remote vSCSI", IdForm.TWO, F.ALL_ONLY | F.REMOTE | F.STRIKES, list(
                a("iopsRead", "iopsRead", false),
                a("iopsWrite", "iopsWrite", false),
                a("latencyRead", "latencyReadUs", false),
                a("latencyWrite", "latencyWriteUs", false),
                a("throughputRead", "throughputReadBytesPerSec", false),
                a("throughputWrite", "throughputWriteBytesPerSec", false)));

    /** Root segment of every pushed key (design Q2). */
    static final String ROOT = "vSAN Perf";

    /**
     * Which metrics an instance collects (v3 "Setting: metric_scope").
     * {@link #DASHBOARD}: the 451 keys over 33 types the Python pack's views
     * use, sent as the {@code labels} filter (Builds 1 and 2, unchanged).
     * {@link #ALL}: every catalog key of the 69 types (1,595), no
     * {@code labels} filter; labels outside the catalog are dropped and
     * counted.
     */
    enum Scope {
        DASHBOARD("dashboard"), ALL("all");

        final String label;

        Scope(String label) {
            this.label = label;
        }

        /** The setting's value, any case; {@code null} when it is neither. */
        static Scope parse(String raw) {
            for (Scope s : values()) {
                if (s.label.equalsIgnoreCase(raw)) return s;
            }
            return null;
        }
    }

    /** Constructor flags (a nested class, so the constant arguments may name them). */
    static final class F {
        private F() {}

        /** Build 3: the type is queried only under {@code metric_scope=all}. */
        static final int ALL_ONLY = 1;
        /**
         * HCI Mesh (v3 "Remote types"): lands only on an object of the queried
         * cluster (a node uuid of that cluster; its cluster uuid; a VM or vdisk
         * of its VM read), otherwise counted in {@code *_unresolved}.
         */
        static final int REMOTE = 2;
        /**
         * v3 Q5 (orchestrator decision 2026-11-07): the nine types that fault
         * {@code InvalidArgument: entityRefId} on a wildcard query on 9.1.1. A
         * type-unavailable fault is an optional call failure (counted in
         * {@code optional_calls_failed}) and the type is learned after
         * {@link LearnedTypes#FAULT_STRIKES} consecutive faults, with the
         * learned-empty re-probe spread, instead of at the first fault.
         */
        static final int STRIKES = 4;
    }

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
         * counted (v2 "Runtime landing"). A type whose group a host type
         * already uses never lands on a host ({@link #landsOnHost()}).
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
        /**
         * {@code <uuid>|<instance>}: pNIC, TCP/IP stack, vmknic (RDT), vSCSI
         * device; Build 3: heap, slab, iSCSI target.
         */
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
        PATH(1, -1, true),
        /**
         * Build 3, {@code host-cpu}: {@code <nodeUuid>|cpu-<n>} (instance
         * {@code cpu-<n>}) or {@code <nodeUuid>} alone, the host aggregate,
         * which has no instance (key {@code vSAN Perf|pCPU|<leaf>}).
         */
        ONE_OR_TWO(2, 1, false),
        /**
         * Build 3, {@code dom-world-cpu} and {@code lsom-world-cpu}:
         * {@code <nodeUuid>|<world name>|<world id>}, instance
         * {@code <world name>-<world id>}.
         */
        WORLD(3, 1, false),
        /**
         * Build 3, {@code vsan-iscsi-lun}: {@code <nodeUuid>|<target>|<lun>},
         * instance {@code <target>/<lun>}.
         */
        LUN(3, 1, false),
        /**
         * Build 3, {@code remote-virtual-disk}: as {@link #PATH}, but an id
         * that is not a path (it holds {@code |}) is kept whole, so the
         * device map lookup misses and it is counted in
         * {@code vdisks_unresolved} (v3 VM tier table), not as unparsed.
         */
        REMOTE_PATH(1, -1, true);

        /** Exact number of {@code |} parts (the maximum for {@link #ONE_OR_TWO}; paths: not split). */
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

        /** A datastore-path form: the instance is the bus position from the VM device read. */
        boolean isPath() {
            return this == PATH || this == REMOTE_PATH;
        }
    }

    /** One catalog row: the vSAN label, the Ops key leaf, the transform, the scope. */
    static final class Metric {
        final String label;
        final String leaf;
        /** The service reports this label per mille; pushed divided by 10. */
        final boolean perMille;
        /**
         * Collected under {@code metric_scope=dashboard} (the 451 released
         * keys); {@code false}: only under {@code all} (Build 3, v3 Appendix A).
         */
        final boolean dashboard;
        /**
         * Position in the type's catalog (the dedup mark slot); {@code -1}
         * outside a catalog. Dashboard rows come first, so a dashboard
         * position is the same under either scope.
         */
        final int position;

        Metric(String label, String leaf, boolean perMille) {
            this(label, leaf, perMille, true, -1);
        }

        private Metric(String label, String leaf, boolean perMille, boolean dashboard,
                int position) {
            this.label = label;
            this.leaf = leaf;
            this.perMille = perMille;
            this.dashboard = dashboard;
            this.position = position;
        }

        /** The pushed value for a raw service value. */
        double convert(double raw) {
            return perMille ? raw / 10.0 : raw;
        }

        boolean in(Scope scope) {
            return dashboard || scope == Scope.ALL;
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
    /** Build 3: queried only under {@code metric_scope=all} ({@link F#ALL_ONLY}). */
    final boolean allOnly;
    /** Build 3: an HCI Mesh type ({@link F#REMOTE}). */
    final boolean remote;
    /** Build 3, v3 Q5: learned after consecutive faults, counted as optional failures ({@link F#STRIKES}). */
    final boolean faultStrikes;
    private final Map<String, Metric> byLabel;
    /** Dashboard rows (the first {@code dashboardSize} positions). */
    private final int dashboardSize;

    EntityType(String id, Tier tier, Target target, String group, IdForm form,
            List<Metric> metrics) {
        this(id, tier, target, group, form, 0, metrics);
    }

    EntityType(String id, Tier tier, Target target, String group, IdForm form, int flags,
            List<Metric> metrics) {
        this.id = id;
        this.tier = tier;
        this.target = target;
        this.group = group;
        this.form = form;
        this.instanced = form.instancePart >= 0 || form.resolvedInstance;
        this.allOnly = (flags & F.ALL_ONLY) != 0;
        this.remote = (flags & F.REMOTE) != 0;
        this.faultStrikes = (flags & F.STRIKES) != 0;
        Map<String, Metric> m = new LinkedHashMap<>();
        int pos = 0;
        int dash = 0;
        boolean sawAll = false;
        for (Metric x : metrics) {
            if (x.dashboard) {
                if (sawAll || allOnly) {
                    throw new IllegalStateException(id + ": dashboard row " + x.label
                            + " after an all-scope row, or on an all-only type");
                }
                dash++;
            } else {
                sawAll = true;
            }
            if (m.put(x.label, new Metric(x.label, x.leaf, x.perMille, x.dashboard, pos++))
                    != null) {
                throw new IllegalStateException(id + ": duplicate label " + x.label);
            }
        }
        this.byLabel = Collections.unmodifiableMap(m);
        this.dashboardSize = dash;
    }

    /** A dashboard-scope row (Builds 1 and 2). */
    private static Metric m(String label, String leaf, boolean perMille) {
        return new Metric(label, leaf, perMille);
    }

    /** A row collected only under {@code metric_scope=all} (Build 3, v3 Appendix A). */
    private static Metric a(String label, String leaf, boolean perMille) {
        return new Metric(label, leaf, perMille, false, -1);
    }

    private static List<Metric> list(Metric... ms) {
        List<Metric> l = new ArrayList<>();
        Collections.addAll(l, ms);
        return l;
    }

    /** The dashboard-scope catalog row for a vSAN label, or {@code null} when not collected. */
    Metric metric(String label) {
        return metric(label, Scope.DASHBOARD);
    }

    /** The catalog row for a vSAN label under {@code scope}, or {@code null} when not collected. */
    Metric metric(String label, Scope scope) {
        Metric m = byLabel.get(label);
        return m != null && m.in(scope) ? m : null;
    }

    /** Every dashboard-scope catalog row, in table order. */
    List<Metric> metrics() {
        return metrics(Scope.DASHBOARD);
    }

    /** Every catalog row of {@code scope}, in table order (dashboard rows first). */
    List<Metric> metrics(Scope scope) {
        List<Metric> out = new ArrayList<>();
        for (Metric m : byLabel.values()) if (m.in(scope)) out.add(m);
        return out;
    }

    /** Number of dashboard-scope rows. */
    int size() {
        return dashboardSize;
    }

    /** Number of catalog rows of {@code scope} (the dedup mark array length). */
    int size(Scope scope) {
        return scope == Scope.ALL ? byLabel.size() : dashboardSize;
    }

    /** The dashboard {@code labels} filter: exactly the dashboard-scope catalog labels. */
    List<String> labels() {
        List<String> out = new ArrayList<>();
        for (Metric m : metrics(Scope.DASHBOARD)) out.add(m.label);
        return out;
    }

    /**
     * The {@code labels} array to send under {@code scope}, or {@code null}
     * for none (v3 C3: under {@code all} the filter is not sent).
     */
    List<String> requestLabels(Scope scope) {
        return scope == Scope.ALL ? null : labels();
    }

    /** The type is queried under {@code scope}. */
    boolean inScope(Scope scope) {
        return !allOnly || scope == Scope.ALL;
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
     * A landing type ({@link Target#CLUSTER_OR_HOST}) may put an entity on a
     * host: only when no host-side type uses its group, so two types never
     * write one key on a host (Build 3: {@code cluster-pmem} shares
     * {@code PMem} with {@code host-pmem} and lands on its cluster only).
     */
    boolean landsOnHost() {
        return target == Target.CLUSTER_OR_HOST && !HOST_GROUPS.contains(group);
    }

    /** Groups of the types whose keys land on HostSystem (host and disk targets). */
    private static final java.util.Set<String> HOST_GROUPS;

    static {
        java.util.Set<String> g = new java.util.HashSet<>();
        for (EntityType t : values()) {
            if (t.target == Target.HOST || t.target == Target.DISK) g.add(t.group);
        }
        HOST_GROUPS = Collections.unmodifiableSet(g);
    }

    /**
     * The Ops stat key: {@code vSAN Perf|<group>|<leaf>}, or
     * {@code vSAN Perf|<group>:<instance>|<leaf>} for an instanced type. An
     * instanced type's entity with no instance (the {@code host-cpu} host
     * aggregate) gets the plain group.
     */
    String statKey(String instance, Metric metric) {
        StringBuilder b = new StringBuilder(ROOT).append('|').append(group);
        if (instanced && instance != null) b.append(':').append(instance);
        return b.append('|').append(metric.leaf).toString();
    }

    /**
     * Wire cap for this type's 200 bodies (v2 Sizing requirement 3, streamed:
     * only the wire size is capped): 16 MB for the VM types, 4 MB otherwise.
     */
    long wireCap() {
        return tier == Tier.VM ? 16L * 1024 * 1024 : 4L * 1024 * 1024;
    }

    /**
     * Build 21 (Codex on PR #11): the type an operator names in a setting,
     * any case ({@code computecluster-remotedomclient} is
     * {@code computeCluster-remotedomclient}). The 69 ids differ by more than
     * case, so this is unambiguous; requests keep the exact {@link #id}.
     */
    static EntityType byIdIgnoreCase(String id) {
        for (EntityType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    /** The type with this {@code entityRefId} prefix (any of the 69), or {@code null}. */
    static EntityType byId(String id) {
        for (EntityType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    /** The dashboard-scope types of these tiers, in collection order. */
    static List<EntityType> ofTiers(java.util.Set<Tier> tiers) {
        return ofTiers(tiers, Scope.DASHBOARD);
    }

    /** The types of these tiers under {@code scope}, in collection order. */
    static List<EntityType> ofTiers(java.util.Set<Tier> tiers, Scope scope) {
        List<EntityType> out = new ArrayList<>();
        for (EntityType t : values()) {
            if (tiers.contains(t.tier) && t.inScope(scope)) out.add(t);
        }
        return out;
    }
}
