<!-- Copied verbatim from the factory knowledge/designs/vsanperf-mp-v2.md, sections "Cluster tier", "Host tier", "Disk tier" and "VM tier" of "Metric tables (390 keys)", 2026-11-07. See tests/fixtures/README.md. -->

### Cluster tier: 10 types, 189 keys (lands on ClusterComputeResource)

#### `vsan-cluster-capacity` (Python kind `VsanClusterCapacity`): 5 keys per cluster

vSAN datastore capacity and space efficiency for the cluster. `dedupRatio` is served as ratio x 100 (Python label "100 = 1.00x"; 145 on the lab) and pushed as served.

- Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 5 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `dedupRatio` | `vSAN Perf\|Capacity\|dedupRatio` | Space Efficiency Ratio (100 = 1.00x) | none stated | none; ratio x 100 (Python label: 100 = 1.00x); pushed as served |
| `free` | `vSAN Perf\|Capacity\|freeBytes` | Free Capacity | bytes | none |
| `savedByDedup` | `vSAN Perf\|Capacity\|savedByDedupBytes` | Saved Capacity | bytes | none |
| `total` | `vSAN Perf\|Capacity\|totalBytes` | Total Capacity | bytes | none |
| `used` | `vSAN Perf\|Capacity\|usedBytes` | Used Capacity | bytes | none |

#### `cluster-domclient` (Python kind `VsanClusterDomclient`): 11 keys per cluster

The cluster-wide total of the hosts' DOM Client role: the I/O the cluster's VMs issued, as the clients saw it (the host-level reading is in the v1 REFERENCE "DOM Client" text).

- Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 11 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `congestion` | `vSAN Perf\|DOM Client\|congestion` | Congestions | none stated | none |
| `iopsRead` | `vSAN Perf\|DOM Client\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|DOM Client\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyAvgRead` | `vSAN Perf\|DOM Client\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|DOM Client\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `oio` | `vSAN Perf\|DOM Client\|oio` | Outstanding IO | none stated | none |
| `readCongestion` | `vSAN Perf\|DOM Client\|readCongestion` | Read Congestions | none stated | none |
| `throughputRead` | `vSAN Perf\|DOM Client\|throughputReadBytesPerSec` | Read throughput | bytes/s | none |
| `throughputWrite` | `vSAN Perf\|DOM Client\|throughputWriteBytesPerSec` | Write throughput | bytes/s | none |
| `unmapCongestion` | `vSAN Perf\|DOM Client\|unmapCongestion` | Unmap congestion | none stated | none |
| `writeCongestion` | `vSAN Perf\|DOM Client\|writeCongestion` | Write Congestions | none stated | none |

#### `cluster-domcompmgr` (Python kind `VsanClusterDomcompmgr`): 12 keys per cluster

Cluster-level counterpart of the host DOM Comp Mgr group (component manager I/O, including recovery writes and resync reads per its labels).

- Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 12 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `congestion` | `vSAN Perf\|DOM Comp Mgr\|congestion` | Congestions | none stated | none |
| `iopsRead` | `vSAN Perf\|DOM Comp Mgr\|iopsRead` | Read IOPS | IO/s | none |
| `iopsRecWrite` | `vSAN Perf\|DOM Comp Mgr\|iopsRecWrite` | Recovery Write IOPS | IO/s | none |
| `iopsResyncRead` | `vSAN Perf\|DOM Comp Mgr\|iopsResyncRead` | Resync Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|DOM Comp Mgr\|iopsWrite` | Write IOPS | IO/s | none |
| `latAvgResyncRead` | `vSAN Perf\|DOM Comp Mgr\|latAvgResyncReadUs` | Resync Read Latency | microseconds | none |
| `latencyAvgRead` | `vSAN Perf\|DOM Comp Mgr\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgRecWrite` | `vSAN Perf\|DOM Comp Mgr\|latencyAvgRecWriteUs` | Recovery Write Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|DOM Comp Mgr\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `oio` | `vSAN Perf\|DOM Comp Mgr\|oio` | Outstanding IO | none stated | none |
| `throughputRecWrite` | `vSAN Perf\|DOM Comp Mgr\|throughputRecWriteBytesPerSec` | Recovery Write Throughput | bytes/s | none |
| `tputResyncRead` | `vSAN Perf\|DOM Comp Mgr\|tputResyncReadBytesPerSec` | Resync Read Throughput | bytes/s | none |

#### `cluster-domowner` (Python kind `VsanClusterDomowner`): 23 keys per cluster

The cluster-wide total of the hosts' DOM Owner role: owner-side I/O for every object in the cluster, whichever host's VMs issued it (the host-level reading is in the v1 REFERENCE "DOM Owner" text), plus the cluster's resync job counters.

- Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 23 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `avgResyncParallelism` | `vSAN Perf\|DOM Owner\|avgResyncParallelism` | Resync parallelism (average) | none stated | none |
| `congestion` | `vSAN Perf\|DOM Owner\|congestion` | Congestions | none stated | none |
| `iopsRead` | `vSAN Perf\|DOM Owner\|iopsRead` | Read IOPS | IO/s | none |
| `iopsRecWrite` | `vSAN Perf\|DOM Owner\|iopsRecWrite` | Recovery Write IOPS | IO/s | none |
| `iopsResyncRead` | `vSAN Perf\|DOM Owner\|iopsResyncRead` | Resync Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|DOM Owner\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyAvgRead` | `vSAN Perf\|DOM Owner\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|DOM Owner\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `numCompleteFullResyncOps` | `vSAN Perf\|DOM Owner\|numCompleteFullResyncOps` | Num complete full resync ops | none stated | none |
| `numCompleteResyncOps` | `vSAN Perf\|DOM Owner\|numCompleteResyncOps` | Num complete resync ops | none stated | none |
| `numInflightPriorityResyncJobs` | `vSAN Perf\|DOM Owner\|numInflightPriorityResyncJobs` | Num inflight priority resync jobs | none stated | none |
| `numInflightSharedResyncJobs` | `vSAN Perf\|DOM Owner\|numInflightSharedResyncJobs` | Num inflight shared resync jobs | none stated | none |
| `numPendingDecomResyncJobs` | `vSAN Perf\|DOM Owner\|numPendingDecomResyncJobs` | Num pending decom resync jobs | none stated | none |
| `numPendingFullResyncJobs` | `vSAN Perf\|DOM Owner\|numPendingFullResyncJobs` | Num pending full resync jobs | none stated | none |
| `numPendingResyncJobs` | `vSAN Perf\|DOM Owner\|numPendingResyncJobs` | Num pending resync jobs | none stated | none |
| `numRunningFullResyncJobs` | `vSAN Perf\|DOM Owner\|numRunningFullResyncJobs` | Num running full resync jobs | none stated | none |
| `numRunningResyncJobs` | `vSAN Perf\|DOM Owner\|numRunningResyncJobs` | Num running resync jobs | none stated | none |
| `numSuspendedResyncJobs` | `vSAN Perf\|DOM Owner\|numSuspendedResyncJobs` | Num suspended resync jobs | none stated | none |
| `oio` | `vSAN Perf\|DOM Owner\|oio` | Outstanding IO | none stated | none |
| `readCongestion` | `vSAN Perf\|DOM Owner\|readCongestion` | Read Congestions | none stated | none |
| `tputRead` | `vSAN Perf\|DOM Owner\|tputReadBytesPerSec` | Read Throughput | bytes/s | none |
| `tputWrite` | `vSAN Perf\|DOM Owner\|tputWriteBytesPerSec` | Write Throughput | bytes/s | none |
| `writeCongestion` | `vSAN Perf\|DOM Owner\|writeCongestion` | Write Congestions | none stated | none |

#### `cluster-esa-dedup-domclient-io` (Python kind `VsanClusterEsaDedupDomclientIo`): 24 keys per cluster

ESA global deduplication I/O seen from the DOM client (chunk, engine and hash object reads and writes). Live on the lab's 9.1.1 ESA cluster, although the Python pack carries it only in its provisional `perfsvc_model_extra.py`.

- Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 24 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `dedupChunkReadCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCount` | Deduplication Chunk Object Read IO Count | none stated | none |
| `dedupChunkWriteCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCount` | Deduplication Chunk Object Write IO Count | none stated | none |
| `dedupEngineReadCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCount` | Deduplication Read IO Count | none stated | none |
| `dedupEngineWriteCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCount` | Deduplication Write IO Count | none stated | none |
| `dedupHashReadCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCount` | Deduplication Hash Object Read IO Count | none stated | none |
| `dedupHashWriteCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCount` | Deduplication Hash Object Write IO Count | none stated | none |
| `iopsDedupChunkObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjRead` | Deduplication Chunk Object Read IOPS | IO/s | none |
| `iopsDedupChunkObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjWrite` | Deduplication Chunk Object Write IOPS | IO/s | none |
| `iopsDedupEngineRead` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineRead` | Deduplication Read IOPS | IO/s | none |
| `iopsDedupEngineWrite` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineWrite` | Deduplication Write IOPS | IO/s | none |
| `iopsDedupHashObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjRead` | Deduplication Hash Object Read IOPS | IO/s | none |
| `iopsDedupHashObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjWrite` | Deduplication Hash Object Write IOPS | IO/s | none |
| `latencyAvgDedupChunkObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjReadUs` | Deduplication Chunk Object Read Latency | microseconds | none |
| `latencyAvgDedupChunkObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjWriteUs` | Deduplication Chunk Object Write Latency | microseconds | none |
| `latencyAvgDedupEngineRead` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineReadUs` | Deduplication Read Latency | microseconds | none |
| `latencyAvgDedupEngineWrite` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineWriteUs` | Deduplication Write Latency | microseconds | none |
| `latencyAvgDedupHashObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjReadUs` | Deduplication Hash Object Read Latency | microseconds | none |
| `latencyAvgDedupHashObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjWriteUs` | Deduplication Hash Object Write Latency | microseconds | none |
| `tputDedupChunkObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjRead` | Deduplication Chunk Object Read Throughput | none stated | none |
| `tputDedupChunkObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjWrite` | Deduplication Chunk Object Write Throughput | none stated | none |
| `tputDedupEngineRead` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineRead` | Deduplication Read Throughput | none stated | none |
| `tputDedupEngineWrite` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineWrite` | Deduplication Write Throughput | none stated | none |
| `tputDedupHashObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjRead` | Deduplication Hash Object Read Throughput | none stated | none |
| `tputDedupHashObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjWrite` | Deduplication Hash Object Write Throughput | none stated | none |

#### `cluster-rdt-network-latency` (Python kind `VsanClusterRdtLatency`): 9 keys per cluster

Reliable Datagram Transport latency and socket-buffer figures for the cluster.

- Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 9 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `avgLatency` | `vSAN Perf\|RDT Latency\|avgLatencyUs` | RDT Network VNIC Average Latency | microseconds | none |
| `kaReset` | `vSAN Perf\|RDT Latency\|kaReset` | Keepalive reset | none stated | none |
| `maxLatency` | `vSAN Perf\|RDT Latency\|maxLatencyUs` | RDT Network Cluster Max Latency | microseconds | none |
| `minLatency` | `vSAN Perf\|RDT Latency\|minLatencyUs` | RDT Network Cluster Min Latency | microseconds | none |
| `numReadyDelay` | `vSAN Perf\|RDT Latency\|numReadyDelay` | Num ready delay | none stated | none |
| `rxSbSpaceMin` | `vSAN Perf\|RDT Latency\|rxSbSpaceMinBytes` | RDT Network Socket Min Inbound Bytes | bytes | none |
| `txCtxQMax` | `vSAN Perf\|RDT Latency\|txCtxQMaxBytes` | RDT Network Max Outbound Context Bytes | bytes | none |
| `txQLatAvg` | `vSAN Perf\|RDT Latency\|txQLatAvgUs` | RDT Network Average Outbound Queueing Latency | microseconds | none |
| `txSbSpaceMin` | `vSAN Perf\|RDT Latency\|txSbSpaceMinBytes` | RDT Network Socket Min Outbound Bytes | bytes | none |

#### `cluster-zdom-top-stats` (Python kind `VsanClusterZdomTopStats`): 7 keys per cluster

ESA zDOM top-level I/O for the cluster.

- Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 7 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `iopsRead` | `vSAN Perf\|ZDOM Top Stats\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|ZDOM Top Stats\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyAvgRead` | `vSAN Perf\|ZDOM Top Stats\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|ZDOM Top Stats\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `oio` | `vSAN Perf\|ZDOM Top Stats\|oio` | Outstanding IO | none stated | none |
| `throughputRead` | `vSAN Perf\|ZDOM Top Stats\|throughputReadBytesPerSec` | Read throughput | bytes/s | none |
| `throughputWrite` | `vSAN Perf\|ZDOM Top Stats\|throughputWriteBytesPerSec` | Write throughput | bytes/s | none |

#### `vsan-direct-cluster` (Python kind `VsanDirectCluster`): 8 keys per cluster

vSAN Direct. Empty on the lab (not configured); entity id form unobserved.

- Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time, see Identity). Instance: none. Lab: empty on the lab (wildcard echo).

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `iopsDevRead` | `vSAN Perf\|vSAN Direct Cluster\|iopsDevRead` | Physical-layer Read IOPS | IO/s | none |
| `iopsDevWrite` | `vSAN Perf\|vSAN Direct Cluster\|iopsDevWrite` | Physical-layer Write IOPS | IO/s | none |
| `latencyDevRead` | `vSAN Perf\|vSAN Direct Cluster\|latencyDevReadUs` | Physical-layer Read Latency | microseconds | none |
| `latencyDevWrite` | `vSAN Perf\|vSAN Direct Cluster\|latencyDevWriteUs` | Physical-layer Write Latency | microseconds | none |
| `oioDevRead` | `vSAN Perf\|vSAN Direct Cluster\|oioDevRead` | Read Outstanding IO | none stated | none |
| `oioDevWrite` | `vSAN Perf\|vSAN Direct Cluster\|oioDevWrite` | Write Outstanding IO | none stated | none |
| `throughputDevRead` | `vSAN Perf\|vSAN Direct Cluster\|throughputDevReadBytesPerSec` | Physical-layer Read Throughput | bytes/s | none |
| `throughputDevWrite` | `vSAN Perf\|vSAN Direct Cluster\|throughputDevWriteBytesPerSec` | Physical-layer Write Throughput | bytes/s | none |

#### `vsan-file-service` (Python kind `VsanFileService`): 8 keys per cluster

vSAN File Service. Empty on the lab (not enabled); entity id form unobserved.

- Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time). Instance: none. Lab: empty on the lab (wildcard echo).

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `readLatency` | `vSAN Perf\|File Service\|readLatencyUs` | Read latency | microseconds | none |
| `readOpTotal` | `vSAN Perf\|File Service\|readOpTotal` | IOPS for read operations | IO/s | none |
| `readRequested` | `vSAN Perf\|File Service\|readRequestedBytesPerSec` | Requested bytes for read | bytes/s | none |
| `readTransferred` | `vSAN Perf\|File Service\|readTransferredBytesPerSec` | Actual transferred bytes for read | bytes/s | none |
| `writeLatency` | `vSAN Perf\|File Service\|writeLatencyUs` | Write latency | microseconds | none |
| `writeOpTotal` | `vSAN Perf\|File Service\|writeOpTotal` | IOPS for write operations | IO/s | none |
| `writeRequested` | `vSAN Perf\|File Service\|writeRequestedBytesPerSec` | Requested bytes for write | bytes/s | none |
| `writeTransferred` | `vSAN Perf\|File Service\|writeTransferredBytesPerSec` | Actual transferred bytes for write | bytes/s | none |

#### `ioinsight` (Python kind `VsanIoinsight`): 82 keys per cluster

IOInsight capture results. Empty on the lab (no capture running); entity id form unobserved. 82 of the cluster tier's 189 keys.

- Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time). Instance: none. Lab: empty on the lab (wildcard echo).

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `aligned4kRatio` | `vSAN Perf\|IOInsight\|aligned4kRatio` | 4K Aligned IO | none stated | none |
| `aligned4kReadRatio` | `vSAN Perf\|IOInsight\|aligned4kReadRatio` | 4K Aligned Read IO | none stated | none |
| `aligned4kWriteRatio` | `vSAN Perf\|IOInsight\|aligned4kWriteRatio` | 4K Aligned Write IO | none stated | none |
| `iopsRead` | `vSAN Perf\|IOInsight\|iopsRead` | Read IOPS | IO/s | none |
| `iopsTotal` | `vSAN Perf\|IOInsight\|iopsTotal` | Total IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|IOInsight\|iopsWrite` | Write IOPS | IO/s | none |
| `randomRatio` | `vSAN Perf\|IOInsight\|randomRatio` | Random IO | none stated | none |
| `randomReadRatio` | `vSAN Perf\|IOInsight\|randomReadRatio` | Random Read IO | none stated | none |
| `randomWriteRatio` | `vSAN Perf\|IOInsight\|randomWriteRatio` | Random Write IO | none stated | none |
| `readRatio` | `vSAN Perf\|IOInsight\|readRatio` | Read IO | none stated | none |
| `riosz0_4k` | `vSAN Perf\|IOInsight\|riosz0_4k` | ReadSize<4K | none stated | none |
| `riosz128k` | `vSAN Perf\|IOInsight\|riosz128k` | ReadSize=128K | none stated | none |
| `riosz128k_256k` | `vSAN Perf\|IOInsight\|riosz128k_256k` | 128K<ReadSize<256K | none stated | none |
| `riosz16k` | `vSAN Perf\|IOInsight\|riosz16k` | ReadSize=16K | none stated | none |
| `riosz16k_32k` | `vSAN Perf\|IOInsight\|riosz16k_32k` | 16K<ReadSize<32K | none stated | none |
| `riosz1_m` | `vSAN Perf\|IOInsight\|riosz1_m` | ReadSize>1M | none stated | none |
| `riosz1m` | `vSAN Perf\|IOInsight\|riosz1m` | ReadSize=1M | none stated | none |
| `riosz256k` | `vSAN Perf\|IOInsight\|riosz256k` | ReadSize=256K | none stated | none |
| `riosz256k_512k` | `vSAN Perf\|IOInsight\|riosz256k_512k` | 256K<ReadSize<512K | none stated | none |
| `riosz32k` | `vSAN Perf\|IOInsight\|riosz32k` | ReadSize=32K | none stated | none |
| `riosz32k_64k` | `vSAN Perf\|IOInsight\|riosz32k_64k` | 32K<ReadSize<64K | none stated | none |
| `riosz4k` | `vSAN Perf\|IOInsight\|riosz4k` | ReadSize=4K | none stated | none |
| `riosz4k_8k` | `vSAN Perf\|IOInsight\|riosz4k_8k` | 4K<ReadSize<8K | none stated | none |
| `riosz512k` | `vSAN Perf\|IOInsight\|riosz512k` | ReadSize=512K | none stated | none |
| `riosz512k_1m` | `vSAN Perf\|IOInsight\|riosz512k_1m` | 512K<ReadSize<1M | none stated | none |
| `riosz64k` | `vSAN Perf\|IOInsight\|riosz64k` | ReadSize=64K | none stated | none |
| `riosz64k_128k` | `vSAN Perf\|IOInsight\|riosz64k_128k` | 64K<ReadSize<128K | none stated | none |
| `riosz8k` | `vSAN Perf\|IOInsight\|riosz8k` | ReadSize=8K | none stated | none |
| `riosz8k_16k` | `vSAN Perf\|IOInsight\|riosz8k_16k` | 8K<ReadSize<16K | none stated | none |
| `rlat0_1us` | `vSAN Perf\|IOInsight\|rlat0_1us` | 0<ReadLatency<1(us) | none stated | none |
| `rlat100_500us` | `vSAN Perf\|IOInsight\|rlat100_500us` | 100<ReadLatency<500(us) | none stated | none |
| `rlat100_ms` | `vSAN Perf\|IOInsight\|rlat100_ms` | ReadLatency>100(ms) | none stated | none |
| `rlat10_100us` | `vSAN Perf\|IOInsight\|rlat10_100us` | 10<ReadLatency<100(us) | none stated | none |
| `rlat10_25ms` | `vSAN Perf\|IOInsight\|rlat10_25ms` | 10<ReadLatency<25(ms) | none stated | none |
| `rlat1_10us` | `vSAN Perf\|IOInsight\|rlat1_10us` | 1<ReadLatency<10(us) | none stated | none |
| `rlat1_5ms` | `vSAN Perf\|IOInsight\|rlat1_5ms` | 1<ReadLatency<5(ms) | none stated | none |
| `rlat25_50ms` | `vSAN Perf\|IOInsight\|rlat25_50ms` | 25<ReadLatency<50(ms) | none stated | none |
| `rlat500us_1ms` | `vSAN Perf\|IOInsight\|rlat500us_1ms` | 500us<ReadLatency<1ms | none stated | none |
| `rlat50_100ms` | `vSAN Perf\|IOInsight\|rlat50_100ms` | 50<ReadLatency<100(ms) | none stated | none |
| `rlat5_10ms` | `vSAN Perf\|IOInsight\|rlat5_10ms` | 5<ReadLatency<10(ms) | none stated | none |
| `sequentialRatio` | `vSAN Perf\|IOInsight\|sequentialRatio` | Sequential IO | none stated | none |
| `sequentialReadRatio` | `vSAN Perf\|IOInsight\|sequentialReadRatio` | Sequential Read IO | none stated | none |
| `sequentialWriteRatio` | `vSAN Perf\|IOInsight\|sequentialWriteRatio` | Sequential Write IO | none stated | none |
| `throughputRandom` | `vSAN Perf\|IOInsight\|throughputRandomBytesPerSec` | Random Throughput | bytes/s | none |
| `throughputRead` | `vSAN Perf\|IOInsight\|throughputReadBytesPerSec` | Read throughput | bytes/s | none |
| `throughputSequential` | `vSAN Perf\|IOInsight\|throughputSequentialBytesPerSec` | Sequential Throughput | bytes/s | none |
| `throughputTotal` | `vSAN Perf\|IOInsight\|throughputTotalBytesPerSec` | Total Throughput | bytes/s | none |
| `throughputWrite` | `vSAN Perf\|IOInsight\|throughputWriteBytesPerSec` | Write throughput | bytes/s | none |
| `unaligned4kRatio` | `vSAN Perf\|IOInsight\|unaligned4kRatio` | 4K Unaligned IO | none stated | none |
| `unaligned4kReadRatio` | `vSAN Perf\|IOInsight\|unaligned4kReadRatio` | 4K Unaligned Read IO | none stated | none |
| `unaligned4kWriteRatio` | `vSAN Perf\|IOInsight\|unaligned4kWriteRatio` | 4K Unaligned Write IO | none stated | none |
| `wiosz0_4k` | `vSAN Perf\|IOInsight\|wiosz0_4k` | WriteSize<4K | none stated | none |
| `wiosz128k` | `vSAN Perf\|IOInsight\|wiosz128k` | WriteSize=128K | none stated | none |
| `wiosz128k_256k` | `vSAN Perf\|IOInsight\|wiosz128k_256k` | 128K<WriteSize<256K | none stated | none |
| `wiosz16k` | `vSAN Perf\|IOInsight\|wiosz16k` | WriteSize=16K | none stated | none |
| `wiosz16k_32k` | `vSAN Perf\|IOInsight\|wiosz16k_32k` | 16K<WriteSize<32K | none stated | none |
| `wiosz1_m` | `vSAN Perf\|IOInsight\|wiosz1_m` | WriteSize>1M | none stated | none |
| `wiosz1m` | `vSAN Perf\|IOInsight\|wiosz1m` | WriteSize=1M | none stated | none |
| `wiosz256k` | `vSAN Perf\|IOInsight\|wiosz256k` | WriteSize=256K | none stated | none |
| `wiosz256k_512k` | `vSAN Perf\|IOInsight\|wiosz256k_512k` | 256K<WriteSize<512K | none stated | none |
| `wiosz32k` | `vSAN Perf\|IOInsight\|wiosz32k` | WriteSize=32K | none stated | none |
| `wiosz32k_64k` | `vSAN Perf\|IOInsight\|wiosz32k_64k` | 32K<WriteSize<64K | none stated | none |
| `wiosz4k` | `vSAN Perf\|IOInsight\|wiosz4k` | WriteSize=4K | none stated | none |
| `wiosz4k_8k` | `vSAN Perf\|IOInsight\|wiosz4k_8k` | 4K<WriteSize<8K | none stated | none |
| `wiosz512k` | `vSAN Perf\|IOInsight\|wiosz512k` | WriteSize=512K | none stated | none |
| `wiosz512k_1m` | `vSAN Perf\|IOInsight\|wiosz512k_1m` | 512K<WriteSize<1M | none stated | none |
| `wiosz64k` | `vSAN Perf\|IOInsight\|wiosz64k` | WriteSize=64K | none stated | none |
| `wiosz64k_128k` | `vSAN Perf\|IOInsight\|wiosz64k_128k` | 64K<WriteSize<128K | none stated | none |
| `wiosz8k` | `vSAN Perf\|IOInsight\|wiosz8k` | WriteSize=8K | none stated | none |
| `wiosz8k_16k` | `vSAN Perf\|IOInsight\|wiosz8k_16k` | 8K<WriteSize<16K | none stated | none |
| `wlat0_1us` | `vSAN Perf\|IOInsight\|wlat0_1us` | 0<WriteLatency<1(us) | none stated | none |
| `wlat100_500us` | `vSAN Perf\|IOInsight\|wlat100_500us` | 100<WriteLatency<500(us) | none stated | none |
| `wlat100_ms` | `vSAN Perf\|IOInsight\|wlat100_ms` | WriteLatency>100(ms) | none stated | none |
| `wlat10_100us` | `vSAN Perf\|IOInsight\|wlat10_100us` | 10<WriteLatency<100(us) | none stated | none |
| `wlat10_25ms` | `vSAN Perf\|IOInsight\|wlat10_25ms` | 10<WriteLatency<25(ms) | none stated | none |
| `wlat1_10us` | `vSAN Perf\|IOInsight\|wlat1_10us` | 1<WriteLatency<10(us) | none stated | none |
| `wlat1_5ms` | `vSAN Perf\|IOInsight\|wlat1_5ms` | 1<WriteLatency<5(ms) | none stated | none |
| `wlat25_50ms` | `vSAN Perf\|IOInsight\|wlat25_50ms` | 25<WriteLatency<50(ms) | none stated | none |
| `wlat500us_1ms` | `vSAN Perf\|IOInsight\|wlat500us_1ms` | 500us<WriteLatency<1ms | none stated | none |
| `wlat50_100ms` | `vSAN Perf\|IOInsight\|wlat50_100ms` | 50<WriteLatency<100(ms) | none stated | none |
| `wlat5_10ms` | `vSAN Perf\|IOInsight\|wlat5_10ms` | 5<WriteLatency<10(ms) | none stated | none |
| `writeRatio` | `vSAN Perf\|IOInsight\|writeRatio` | Write IO | none stated | none |

### Host tier: 13 types, 149 keys (lands on HostSystem)

#### `cmmds-net` (Python kind `VsanCmmdsNet`): 10 keys per host

CMMDS (cluster membership directory) traffic on the host: group and RDT receive/transmit counts and throughput.

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 50 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `groupRx` | `vSAN Perf\|CMMDS Network\|groupRx` | Group Inbound | none stated | none |
| `groupRxThroughput` | `vSAN Perf\|CMMDS Network\|groupRxThroughputBytesPerSec` | Group Inbound Throughput | bytes/s | none |
| `groupTxMcast` | `vSAN Perf\|CMMDS Network\|groupTxMcast` | Group Outbound Multicast | none stated | none |
| `groupTxMcastThroughput` | `vSAN Perf\|CMMDS Network\|groupTxMcastThroughputBytesPerSec` | Group Outbound Multicast Throughput | bytes/s | none |
| `groupTxUcast` | `vSAN Perf\|CMMDS Network\|groupTxUcast` | Group Outbound Unicast | none stated | none |
| `groupTxUcastThroughput` | `vSAN Perf\|CMMDS Network\|groupTxUcastThroughputBytesPerSec` | Group Outbound Unicast Throughput | bytes/s | none |
| `rdtRx` | `vSAN Perf\|CMMDS Network\|rdtRx` | Read Inbound | none stated | none |
| `rdtRxThroughput` | `vSAN Perf\|CMMDS Network\|rdtRxThroughputBytesPerSec` | Read Inbound Throughput | bytes/s | none |
| `rdtTx` | `vSAN Perf\|CMMDS Network\|rdtTx` | Read Outbound | none stated | none |
| `rdtTxThroughput` | `vSAN Perf\|CMMDS Network\|rdtTxThroughputBytesPerSec` | Read Outbound Throughput | bytes/s | none |

#### `vsan-cpu` (Python kind `VsanCpu`): 7 keys per host

CPU used by vSAN worlds on the host.

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 35 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `avgRunPct` | `vSAN Perf\|CPU\|avgRunPct` | Run percent (average) | percent | none |
| `avgUsedPct` | `vSAN Perf\|CPU\|avgUsedPct` | Used percent (average) | percent | none |
| `maxRunPct` | `vSAN Perf\|CPU\|maxRunPct` | Run percent (max) | percent | none |
| `maxUsedPct` | `vSAN Perf\|CPU\|maxUsedPct` | Used percent (max) | percent | none |
| `readyPct` | `vSAN Perf\|CPU\|readyPct` | Percentage of Ready CPU Time | percent | none |
| `runPct` | `vSAN Perf\|CPU\|runPct` | Percentage of Running CPU Time | percent | none |
| `usedPct` | `vSAN Perf\|CPU\|usedPct` | Percentage of Used CPU Time | percent | none |

#### `vsan-direct-host` (Python kind `VsanDirectHost`): 8 keys per host

vSAN Direct on the host. Empty on the lab; entity id form unobserved (Python pack models it on `host_uuid`, provisional).

- Lands on: HostSystem. Instance: none (if an observed id carries a second part, it is skipped and counted, see Failure handling). Lab: empty on the lab (wildcard echo).

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `iopsDevRead` | `vSAN Perf\|vSAN Direct\|iopsDevRead` | Physical-layer Read IOPS | IO/s | none |
| `iopsDevWrite` | `vSAN Perf\|vSAN Direct\|iopsDevWrite` | Physical-layer Write IOPS | IO/s | none |
| `latencyDevRead` | `vSAN Perf\|vSAN Direct\|latencyDevReadUs` | Physical-layer Read Latency | microseconds | none |
| `latencyDevWrite` | `vSAN Perf\|vSAN Direct\|latencyDevWriteUs` | Physical-layer Write Latency | microseconds | none |
| `oioDevRead` | `vSAN Perf\|vSAN Direct\|oioDevRead` | Read Outstanding IO | none stated | none |
| `oioDevWrite` | `vSAN Perf\|vSAN Direct\|oioDevWrite` | Write Outstanding IO | none stated | none |
| `throughputDevRead` | `vSAN Perf\|vSAN Direct\|throughputDevReadBytesPerSec` | Physical-layer Read Throughput | bytes/s | none |
| `throughputDevWrite` | `vSAN Perf\|vSAN Direct\|throughputDevWriteBytesPerSec` | Physical-layer Write Throughput | bytes/s | none |

#### `host-domcompmgr` (Python kind `VsanHostDomcompmgr`): 16 keys per host

DOM component manager role on the host: the layer that serves component I/O, including resync reads and recovery writes.

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 80 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `componentCongestion` | `vSAN Perf\|DOM Comp Mgr\|componentCongestion` | Component congestion | none stated | none |
| `iopsRead` | `vSAN Perf\|DOM Comp Mgr\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|DOM Comp Mgr\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyAvgRead` | `vSAN Perf\|DOM Comp Mgr\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|DOM Comp Mgr\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `latencyMaxRead` | `vSAN Perf\|DOM Comp Mgr\|latencyMaxReadUs` | Latency (max, read) | microseconds | none |
| `latencyMaxWrite` | `vSAN Perf\|DOM Comp Mgr\|latencyMaxWriteUs` | Latency (max, write) | microseconds | none |
| `latencyStddevWrite` | `vSAN Perf\|DOM Comp Mgr\|latencyStddevWriteUs` | Latency standard deviation (write) | microseconds | none |
| `namespaceDispatchedCostWrite` | `vSAN Perf\|DOM Comp Mgr\|namespaceDispatchedCostWriteBytesPerSec` | Namespace dispatched cost (write) | bytes/s | none |
| `oio` | `vSAN Perf\|DOM Comp Mgr\|oio` | Outstanding IO | none stated | none |
| `resyncReadCongestion` | `vSAN Perf\|DOM Comp Mgr\|resyncReadCongestion` | Resync read congestion | none stated | none |
| `sharedCongestion` | `vSAN Perf\|DOM Comp Mgr\|sharedCongestion` | Shared congestion | none stated | none |
| `throughputRead` | `vSAN Perf\|DOM Comp Mgr\|throughputReadBytesPerSec` | Read throughput | bytes/s | none |
| `throughputWrite` | `vSAN Perf\|DOM Comp Mgr\|throughputWriteBytesPerSec` | Write throughput | bytes/s | none |
| `vmdiskDispatchedCostWrite` | `vSAN Perf\|DOM Comp Mgr\|vmdiskDispatchedCostWriteBytesPerSec` | VM disk dispatched cost (write) | bytes/s | none |
| `vmdiskQueueDepthWrite` | `vSAN Perf\|DOM Comp Mgr\|vmdiskQueueDepthWrite` | VM disk queue depth (write) | none stated | none |

#### `host-esa-dedup-domclient-io` (Python kind `VsanHostEsaDedupDomclientIo`): 24 keys per host

ESA global deduplication I/O seen from this host's DOM client.

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 120 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `dedupChunkReadCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCount` | Deduplication Chunk Object Read IO Count | none stated | none |
| `dedupChunkWriteCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCount` | Deduplication Chunk Object Write IO Count | none stated | none |
| `dedupEngineReadCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCount` | Deduplication Read IO Count | none stated | none |
| `dedupEngineWriteCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCount` | Deduplication Write IO Count | none stated | none |
| `dedupHashReadCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCount` | Deduplication Hash Object Read IO Count | none stated | none |
| `dedupHashWriteCount` | `vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCount` | Deduplication Hash Object Write IO Count | none stated | none |
| `iopsDedupChunkObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjRead` | Deduplication Chunk Object Read IOPS | IO/s | none |
| `iopsDedupChunkObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjWrite` | Deduplication Chunk Object Write IOPS | IO/s | none |
| `iopsDedupEngineRead` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineRead` | Deduplication Read IOPS | IO/s | none |
| `iopsDedupEngineWrite` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineWrite` | Deduplication Write IOPS | IO/s | none |
| `iopsDedupHashObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjRead` | Deduplication Hash Object Read IOPS | IO/s | none |
| `iopsDedupHashObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjWrite` | Deduplication Hash Object Write IOPS | IO/s | none |
| `latencyAvgDedupChunkObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjReadUs` | Deduplication Chunk Object Read Latency | microseconds | none |
| `latencyAvgDedupChunkObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjWriteUs` | Deduplication Chunk Object Write Latency | microseconds | none |
| `latencyAvgDedupEngineRead` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineReadUs` | Deduplication Read Latency | microseconds | none |
| `latencyAvgDedupEngineWrite` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineWriteUs` | Deduplication Write Latency | microseconds | none |
| `latencyAvgDedupHashObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjReadUs` | Deduplication Hash Object Read Latency | microseconds | none |
| `latencyAvgDedupHashObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjWriteUs` | Deduplication Hash Object Write Latency | microseconds | none |
| `tputDedupChunkObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjRead` | Deduplication Chunk Object Read Throughput | none stated | none |
| `tputDedupChunkObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjWrite` | Deduplication Chunk Object Write Throughput | none stated | none |
| `tputDedupEngineRead` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineRead` | Deduplication Read Throughput | none stated | none |
| `tputDedupEngineWrite` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineWrite` | Deduplication Write Throughput | none stated | none |
| `tputDedupHashObjRead` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjRead` | Deduplication Hash Object Read Throughput | none stated | none |
| `tputDedupHashObjWrite` | `vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjWrite` | Deduplication Hash Object Write Throughput | none stated | none |

#### `vsan-host-net` (Python kind `VsanHostNet`): 8 keys per host

vSAN network traffic of the host as a whole (all vSAN vmknics).

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 40 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `portRxDrops` | `vSAN Perf\|Host Network\|portRxDropsPct` | Inbound Packet Drop Rate of vSwitch Port | percent | per-mille / 10 |
| `portTxDrops` | `vSAN Perf\|Host Network\|portTxDropsPct` | Outbound Packet Drop Rate of vSwitch Port | percent | per-mille / 10 |
| `rxPackets` | `vSAN Perf\|Host Network\|rxPackets` | Inbound Packets Per Second | none stated | none |
| `rxPacketsLossRate` | `vSAN Perf\|Host Network\|rxPacketsLossRatePct` | Inbound Packet Discard Rate | percent | per-mille / 10 |
| `rxThroughput` | `vSAN Perf\|Host Network\|rxThroughputBytesPerSec` | Throughput Inbound | bytes/s | none |
| `txPackets` | `vSAN Perf\|Host Network\|txPackets` | Outbound Packets Per Second | none stated | none |
| `txPacketsLossRate` | `vSAN Perf\|Host Network\|txPacketsLossRatePct` | Outbound Packet Discard Rate | percent | per-mille / 10 |
| `txThroughput` | `vSAN Perf\|Host Network\|txThroughputBytesPerSec` | Throughput Outbound | bytes/s | none |

#### `host-zdom-top-stats` (Python kind `VsanHostZdomTopStats`): 11 keys per host

ESA zDOM top-level I/O on the host.

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 55 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `iopsRead` | `vSAN Perf\|ZDOM Top Stats\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|ZDOM Top Stats\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyAvgRead` | `vSAN Perf\|ZDOM Top Stats\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|ZDOM Top Stats\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `oio` | `vSAN Perf\|ZDOM Top Stats\|oio` | Outstanding IO | none stated | none |
| `oioWrite` | `vSAN Perf\|ZDOM Top Stats\|oioWrite` | Outstanding IO (write) | none stated | none |
| `readLatencyMaxUs` | `vSAN Perf\|ZDOM Top Stats\|readLatencyMaxUs` | Read latency max us | microseconds | none |
| `throughputRead` | `vSAN Perf\|ZDOM Top Stats\|throughputReadBytesPerSec` | Read throughput | bytes/s | none |
| `throughputWrite` | `vSAN Perf\|ZDOM Top Stats\|throughputWriteBytesPerSec` | Write throughput | bytes/s | none |
| `unmapLatencyMaxUs` | `vSAN Perf\|ZDOM Top Stats\|unmapLatencyMaxUs` | Unmap latency max us | microseconds | none |
| `writeLatencyMaxUs` | `vSAN Perf\|ZDOM Top Stats\|writeLatencyMaxUs` | Write latency max us | microseconds | none |

#### `vsan-memory` (Python kind `VsanMemory`): 12 keys per host

Memory reserved and consumed by the vSAN kernel modules and daemons on the host.

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 60 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `clomdConsumedSize` | `vSAN Perf\|Memory\|clomdConsumedSizeBytes` | Clomd consumed size | bytes | none |
| `clomdReservedSize` | `vSAN Perf\|Memory\|clomdReservedSizeBytes` | Clomd reserved size | bytes | none |
| `cmmdsdConsumedSize` | `vSAN Perf\|Memory\|cmmdsdConsumedSizeBytes` | Cmmdsd consumed size | bytes | none |
| `cmmdsdReservedSize` | `vSAN Perf\|Memory\|cmmdsdReservedSizeBytes` | Cmmdsd reserved size | bytes | none |
| `epdConsumedSize` | `vSAN Perf\|Memory\|epdConsumedSizeBytes` | Epd consumed size | bytes | none |
| `kernelConsumedSize` | `vSAN Perf\|Memory\|kernelConsumedSize` | Kernel consumed size | none stated | none |
| `kernelReservedSize` | `vSAN Perf\|Memory\|kernelReservedSizeBytes` | Kernel Reserved Memory Size | bytes | none |
| `osfsdConsumedSize` | `vSAN Perf\|Memory\|osfsdConsumedSizeBytes` | Osfsd consumed size | bytes | none |
| `uwConsumedSize` | `vSAN Perf\|Memory\|uwConsumedSizeBytes` | Uw consumed size | bytes | none |
| `uwReservedSize` | `vSAN Perf\|Memory\|uwReservedSizeBytes` | User world Reserved Memory Size | bytes | none |
| `vsandevicemonitordConsumedSize` | `vSAN Perf\|Memory\|vsandevicemonitordConsumedSizeBytes` | Vsandevicemonitord consumed size | bytes | none |
| `vsanmgmtdConsumedSize` | `vSAN Perf\|Memory\|vsanmgmtdConsumedSizeBytes` | Vsanmgmtd consumed size | bytes | none |

#### `rdt-network-latency` (Python kind `VsanRdtLatency`): 11 keys per host

RDT latency and socket-buffer figures for the host.

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 55 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `avgLatency` | `vSAN Perf\|RDT Latency\|avgLatencyUs` | RDT Network VNIC Average Latency | microseconds | none |
| `kaReset` | `vSAN Perf\|RDT Latency\|kaReset` | Keepalive reset | none stated | none |
| `maxLatency` | `vSAN Perf\|RDT Latency\|maxLatencyUs` | RDT Network Cluster Max Latency | microseconds | none |
| `minLatency` | `vSAN Perf\|RDT Latency\|minLatencyUs` | RDT Network Cluster Min Latency | microseconds | none |
| `numReadyDelay` | `vSAN Perf\|RDT Latency\|numReadyDelay` | Num ready delay | none stated | none |
| `rxSbSpaceMax` | `vSAN Perf\|RDT Latency\|rxSbSpaceMaxBytes` | RDT Network Socket Max Inbound Bytes | bytes | none |
| `rxSbSpaceMin` | `vSAN Perf\|RDT Latency\|rxSbSpaceMinBytes` | RDT Network Socket Min Inbound Bytes | bytes | none |
| `txCtxQMax` | `vSAN Perf\|RDT Latency\|txCtxQMaxBytes` | RDT Network Max Outbound Context Bytes | bytes | none |
| `txQLatAvg` | `vSAN Perf\|RDT Latency\|txQLatAvgUs` | RDT Network Average Outbound Queueing Latency | microseconds | none |
| `txQLatMax` | `vSAN Perf\|RDT Latency\|txQLatMaxUs` | RDT Network Max Outbound Queueing Latency | microseconds | none |
| `txSbSpaceMin` | `vSAN Perf\|RDT Latency\|txSbSpaceMinBytes` | RDT Network Socket Min Outbound Bytes | bytes | none |

#### `vsan-tcpip-stats` (Python kind `VsanTcpIp`): 12 keys per netstack

TCP/IP stack statistics for the stack carrying vSAN, one instance per stack.

- Lands on: HostSystem. Instance: `<stack>` = the netstack name from the entity id (`defaultTcpipStack` on the lab). Lab: 5 entities, 60 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `tcpErrs` | `vSAN Perf\|TCP/IP:<stack>\|tcpErrsPct` | TCP Errors | percent | per-mille / 10 |
| `tcpHalfopenDropRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpHalfopenDropRatePct` | Half Open Drop Rate | percent | per-mille / 10 |
| `tcpRcvdupackRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpRcvdupackRatePct` | Received Duplicate Acknowledge Rate | percent | per-mille / 10 |
| `tcpRcvduppackRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpRcvduppackRatePct` | Received Duplicate Packets Rate | percent | per-mille / 10 |
| `tcpRcvoopackRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpRcvoopackRatePct` | Received Out-of-order Packets Rate | percent | per-mille / 10 |
| `tcpRxErrRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpRxErrRate` | TCP RX errors (rate) | none stated | none |
| `tcpSackRcvBlocksRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpSackRcvBlocksRatePct` | Sack Received Blocks Rate | percent | per-mille / 10 |
| `tcpSackRexmitsRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpSackRexmitsRatePct` | Sack Rexmits Rate | percent | per-mille / 10 |
| `tcpSackSendBlocksRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpSackSendBlocksRatePct` | Sack Send Blocks Rate | percent | per-mille / 10 |
| `tcpSndZeroWin` | `vSAN Perf\|TCP/IP:<stack>\|tcpSndZeroWin` | TCP Send Zero Window | none stated | none |
| `tcpTimeoutDropRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpTimeoutDropRatePct` | Timeout Drop Rate | percent | per-mille / 10 |
| `tcpTxRexmitRate` | `vSAN Perf\|TCP/IP:<stack>\|tcpTxRexmitRate` | TCP TX retransmit (rate) | none stated | none |

#### `vsan-vnic-net` (Python kind `VsanVnic`): 10 keys per vSAN vmknic

vSAN traffic per vSAN vmknic.

- Lands on: HostSystem. Instance: `<vmk>` = the vmknic name (`vmk2` on the lab); the stack part of the id is dropped from the key because a vmknic name is unique on its host (ESXi naming, inferred; the lab has one stack). Lab: 5 entities, 50 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `portRxDrops` | `vSAN Perf\|vNIC:<vmk>\|portRxDropsPct` | Inbound Packet Drop Rate of vSwitch Port | percent | per-mille / 10 |
| `portRxpkts` | `vSAN Perf\|vNIC:<vmk>\|portRxpkts` | Port RX packets | none stated | none |
| `portTxDrops` | `vSAN Perf\|vNIC:<vmk>\|portTxDropsPct` | Outbound Packet Drop Rate of vSwitch Port | percent | per-mille / 10 |
| `portTxpkts` | `vSAN Perf\|vNIC:<vmk>\|portTxpkts` | Port TX packets | none stated | none |
| `rxPackets` | `vSAN Perf\|vNIC:<vmk>\|rxPackets` | Inbound Packets Per Second | none stated | none |
| `rxPacketsLossRate` | `vSAN Perf\|vNIC:<vmk>\|rxPacketsLossRatePct` | Inbound Packet Discard Rate | percent | per-mille / 10 |
| `rxThroughput` | `vSAN Perf\|vNIC:<vmk>\|rxThroughputBytesPerSec` | Throughput Inbound | bytes/s | none |
| `txPackets` | `vSAN Perf\|vNIC:<vmk>\|txPackets` | Outbound Packets Per Second | none stated | none |
| `txPacketsLossRate` | `vSAN Perf\|vNIC:<vmk>\|txPacketsLossRatePct` | Outbound Packet Discard Rate | percent | per-mille / 10 |
| `txThroughput` | `vSAN Perf\|vNIC:<vmk>\|txThroughputBytesPerSec` | Throughput Outbound | bytes/s | none |

#### `vnic-rdt-network-latency` (Python kind `VsanVnicRdtLatency`): 10 keys per vSAN vmknic

RDT latency per vSAN vmknic.

- Lands on: HostSystem. Instance: `<vmk>` = the vmknic name. Lab: 5 entities, 50 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `avgLatency` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|avgLatencyUs` | RDT Network VNIC Average Latency | microseconds | none |
| `kaReset` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|kaReset` | Keepalive reset | none stated | none |
| `maxLatency` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|maxLatencyUs` | RDT Network Cluster Max Latency | microseconds | none |
| `minLatency` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|minLatencyUs` | RDT Network Cluster Min Latency | microseconds | none |
| `numReadyDelay` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|numReadyDelay` | Num ready delay | none stated | none |
| `rxSbSpaceMin` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|rxSbSpaceMinBytes` | RDT Network Socket Min Inbound Bytes | bytes | none |
| `txCtxQMax` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|txCtxQMaxBytes` | RDT Network Max Outbound Context Bytes | bytes | none |
| `txQLatAvg` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|txQLatAvgUs` | RDT Network Average Outbound Queueing Latency | microseconds | none |
| `txQLatMax` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|txQLatMaxUs` | RDT Network Max Outbound Queueing Latency | microseconds | none |
| `txSbSpaceMin` | `vSAN Perf\|vNIC RDT Latency:<vmk>\|txSbSpaceMinBytes` | RDT Network Socket Min Outbound Bytes | bytes | none |

#### `zdom-vtx` (Python kind `VsanZdomVtx`): 10 keys per host

ESA zDOM transaction layer (cache references, bank flushes, transaction latency).

- Lands on: HostSystem. Instance: none. Lab: 5 entities, 50 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `checkpointWorkerWakeupMs` | `vSAN Perf\|ZDOM VTX\|checkpointWorkerWakeupMs` | Checkpoint worker wakeup ms | milliseconds | none |
| `latAvgTotalOpIO` | `vSAN Perf\|ZDOM VTX\|latAvgTotalOpIOUs` | Latency avg total op IO | microseconds | none |
| `latAvgTxnBank` | `vSAN Perf\|ZDOM VTX\|latAvgTxnBankUs` | Latency avg transaction bank | microseconds | none |
| `maxDiscUsedPct` | `vSAN Perf\|ZDOM VTX\|maxDiscUsedPct` | Disc used percent (max) | percent | none |
| `rateBankFlushTotalCacheRef` | `vSAN Perf\|ZDOM VTX\|rateBankFlushTotalCacheRef` | Bank flush total cache ref (rate) | none stated | none |
| `rateSegCleaningCtxDataTotalCacheRef` | `vSAN Perf\|ZDOM VTX\|rateSegCleaningCtxDataTotalCacheRef` | Seg cleaning context data total cache ref (rate) | none stated | none |
| `rateTotalCacheRef` | `vSAN Perf\|ZDOM VTX\|rateTotalCacheRef` | Cache ref (rate, total) | none stated | none |
| `rateTxnLookupCacheRef` | `vSAN Perf\|ZDOM VTX\|rateTxnLookupCacheRef` | Transaction lookup cache ref (rate) | none stated | none |
| `rateTxnPrefetchTotalCacheRef` | `vSAN Perf\|ZDOM VTX\|rateTxnPrefetchTotalCacheRef` | Transaction prefetch total cache ref (rate) | none stated | none |
| `rateTxnReadWrite` | `vSAN Perf\|ZDOM VTX\|rateTxnReadWrite` | Transaction (read, write, rate) | none stated | none |

### Disk tier: 4 types, 31 keys (lands on HostSystem, instanced by disk uuid)

#### `cache-disk` (Python kind `VsanCacheDisk`): 8 keys per disk

OSA cache-tier disk. Empty on ESA; learned-skipped after three empty cycles. 8 of 8 names match built-in OSA disk keys (INV §3, name match only).

- Lands on: HostSystem (the host the disk map read it from). Instance: `<disk uuid>` = the vSAN disk uuid from the entity id. Lab: empty on the lab (wildcard echo).

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `iopsDevRead` | `vSAN Perf\|Cache Disk:<disk uuid>\|iopsDevRead` | Physical-layer Read IOPS | IO/s | none; name matches built-in `vSAN\|Performance\|iopsDevRead` |
| `iopsDevWrite` | `vSAN Perf\|Cache Disk:<disk uuid>\|iopsDevWrite` | Physical-layer Write IOPS | IO/s | none; name matches built-in `vSAN\|Performance\|iopsDevWrite` |
| `latencyDevDAvg` | `vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevDAvgUs` | Device Average Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevDAvg` |
| `latencyDevGAvg` | `vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevGAvgUs` | Guest Average Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevGAvg` |
| `latencyDevRead` | `vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevReadUs` | Physical-layer Read Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevRead` |
| `latencyDevWrite` | `vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevWriteUs` | Physical-layer Write Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevWrite` |
| `throughputDevRead` | `vSAN Perf\|Cache Disk:<disk uuid>\|throughputDevReadBytesPerSec` | Physical-layer Read Throughput | bytes/s | none; name matches built-in `vSAN\|Performance\|throughputDevRead` |
| `throughputDevWrite` | `vSAN Perf\|Cache Disk:<disk uuid>\|throughputDevWriteBytesPerSec` | Physical-layer Write Throughput | bytes/s | none; name matches built-in `vSAN\|Performance\|throughputDevWrite` |

#### `capacity-disk` (Python kind `VsanCapacityDisk`): 13 keys per disk

OSA capacity-tier disk. Empty on ESA; learned-skipped. 8 of 13 names match built-in keys (INV §3).

- Lands on: HostSystem. Instance: `<disk uuid>`. Lab: empty on the lab (wildcard echo).

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `deleteCongestion` | `vSAN Perf\|Capacity Disk:<disk uuid>\|deleteCongestion` | vSAN Layer Delete Congestion | bytes | none; Python unit says bytes; left unsuffixed (Key risks R3b) |
| `iopsDevRead` | `vSAN Perf\|Capacity Disk:<disk uuid>\|iopsDevRead` | Physical-layer Read IOPS | IO/s | none; name matches built-in `vSAN\|Performance\|iopsDevRead` |
| `iopsDevWrite` | `vSAN Perf\|Capacity Disk:<disk uuid>\|iopsDevWrite` | Physical-layer Write IOPS | IO/s | none; name matches built-in `vSAN\|Performance\|iopsDevWrite` |
| `iopsRead` | `vSAN Perf\|Capacity Disk:<disk uuid>\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|Capacity Disk:<disk uuid>\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyDevDAvg` | `vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevDAvgUs` | Device Average Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevDAvg` |
| `latencyDevGAvg` | `vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevGAvgUs` | Guest Average Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevGAvg` |
| `latencyDevRead` | `vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevReadUs` | Physical-layer Read Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevRead` |
| `latencyDevWrite` | `vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevWriteUs` | Physical-layer Write Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevWrite` |
| `latencyRead` | `vSAN Perf\|Capacity Disk:<disk uuid>\|latencyReadUs` | vSAN Layer Read Latency | microseconds | none |
| `latencyWrite` | `vSAN Perf\|Capacity Disk:<disk uuid>\|latencyWriteUs` | vSAN Layer Write Latency | microseconds | none |
| `throughputDevRead` | `vSAN Perf\|Capacity Disk:<disk uuid>\|throughputDevReadBytesPerSec` | Physical-layer Read Throughput | bytes/s | none; name matches built-in `vSAN\|Performance\|throughputDevRead` |
| `throughputDevWrite` | `vSAN Perf\|Capacity Disk:<disk uuid>\|throughputDevWriteBytesPerSec` | Physical-layer Write Throughput | bytes/s | none; name matches built-in `vSAN\|Performance\|throughputDevWrite` |

#### `vsan-esa-disk-layer` (Python kind `VsanEsaDiskLayer`): 5 keys per disk

vSAN-layer I/O per ESA disk. 4 of 5 names match built-in `Vsan2Disk` `vSAN|Performance|*` keys (INV §3).

- Lands on: HostSystem. Instance: `<disk uuid>`. Lab: 5 entities, 25 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `avgLatReadCapacity` | `vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatReadCapacity` | vSAN Layer Average Read Latency of vSAN ESA Disk | none stated | none; name matches built-in `vSAN\|Performance\|avgLatReadCapacity` |
| `avgLatWriteCapacity` | `vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatWriteCapacity` | vSAN Layer Average Write Latency of vSAN ESA Disk | none stated | none; name matches built-in `vSAN\|Performance\|avgLatWriteCapacity` |
| `avgLatWritePerf` | `vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatWritePerf` | Latency write perf (average) | none stated | none |
| `iopsReadCapacity` | `vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsReadCapacity` | vSAN Layer Read IOPS of vSAN ESA Disk | IO/s | none; name matches built-in `vSAN\|Performance\|iopsReadCapacity` |
| `iopsWriteCapacity` | `vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsWriteCapacity` | vSAN Layer Write IOPS of vSAN ESA Disk | IO/s | none; name matches built-in `vSAN\|Performance\|iopsWriteCapacity` |

#### `vsan-esa-disk-scsifw` (Python kind `VsanEsaDiskScsifw`): 5 keys per disk

Device (SCSI framework) latency per ESA disk. 4 of 5 names match built-in `Vsan2Disk` keys (INV §3).

- Lands on: HostSystem. Instance: `<disk uuid>`. Lab: 5 entities, 25 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `latencyDevDAvg` | `vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevDAvgUs` | Device Average Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevDAvg` |
| `latencyDevGAvg` | `vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevGAvgUs` | Guest Average Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevGAvg` |
| `latencyDevKAvg` | `vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevKAvgUs` | Latency dev k (average) | microseconds | none |
| `latencyDevRead` | `vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevReadUs` | Physical-layer Read Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevRead` |
| `latencyDevWrite` | `vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevWriteUs` | Physical-layer Write Latency | microseconds | none; name matches built-in `vSAN\|Performance\|latencyDevWrite` |

### VM tier: 3 types, 21 keys (lands on VirtualMachine)

#### `virtual-machine` (Python kind `VsanVirtualMachine`): 6 keys per VM

vSAN I/O per VM, as the VM's DOM client saw it.

- Lands on: VirtualMachine. Instance: none. Lab: 30 entities, 180 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `iopsRead` | `vSAN Perf\|VM\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|VM\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyRead` | `vSAN Perf\|VM\|latencyReadUs` | vSAN Layer Read Latency | microseconds | none |
| `latencyWrite` | `vSAN Perf\|VM\|latencyWriteUs` | vSAN Layer Write Latency | microseconds | none |
| `throughputRead` | `vSAN Perf\|VM\|throughputReadBytesPerSec` | Read throughput | bytes/s | none |
| `throughputWrite` | `vSAN Perf\|VM\|throughputWriteBytesPerSec` | Write throughput | bytes/s | none |

#### `virtual-disk` (Python kind `VsanVirtualDisk`): 7 keys per VMDK

Per-VMDK normalized IOPS and IOPS limit. The 4 `NIOPSRead*`/`NIOPSWrite*` labels were `None` for all 148 entities on the lab (map); they stay requested and documented, and push nothing while `None`.

- Lands on: VirtualMachine. Instance: `<busN:M>` = the VMDK's bus position from the device read (`scsi0:0`; derivation in §VirtualMachine; `deviceInfo.label` is the alternative, Q5). Lab: 148 entities, 1036 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `NIOPS` | `vSAN Perf\|Virtual Disk:<busN:M>\|NIOPS` | Normalized IOPS | IO/s | none |
| `NIOPSDelayed` | `vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSDelayed` | Delayed Normalized IOPS | IO/s | none |
| `NIOPSRead` | `vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSRead` | Niops (read) | IO/s | none; all `None` on the lab (148/148); requested, nothing pushed |
| `NIOPSReadDelayed` | `vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSReadDelayed` | Niops read delayed | IO/s | none; all `None` on the lab (148/148); requested, nothing pushed |
| `NIOPSWrite` | `vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSWrite` | Niops (write) | IO/s | none; all `None` on the lab (148/148); requested, nothing pushed |
| `NIOPSWriteDelayed` | `vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSWriteDelayed` | Niops write delayed | IO/s | none; all `None` on the lab (148/148); requested, nothing pushed |
| `iopsLimit` | `vSAN Perf\|Virtual Disk:<busN:M>\|iopsLimit` | Normalized IOPS Limit | IO/s | none |

#### `vscsi` (Python kind `VsanVscsi`): 8 keys per virtual SCSI device

I/O per virtual SCSI device.

- Lands on: VirtualMachine. Instance: `<scsiN:M>` = the device part of the entity id (`scsi0:0`), same shape as the vCenter adapter's own `virtualDisk:scsi0:0`. Lab: 148 entities, 1184 series.

| vSAN label | Ops stat key | Python label | Unit | Transform |
|---|---|---|---|---|
| `iopsRead` | `vSAN Perf\|vSCSI:<scsiN:M>\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|vSCSI:<scsiN:M>\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyRead` | `vSAN Perf\|vSCSI:<scsiN:M>\|latencyReadUs` | vSAN Layer Read Latency | microseconds | none |
| `latencyWrite` | `vSAN Perf\|vSCSI:<scsiN:M>\|latencyWriteUs` | vSAN Layer Write Latency | microseconds | none |
| `readCount` | `vSAN Perf\|vSCSI:<scsiN:M>\|readCount` | Read count | none stated | none |
| `throughputRead` | `vSAN Perf\|vSCSI:<scsiN:M>\|throughputReadBytesPerSec` | Read throughput | bytes/s | none |
| `throughputWrite` | `vSAN Perf\|vSCSI:<scsiN:M>\|throughputWriteBytesPerSec` | Write throughput | bytes/s | none |
| `writeCount` | `vSAN Perf\|vSCSI:<scsiN:M>\|writeCount` | Write count | none stated | none |
